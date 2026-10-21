package app.familycalendar.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class Person(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Long,
)

data class Event(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val personIds: List<String>,
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val notes: String = "",
    /** Shown in the Year view. */
    val major: Boolean = false,
    /** Birthdays, anniversaries and other dates that come back every year. */
    val repeatsYearly: Boolean = false,
    /** Set on events imported from a linked calendar; a re-import replaces them. */
    val sourceId: String? = null,
) {
    val isAllDay: Boolean get() = start == null

    /** End time used for overlap checks; a timed event with no end counts as one hour. */
    val effectiveEnd: LocalTime?
        get() = end ?: start?.let { if (it >= LocalTime.of(23, 0)) LocalTime.MAX else it.plusHours(1) }
}

data class FamilyData(
    val people: List<Person> = emptyList(),
    val events: List<Event> = emptyList(),
)

val PersonColors: List<Long> = listOf(
    0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFB8C00,
    0xFF8E24AA, 0xFF00897B, 0xFFD81B60, 0xFF6D4C41,
)
