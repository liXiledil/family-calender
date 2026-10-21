package app.familycalendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** One concrete occurrence of a calendar entry, in the phone's time zone. */
data class IcsOccurrence(
    val uid: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
)

/**
 * Reads iCalendar (.ics) feeds such as Outlook's published calendar link, expanding repeating
 * meetings into the occurrences that fall between [from] and [to].
 */
object Ics {
    private const val MAX_STEPS = 5000
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private class Instant(val dateTime: LocalDateTime, val allDay: Boolean)

    private class VEvent(val props: List<Prop>) {
        fun first(name: String) = props.firstOrNull { it.name == name }
        fun all(name: String) = props.filter { it.name == name }
    }

    fun parse(text: String, zone: ZoneId, from: LocalDate, to: LocalDate): List<IcsOccurrence> {
        val events = readEvents(text)
        val (overrides, masters) = events.partition { it.first("RECURRENCE-ID") != null }
        val result = mutableListOf<IcsOccurrence>()
        for (event in masters) {
            if (event.first("STATUS")?.value.equals("CANCELLED", ignoreCase = true)) continue
            val uid = event.first("UID")?.value ?: continue
            val startProp = event.first("DTSTART") ?: continue
            val start = instant(startProp, zone) ?: continue
            val length = length(event, start, zone)
            val replaced = overrides.filter { it.first("UID")?.value == uid }
                .mapNotNull { o -> o.first("RECURRENCE-ID")?.let { instant(it, zone)?.dateTime } }
                .toSet()
            val excluded = event.all("EXDATE").flatMap { p ->
                p.value.split(',').mapNotNull { v -> instant(p.copy(value = v), zone)?.dateTime }
            }.toSet()
            val starts = event.first("RRULE")?.let { expand(start.dateTime, rule(it.value, zone), to) }
                ?: listOf(start.dateTime)
            starts.filter { it !in excluded && it !in replaced }
                .forEach { result += occurrences(uid, title(event), it, length, start.allDay, from, to) }
        }
        for (event in overrides) {
            if (event.first("STATUS")?.value.equals("CANCELLED", ignoreCase = true)) continue
            val uid = event.first("UID")?.value ?: continue
            val start = event.first("DTSTART")?.let { instant(it, zone) } ?: continue
            result += occurrences(uid, title(event), start.dateTime, length(event, start, zone), start.allDay, from, to)
        }
        return result.sortedWith(compareBy({ it.date }, { it.start }))
    }

    private fun title(event: VEvent) = event.first("SUMMARY")?.value?.takeIf { it.isNotBlank() } ?: "Busy"

    /** How long the event lasts, from DTEND or DURATION; one day for all-day, one hour otherwise. */
    private fun length(event: VEvent, start: Instant, zone: ZoneId): java.time.Duration {
        event.first("DTEND")?.let { instant(it, zone) }?.let {
            return java.time.Duration.between(start.dateTime, it.dateTime)
        }
        event.first("DURATION")?.let { p -> runCatching { java.time.Duration.parse(p.value) }.getOrNull() }
            ?.let { return it }
        return if (start.allDay) java.time.Duration.ofDays(1) else java.time.Duration.ofHours(1)
    }

    private fun occurrences(
        uid: String,
        title: String,
        start: LocalDateTime,
        length: java.time.Duration,
        allDay: Boolean,
        from: LocalDate,
        to: LocalDate,
    ): List<IcsOccurrence> {
        if (allDay) {
            val days = maxOf(1L, length.toDays()).coerceAtMost(31)
            return (0 until days).map { start.toLocalDate().plusDays(it) }
                .filter { it in from..to }
                .map { IcsOccurrence(uid, title, it, null, null) }
        }
        val date = start.toLocalDate()
        if (date !in from..to) return emptyList()
        val end = start.plus(length)
        val endTime = if (end.toLocalDate() == date) end.toLocalTime() else LocalTime.of(23, 59)
        return listOf(IcsOccurrence(uid, title, date, start.toLocalTime(), endTime.takeIf { it > start.toLocalTime() }))
    }

    // ---- Recurrence rules ----

    private data class Rule(
        val freq: String,
        val interval: Int,
        val count: Int?,
        val until: LocalDateTime?,
        val byDay: List<Pair<Int?, DayOfWeek>>,
        val byMonthDay: List<Int>,
    )

    private fun rule(value: String, zone: ZoneId): Rule {
        val parts = value.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
        return Rule(
            freq = parts["FREQ"]?.uppercase() ?: "DAILY",
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            count = parts["COUNT"]?.toIntOrNull(),
            until = parts["UNTIL"]?.let { instant(Prop("UNTIL", emptyMap(), it), zone)?.dateTime },
            byDay = parts["BYDAY"]?.split(',')?.mapNotNull(::weekday).orEmpty(),
            byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty(),
        )
    }

