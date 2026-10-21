package app.familycalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ConflictsTest {
    private val day = LocalDate.of(2026, 10, 20)

    private fun event(title: String, people: List<String>, start: String?, end: String? = null, date: LocalDate = day) =
        Event(
            id = title,
            title = title,
            personIds = people,
            date = date,
            start = start?.let(LocalTime::parse),
            end = end?.let(LocalTime::parse),
        )

    @Test
    fun overlappingEventsForSamePersonConflict() {
        val conflicts = findConflicts(
            listOf(event("Teams", listOf("olan"), "15:00", "16:00"), event("Dentist", listOf("olan"), "15:30", "16:30")),
        )
        assertEquals(listOf(Conflict("olan", conflicts[0].first, conflicts[0].second)), conflicts)
        assertEquals(setOf("Teams", "Dentist"), conflictingEventIds(conflicts))
    }

    @Test
    fun backToBackEventsDoNotConflict() {
        val conflicts = findConflicts(
            listOf(event("A", listOf("olan"), "09:00", "10:00"), event("B", listOf("olan"), "10:00", "11:00")),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun differentPeopleDoNotConflict() {
        val conflicts = findConflicts(
            listOf(event("A", listOf("olan"), "09:00", "10:00"), event("B", listOf("maya"), "09:00", "10:00")),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun sharedEventConflictsForEachSharedPerson() {
        val conflicts = findConflicts(
            listOf(event("A", listOf("olan", "maya"), "09:00", "10:00"), event("B", listOf("olan", "maya"), "09:30", "10:30")),
        )
        assertEquals(setOf("olan", "maya"), conflicts.map { it.personId }.toSet())
    }

    @Test
    fun allDayEventsAndOtherDaysAreIgnored() {
        val conflicts = findConflicts(
            listOf(
                event("Holiday", listOf("olan"), null),
                event("A", listOf("olan"), "09:00", "10:00"),
                event("B", listOf("olan"), "09:00", "10:00", date = day.plusDays(1)),
            ),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun eventWithoutEndCountsAsOneHour() {
        val conflicts = findConflicts(
            listOf(event("A", listOf("olan"), "09:00"), event("B", listOf("olan"), "09:45", "10:30")),
        )
        assertEquals(1, conflicts.size)
    }
}
