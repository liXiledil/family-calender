package app.familycalendar.data

/** Repeats yearly events (birthdays, anniversaries) into each of [years], from their first year on. */
fun expandYearly(events: List<Event>, years: IntRange): List<Event> = events.flatMap { e ->
    if (!e.repeatsYearly) {
        listOf(e)
    } else {
        years.filter { it >= e.date.year }.map { y -> e.copy(date = e.date.withYear(y)) }
    }
}
