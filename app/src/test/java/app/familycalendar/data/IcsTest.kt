package app.familycalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IcsTest {
    private val zone = ZoneId.of("America/Chicago")
    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 12, 31)

    private fun cal(vararg events: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("") + "END:VCALENDAR\r\n"

    private fun parse(text: String) = Ics.parse(text, zone, from, to)

    @Test
    fun singleUtcEventConvertsToLocalTime() {
        val text = cal("BEGIN:VEVENT\r\nUID:a\r\nSUMMARY:Standup\r\nDTSTART:20261020T140000Z\r\nDTEND:20261020T143000Z\r\nEND:VEVENT\r\n")
        val events = parse(text)
        assertEquals(1, events.size)
        assertEquals(LocalDate.of(2026, 10, 20), events[0].date)
        assertEquals(LocalTime.of(9, 0), events[0].start)
        assertEquals(LocalTime.of(9, 30), events[0].end)
        assertEquals("Standup", events[0].title)
    }

    @Test
    fun windowsTimeZoneNamesAreUnderstood() {
        val text = cal(
            "BEGIN:VEVENT\r\nUID:b\r\nSUMMARY:Review\r\nDTSTART;TZID=Eastern Standard Time:20261021T100000\r\n" +
                "DTEND;TZID=Eastern Standard Time:20261021T110000\r\nEND:VEVENT\r\n",
        )
        assertEquals(LocalTime.of(9, 0), parse(text).single().start)
    }

    @Test
    fun weeklyRuleWithExceptionAndOverride() {
        val text = cal(
            "BEGIN:VEVENT\r\nUID:w\r\nSUMMARY:1:1\r\nDTSTART;TZID=America/Chicago:20261005T150000\r\n" +
                "DTEND;TZID=America/Chicago:20261005T153000\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=6\r\n" +
                "EXDATE;TZID=America/Chicago:20261007T150000\r\nEND:VEVENT\r\n",
            "BEGIN:VEVENT\r\nUID:w\r\nRECURRENCE-ID;TZID=America/Chicago:20261012T150000\r\nSUMMARY:1:1 moved\r\n" +
                "DTSTART;TZID=America/Chicago:20261012T160000\r\nDTEND;TZID=America/Chicago:20261012T163000\r\nEND:VEVENT\r\n",
        )
        val events = parse(text)
        // Oct 5, 7(excluded), 12(moved), 14, 19, 21 -> 5 shown
        assertEquals(listOf(5, 12, 14, 19, 21), events.map { it.date.dayOfMonth })
        assertEquals("1:1 moved", events.first { it.date.dayOfMonth == 12 }.title)
        assertEquals(LocalTime.of(16, 0), events.first { it.date.dayOfMonth == 12 }.start)
    }

    @Test
    fun monthlyNthWeekdayAndUntil() {
        val text = cal(
            "BEGIN:VEVENT\r\nUID:m\r\nSUMMARY:PTA\r\nDTSTART:20261013T230000Z\r\nRRULE:FREQ=MONTHLY;BYDAY=2TU;UNTIL=20261231T000000Z\r\nEND:VEVENT\r\n",
        )
        assertEquals(listOf(13, 10, 8), parse(text).map { it.date.dayOfMonth })
    }

    @Test
    fun allDayMultiDayEventCoversEachDay() {
        val text = cal(
            "BEGIN:VEVENT\r\nUID:t\r\nSUMMARY:Trip\r\nDTSTART;VALUE=DATE:20261102\r\nDTEND;VALUE=DATE:20261105\r\nEND:VEVENT\r\n",
        )
        val events = parse(text)
        assertEquals(3, events.size)
        assertTrue(events.all { it.start == null })
    }

    @Test
    fun cancelledAndFoldedLinesAndBusyFallback() {
        val text = cal(
            "BEGIN:VEVENT\r\nUID:c\r\nSUMMARY:Gone\r\nSTATUS:CANCELLED\r\nDTSTART:20261020T140000Z\r\nEND:VEVENT\r\n",
            "BEGIN:VEVENT\r\nUID:f\r\nSUMMARY:Long \r\n title\r\nDTSTART:20261020T140000Z\r\nEND:VEVENT\r\n",
            "BEGIN:VEVENT\r\nUID:x\r\nDTSTART:20261020T160000Z\r\nEND:VEVENT\r\n",
        )
        assertEquals(listOf("Long title", "Busy"), parse(text).map { it.title })
    }

    @Test
    fun yearlyExpansionRepeatsFromFirstYear() {
        val birthday = Event(id = "b", title = "Maya's birthday", personIds = emptyList(), date = LocalDate.of(2020, 2, 29), repeatsYearly = true)
        val expanded = expandYearly(listOf(birthday), 2019..2021)
        assertEquals(listOf(LocalDate.of(2020, 2, 29), LocalDate.of(2021, 2, 28)), expanded.map { it.date })
    }
}
