package app.familycalendar.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** A photo, screenshot or PDF to read. [mimeType] is image/jpeg, image/png, image/webp, image/gif or application/pdf. */
class Attachment(val base64: String, val mimeType: String)

/**
 * Talks to Claude with your own API key. Every call is made from this phone, and nothing it
 * returns changes the calendar by itself: scans and audits come back as proposals to approve.
 */
class ClaudeClient(private val settings: ApiKeyStore) {

    suspend fun scan(
        attachments: List<Attachment>,
        text: String,
        people: List<Person>,
        existing: List<Event>,
        today: LocalDate,
    ): Result<List<ProposedEvent>> {
        val system = """
            You read schedules for a family calendar app and turn them into calendar events.
            ${context(today, people)}
            Rules:
            - Extract only events that are actually shown or written. Never invent events, times or people.
            - Dates are YYYY-MM-DD and times are 24-hour HH:MM. If the year is missing, use the next occurrence on or after today.
            - Leave start and end empty for all-day events or when no time is given. Leave end empty when only a start is given.
            - If something repeats (for example "Tuesdays until Dec 12"), list each occurrence as its own event, up to 60 in total. If no end date is given, cover the next 8 weeks.
            - For people, use only names from the family list above that clearly apply, otherwise an empty list.
            - Set major to true for big one-off dates (trips, holidays, school breaks, deadlines) and repeatsYearly to true for birthdays and anniversaries.
            - confidence is high when text is clear, medium when you inferred something, low when the image was hard to read.
            - note is a short sentence about anything the user should double-check, or an empty string.
        """.trimIndent()
        val blocks = attachments.map(::toBlock) +
            ContentBlockParam.ofText(TextBlockParam.builder().text(text.ifBlank { "Extract the events from this." }).build())
        return call(system, blocks, SCAN_SCHEMA, OutputConfig.Effort.MEDIUM).mapCatching { Assistant.parseScan(it, people, existing) }
    }

    suspend fun audit(events: List<Event>, people: List<Person>, today: LocalDate): Result<AuditResult> {
        val conflicts = findConflicts(events).filter { it.first.date >= today }.take(30).joinToString("\n") {
            val who = people.firstOrNull { p -> p.id == it.personId }?.name ?: "someone"
            "- $who: \"${it.first.title}\" overlaps \"${it.second.title}\" on ${it.first.date}"
        }
        val system = """
            You audit a family calendar and propose small clean-ups that keep it accurate and concise.
            ${context(today, people)}
            Look for: duplicate events (same thing entered twice, or a manual entry that duplicates an imported one), inconsistent
            naming of the same thing (rename to one clear style), events with no one assigned, and obviously wrong dates or times
            (such as an end before the start, or a school event on a holiday you can see in the list).
            Rules:
            - Propose only changes you are confident about, and explain each in one plain sentence in reason.
            - Events with imported = true come from a linked calendar and must not be edited or deleted. If a manual event duplicates an imported one, propose deleting the manual one.
            - action "delete" removes every id in eventIds. action "merge" keeps keepId and deletes the other ids in eventIds. action "edit" changes only eventIds[0];
              leave title, date, start, end empty and people as [] for anything that should stay the same.
            - Use ids exactly as given. For fields a proposal does not use, send empty strings and empty lists.
            - summary is two or three sentences on the calendar's health, and may mention scheduling conflicts and a sensible fix for each (you cannot change conflicts directly).
            - If everything looks fine, return no proposals.
        """.trimIndent()
        val prompt = "Events (JSON):\n${Assistant.eventsForPrompt(events, people, today)}\n\nScheduling conflicts found by the app:\n${conflicts.ifBlank { "none" }}"
        return call(system, listOf(textBlock(prompt)), AUDIT_SCHEMA, OutputConfig.Effort.MEDIUM).mapCatching { Assistant.parseAudit(it, people) }
    }

    suspend fun ask(question: String, events: List<Event>, people: List<Person>, today: LocalDate): Result<String> {
        val system = """
            You answer questions about a family's calendar. ${context(today, people)}
            Use only the events provided. Be brief and concrete: lead with the answer, then the supporting dates and times.
            If the events don't contain what is needed, say so instead of guessing. Times are 24-hour in the data; write them in a friendly 12-hour form.
        """.trimIndent()
        val prompt = "Events (JSON):\n${Assistant.eventsForPrompt(events, people, today, daysBack = 7, daysAhead = 90)}\n\nQuestion: $question"
        return call(system, listOf(textBlock(prompt)), null, OutputConfig.Effort.LOW)
    }

