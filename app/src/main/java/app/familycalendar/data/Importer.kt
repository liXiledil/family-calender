package app.familycalendar.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PhoneCalendar(val id: String, val name: String, val account: String)

/** Copies events from linked calendars into the family calendar. Only the source's own events are replaced. */
class Importer(private val context: Context, private val sources: FeedSourceStore) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun window(today: LocalDate = LocalDate.now()) = today.minusDays(30)..today.plusDays(180)

    fun hasCalendarPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Refreshes one source; the result is the number of events now imported from it. */
    suspend fun refresh(source: FeedSource, repository: FamilyRepository): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val range = window()
            val events = when (source.kind) {
                FeedSource.Kind.ICS -> fromIcs(source, range)
                FeedSource.Kind.PHONE -> fromPhone(source, range)
            }
            repository.replaceSourceEvents(source.id, events)
            events.size
        }.also { result ->
            sources.upsert(
                source.copy(
                    lastRefreshed = System.currentTimeMillis(),
                    lastCount = result.getOrDefault(source.lastCount),
                    lastError = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
                )
            )
        }
    }

    private fun fromIcs(source: FeedSource, range: ClosedRange<LocalDate>): List<Event> {
        val text = download(source.target)
        return Ics.parse(text, zone, range.start, range.endInclusive).map { o ->
            Event(
                id = stableId(source.id, o.uid, o.date.toString(), o.start.toString()),
                title = o.title,
                personIds = listOf(source.personId),
                date = o.date,
                start = o.start,
                end = o.end,
                sourceId = source.id,
            )
        }
    }

    private fun download(address: String): String {
        val url = URL(address.trim().replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://"))
        require(url.protocol == "https" || url.protocol == "http") { "That doesn't look like a calendar link." }
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "text/calendar, */*")
        try {
            val code = conn.responseCode
            check(code == 200) { "The calendar link answered with error $code. It may have been turned off." }
            val bytes = conn.inputStream.use { it.readNBytesCapped(MAX_BYTES) }
            val text = bytes.toString(Charsets.UTF_8)
            check(text.contains("BEGIN:VCALENDAR")) { "That link didn't return a calendar." }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            check(out.size() <= max) { "That calendar is too large to import." }
        }
        return out.toByteArray()
    }

    fun listPhoneCalendars(): List<PhoneCalendar> {
        if (!hasCalendarPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val result = mutableListOf<PhoneCalendar>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) result += PhoneCalendar(c.getLong(0).toString(), c.getString(1).orEmpty(), c.getString(2).orEmpty())
        }
        return result
    }

    private fun fromPhone(source: FeedSource, range: ClosedRange<LocalDate>): List<Event> {
        check(hasCalendarPermission()) { "Firefly needs permission to read this phone's calendar." }
        val begin = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = range.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, begin)
        android.content.ContentUris.appendId(builder, end)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val events = mutableListOf<Event>()
        context.contentResolver.query(
            builder.build(), projection, "${CalendarContract.Instances.CALENDAR_ID} = ?", arrayOf(source.target),
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Busy"
                if (allDay) {
                    // All-day instances are stored in UTC midnight, so read them in UTC.
                    val first = Instant.ofEpochMilli(c.getLong(2)).atZone(ZoneId.of("UTC")).toLocalDate()
                    val last = Instant.ofEpochMilli(c.getLong(3)).atZone(ZoneId.of("UTC")).toLocalDate().minusDays(1)
                    var day = first
                    while (!day.isAfter(last) && day <= range.endInclusive && ChronoDays.between(first, day) < 31) {
                        if (day >= range.start) events += phoneEvent(source, c.getLong(0), title, day, null, null)
                        day = day.plusDays(1)
                    }
                } else {
                    val s = Instant.ofEpochMilli(c.getLong(2)).atZone(zone)
                    val e = Instant.ofEpochMilli(c.getLong(3)).atZone(zone)
                    val endTime = if (e.toLocalDate() == s.toLocalDate()) e.toLocalTime() else java.time.LocalTime.of(23, 59)
                    events += phoneEvent(source, c.getLong(0), title, s.toLocalDate(), s.toLocalTime(), endTime)
                }
            }
        }
        return events
    }

    private fun phoneEvent(source: FeedSource, eventId: Long, title: String, date: LocalDate, start: java.time.LocalTime?, end: java.time.LocalTime?) =
        Event(
            id = stableId(source.id, eventId.toString(), date.toString(), start.toString()),
            title = title,
            personIds = listOf(source.personId),
            date = date,
            start = start,
            end = end?.takeIf { start != null && it > start },
            sourceId = source.id,
        )

    private object ChronoDays {
        fun between(a: LocalDate, b: LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(a, b)
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024

        /** The same imported occurrence always gets the same id, so a refresh updates it instead of duplicating it. */
        fun stableId(vararg parts: String): String =
            UUID.nameUUIDFromBytes(MessageDigest.getInstance("SHA-1").digest(parts.joinToString("|").toByteArray())).toString()
    }
}
