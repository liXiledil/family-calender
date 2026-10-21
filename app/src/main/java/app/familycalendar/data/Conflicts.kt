package app.familycalendar.data

/** Two timed events on the same day that overlap and share [personId]. */
data class Conflict(val personId: String, val first: Event, val second: Event)

fun overlaps(a: Event, b: Event): Boolean {
    if (a.date != b.date) return false
    val aStart = a.start ?: return false
    val bStart = b.start ?: return false
    val aEnd = a.effectiveEnd ?: return false
    val bEnd = b.effectiveEnd ?: return false
    return aStart < bEnd && bStart < aEnd
}

/** Personal double-bookings: the same person in two overlapping events. All-day events are ignored. */
fun findConflicts(events: List<Event>): List<Conflict> {
    val result = mutableListOf<Conflict>()
    events.filter { !it.isAllDay }.groupBy { it.date }.values.forEach { day ->
        val sorted = day.sortedBy { it.start }
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (!overlaps(a, b)) continue
                a.personIds.intersect(b.personIds.toSet()).forEach { result += Conflict(it, a, b) }
            }
        }
    }
    return result
}

fun conflictingEventIds(conflicts: List<Conflict>): Set<String> =
    conflicts.flatMap { listOf(it.first.id, it.second.id) }.toSet()