    private fun context(today: LocalDate, people: List<Person>): String {
        val day = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val names = people.joinToString(", ") { it.name }.ifEmpty { "(none yet)" }
        return "Today is $day $today. The phone's time zone is ${ZoneId.systemDefault()}. Family members: $names."
    }

    private fun textBlock(text: String) = ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())

    private fun toBlock(a: Attachment): ContentBlockParam =
        if (a.mimeType == "application/pdf") {
            ContentBlockParam.ofDocument(
                DocumentBlockParam.builder().source(Base64PdfSource.builder().data(a.base64).build()).build()
            )
        } else {
            ContentBlockParam.ofImage(
                ImageBlockParam.builder()
                    .source(Base64ImageSource.builder().data(a.base64).mediaType(Base64ImageSource.MediaType.of(a.mimeType)).build())
                    .build()
            )
        }

    private suspend fun call(
        system: String,
        blocks: List<ContentBlockParam>,
        schema: Map<String, Any>?,
        effort: OutputConfig.Effort,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = settings.load() ?: throw IllegalStateException("Add your Anthropic API key in the Claude tab first.")
            val spent = settings.usageThisMonth().estimatedUsd
            check(spent < settings.monthlyCapUsd) {
                "You've reached your monthly Claude limit (about $${"%.2f".format(spent)}). Raise it in the Claude tab to continue."
            }
            val output = OutputConfig.builder().effort(effort)
            if (schema != null) output.format(jsonFormat(schema))
            val params = MessageCreateParams.builder()
                .model(settings.model)
                .maxTokens(16000L)
                .system(system)
                .outputConfig(output.build())
                .addUserMessageOfBlockParams(blocks)
                // If a safety check declines the request, let the API retry on a fallback model automatically.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()
            val message = AnthropicOkHttpClient.builder().apiKey(key).build().messages().create(params)
            settings.addUsage(message.usage().inputTokens(), message.usage().outputTokens())
            val stop = message.stopReason().orElse(null)
            check(stop != StopReason.REFUSAL) { "Claude declined to process this one." }
            val text = message.content().filter { it.isText() }.joinToString("") { it.asText().text() }.trim()
            check(stop != StopReason.MAX_TOKENS) { "The answer was too long to finish. Try fewer pages or a shorter date range." }
            check(text.isNotEmpty()) { "Claude didn't return anything." }
            text
        }.recoverCatching { e -> throw IllegalStateException(friendly(e), e) }
    }

    private fun friendly(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is IllegalStateException && (m.startsWith("Add your") || m.startsWith("You've reached") || m.startsWith("Claude") || m.startsWith("The answer")) -> m
            "401" in m || "authentication" in m.lowercase() -> "Anthropic rejected the API key. Check it in the Claude tab."
            "429" in m || "rate" in m.lowercase() -> "Claude is busy or you hit a rate limit. Try again in a minute."
            "overloaded" in m.lowercase() -> "Claude is overloaded right now. Try again shortly."
            e is java.io.IOException || e.javaClass.simpleName.contains("Io", ignoreCase = true) -> "Couldn't reach Claude. Check your connection."
            else -> m.ifEmpty { "Something went wrong talking to Claude." }
        }
    }

    private fun jsonFormat(schema: Map<String, Any>): JsonOutputFormat {
        val builder = JsonOutputFormat.Schema.builder()
        schema.forEach { (k, v) -> builder.putAdditionalProperty(k, JsonValue.from(v)) }
        return JsonOutputFormat.builder().schema(builder.build()).build()
    }

    companion object {
        private val STR = mapOf("type" to "string")
        private val BOOL = mapOf("type" to "boolean")
        private fun list(item: Map<String, Any>) = mapOf("type" to "array", "items" to item)
        private fun enum(vararg values: String) = mapOf("type" to "string", "enum" to values.toList())
        private fun obj(vararg props: Pair<String, Any>) = mapOf(
            "type" to "object",
            "properties" to mapOf(*props),
            "required" to props.map { it.first },
            "additionalProperties" to false,
        )

        internal val SCAN_SCHEMA = obj(
            "events" to list(
                obj(
                    "title" to STR, "date" to STR, "start" to STR, "end" to STR, "people" to list(STR),
                    "notes" to STR, "major" to BOOL, "repeatsYearly" to BOOL, "confidence" to enum("high", "medium", "low"),
                )
            ),
            "note" to STR,
        )

        internal val AUDIT_SCHEMA = obj(
            "summary" to STR,
            "proposals" to list(
                obj(
                    "action" to enum("delete", "edit", "merge"), "eventIds" to list(STR), "keepId" to STR, "reason" to STR,
                    "title" to STR, "date" to STR, "start" to STR, "end" to STR, "people" to list(STR),
                )
            ),
        )
    }
}
