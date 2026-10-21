package app.familycalendar.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** An event Claude read from a photo, PDF or typed text, waiting for the user's approval. */
data class ProposedEvent(val event: Event, val confidence: String, val possibleDuplicate: Boolean)

enum class AuditAction { DELETE, EDIT, MERGE }

/** A tidy-up Claude suggests for existing events. Nothing happens until the user approves it. */
data class AuditProposal(
    val action: AuditAction,
    val eventIds: List<String>,
    val keepId: String,
    val reason: String,
    val title: String,
    val date: LocalDate?,
    val start: LocalTime?,
    val end: LocalTime?,
    val personIds: List<String>,
)

data class AuditResult(val summary: String, val proposals: List<AuditProposal>)

object Assistant {
    private fun time(value: String): LocalTime? = value.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    private fun names(people: List<Person>) = people.associateBy { it.name.trim().lowercase() }

    private fun JSONObject.strings(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    /** Reads Claude's scan answer. Rows with an unreadable date are dropped; unknown names are left unassigned. */
    fun parseScan(json: String, people: List<Person>, existing: List<Event>): List<ProposedEvent> {
        val byName = names(people)
        val arr = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val title = o.optString("title").trim().ifEmpty { return@mapNotNull null }
            val date = runCatching { LocalDate.parse(o.optString("date").trim()) }.getOrNull() ?: return@mapNotNull null
            val start = time(o.optString("start"))
            val end = time(o.optString("end"))?.takeIf { start != null && it > start }
            val event = Event(
                title = title,
                personIds = o.strings("people").mapNotNull { byName[it.trim().lowercase()]?.id }.distinct(),
                date = date,
                start = start,
                end = end,
                notes = o.optString("notes").trim(),
                major = o.optBoolean("major"),
                repeatsYearly = o.optBoolean("repeatsYearly"),
            )
            val duplicate = existing.any {
                it.date == date && it.start == start && it.title.trim().equals(title, ignoreCase = true)
            }
            ProposedEvent(event, o.optString("confidence", "medium"), duplicate)
        }
    }

    fun parseAudit(json: String, people: List<Person>): AuditResult {
        val root = JSONObject(json)
        val byName = names(people)
        val arr = root.optJSONArray("proposals") ?: JSONArray()
        val proposals = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val action = when (o.optString("action")) {
                "delete" -> AuditAction.DELETE
                "edit" -> AuditAction.EDIT
                "merge" -> AuditAction.MERGE
                else -> return@mapNotNull null
            }
            val ids = o.strings("eventIds").filter { it.isNotBlank() }
            if (ids.isEmpty()) return@mapNotNull null
            AuditProposal(
                action = action,
                eventIds = ids,
                keepId = o.optString("keepId"),
                reason = o.optString("reason").trim(),
                title = o.optString("title").trim(),
                date = runCatching { LocalDate.parse(o.optString("date").trim()) }.getOrNull(),
                start = time(o.optString("start")),
                end = time(o.optString("end")),
                personIds = o.strings("people").mapNotNull { byName[it.trim().lowercase()]?.id }.distinct(),
            )
        }
        return AuditResult(root.optString("summary").trim(), proposals)
    }

    /** What applying [proposals] would change; events imported from a linked calendar are never touched. */
    data class Changes(val upserts: List<Event>, val deletes: List<Event>)

    fun changesFor(proposals: List<AuditProposal>, events: List<Event>): Changes {
        val byId = events.associateBy { it.id }
        val upserts = linkedMapOf<String, Event>()
        val deletes = linkedMapOf<String, Event>()
        for (p in proposals) {
            val targets = p.eventIds.mapNotNull { byId[it] }.filter { it.sourceId == null }
            when (p.action) {
                AuditAction.DELETE -> targets.forEach { deletes[it.id] = it }
                AuditAction.MERGE -> {
                    val keep = p.keepId.takeIf { it in byId } ?: p.eventIds.firstOrNull { it in byId } ?: continue
                    targets.filter { it.id != keep }.forEach { deletes[it.id] = it }
                }
                AuditAction.EDIT -> targets.firstOrNull()?.let { e ->
                    val start = p.start ?: e.start
                    val end = (p.end ?: e.end)?.takeIf { start != null && it > start }
                    upserts[e.id] = e.copy(
                        title = p.title.ifEmpty { e.title },
                        date = p.date ?: e.date,
                        start = start,
                        end = end,
                        personIds = p.personIds.ifEmpty { e.personIds },
                    )
                }
            }
        }
        return Changes(upserts.values.filter { it.id !in deletes }, deletes.values.toList())
    }

    /** The events Claude sees when auditing or answering questions: a window around today plus every major date. */
    fun eventsForPrompt(events: List<Event>, people: List<Person>, today: LocalDate, daysBack: Long = 14, daysAhead: Long = 120): String {
        val names = people.associate { it.id to it.name }
        val from = today.minusDays(daysBack)
        val to = today.plusDays(daysAhead)
        val picked = events.filter { (it.date in from..to) || it.major || it.repeatsYearly }
            .sortedWith(compareBy({ it.date }, { it.start })).take(500)
        val arr = JSONArray()
        picked.forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("title", e.title).put("date", e.date.toString())
                    .put("start", e.start?.toString() ?: "").put("end", e.end?.toString() ?: "")
                    .put("people", JSONArray(e.personIds.mapNotNull { names[it] }))
                    .put("major", e.major).put("repeatsYearly", e.repeatsYearly)
                    .put("imported", e.sourceId != null).put("notes", e.notes)
            )
        }
        return arr.toString()
    }
}