    private fun weekday(token: String): Pair<Int?, DayOfWeek>? {
        val t = token.trim().uppercase()
        if (t.length < 2) return null
        val day = when (t.takeLast(2)) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> return null
        }
        return t.dropLast(2).takeIf { it.isNotEmpty() }?.toIntOrNull() to day
    }

    /** Start times of each repetition, in order, stopping at COUNT, UNTIL or [to]. */
    private fun expand(start: LocalDateTime, rule: Rule, to: LocalDate): List<LocalDateTime> {
        val out = mutableListOf<LocalDateTime>()
        val time = start.toLocalTime()
        val limit = to.atTime(LocalTime.MAX)
        fun accept(candidate: LocalDateTime): Boolean {
            if (candidate < start) return true
            if (rule.until != null && candidate > rule.until) return false
            if (rule.count != null && out.size >= rule.count) return false
            if (candidate > limit) return false
            out += candidate
            return true
        }
        var step = 0L
        loop@ while (step < MAX_STEPS) {
            val candidates: List<LocalDateTime> = when (rule.freq) {
                "DAILY" -> listOf(start.plusDays(step * rule.interval))
                "WEEKLY" -> {
                    val weekStart = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks(step * rule.interval)
                    val days = rule.byDay.map { it.second }.ifEmpty { listOf(start.dayOfWeek) }
                    days.sorted().map { weekStart.with(TemporalAdjusters.nextOrSame(it)).atTime(time) }
                }
                "MONTHLY" -> {
                    val month = start.toLocalDate().withDayOfMonth(1).plusMonths(step * rule.interval)
                    monthDays(month, rule, start.dayOfMonth).map { it.atTime(time) }
                }
                "YEARLY" -> listOf(start.plusYears(step * rule.interval))
                else -> break@loop
            }
            for (c in candidates.sorted()) {
                if (!accept(c)) break@loop
            }
            step++
        }
        return out
    }

    private fun monthDays(month: LocalDate, rule: Rule, defaultDay: Int): List<LocalDate> {
        val length = month.lengthOfMonth()
        if (rule.byDay.isNotEmpty()) {
            return rule.byDay.flatMap { (ordinal, day) ->
                val all = generateSequence(month.with(TemporalAdjusters.firstInMonth(day))) { it.plusWeeks(1) }
                    .takeWhile { it.month == month.month }.toList()
                when {
                    ordinal == null -> all
                    ordinal > 0 -> listOfNotNull(all.getOrNull(ordinal - 1))
                    else -> listOfNotNull(all.getOrNull(all.size + ordinal))
                }
            }.sorted()
        }
        val days = rule.byMonthDay.ifEmpty { listOf(defaultDay) }
        return days.mapNotNull { d ->
            val day = if (d < 0) length + d + 1 else d
            if (day in 1..length) month.withDayOfMonth(day) else null
        }.sorted()
    }

    // ---- Lines, properties and dates ----

    private fun readEvents(text: String): List<VEvent> {
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        val events = mutableListOf<VEvent>()
        var current: MutableList<Prop>? = null
        var depth = 0
        for (line in lines) {
            val prop = property(line) ?: continue
            when {
                prop.name == "BEGIN" && prop.value.equals("VEVENT", true) -> { current = mutableListOf(); depth = 0 }
                prop.name == "BEGIN" && current != null -> depth++
                prop.name == "END" && prop.value.equals("VEVENT", true) -> { current?.let { events += VEvent(it) }; current = null }
                prop.name == "END" && current != null -> depth--
                current != null && depth == 0 -> current += prop
            }
        }
        return events
    }

    private fun property(line: String): Prop? {
        var inQuotes = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].trim('"') }
        }.toMap()
        return Prop(head[0].uppercase(), params, unescape(line.substring(colon + 1)))
    }

    private fun unescape(v: String) =
        v.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    private fun instant(prop: Prop, zone: ZoneId): Instant? {
        val v = prop.value.trim()
        return runCatching {
            when {
                prop.params["VALUE"].equals("DATE", true) || v.length == 8 ->
                    Instant(LocalDate.parse(v.take(8), dateFormat).atStartOfDay(), allDay = true)
                v.endsWith("Z") ->
                    Instant(LocalDateTime.parse(v.dropLast(1), dateTimeFormat).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime(), false)
                else -> {
                    val local = LocalDateTime.parse(v.take(15), dateTimeFormat)
                    val source = prop.params["TZID"]?.let(::zoneFor) ?: zone
                    Instant(local.atZone(source).withZoneSameInstant(zone).toLocalDateTime(), false)
                }
            }
        }.getOrNull()
    }

    /** Outlook names time zones the Windows way ("Pacific Standard Time"); map the common ones. */
    private val windowsZones = mapOf(
        "Pacific Standard Time" to "America/Los_Angeles",
        "Mountain Standard Time" to "America/Denver",
        "US Mountain Standard Time" to "America/Phoenix",
        "Central Standard Time" to "America/Chicago",
        "Eastern Standard Time" to "America/New_York",
        "US Eastern Standard Time" to "America/Indianapolis",
        "Alaskan Standard Time" to "America/Anchorage",
        "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Atlantic Standard Time" to "America/Halifax",
        "Newfoundland Standard Time" to "America/St_Johns",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Romance Standard Time" to "Europe/Paris",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Central European Standard Time" to "Europe/Warsaw",
        "E. Europe Standard Time" to "Europe/Chisinau",
        "FLE Standard Time" to "Europe/Kiev",
        "GTB Standard Time" to "Europe/Bucharest",
        "India Standard Time" to "Asia/Kolkata",
        "China Standard Time" to "Asia/Shanghai",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "New Zealand Standard Time" to "Pacific/Auckland",
        "UTC" to "UTC",
    )

    private fun zoneFor(id: String): ZoneId? =
        runCatching { ZoneId.of(id) }.getOrNull() ?: windowsZones[id]?.let { ZoneId.of(it) }
}
