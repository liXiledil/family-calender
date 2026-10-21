package app.familycalendar.data

import kotlinx.coroutines.flow.StateFlow

/** Where the family's calendar lives: this phone only, or the shared cloud family. */
interface FamilyRepository {
    val data: StateFlow<FamilyData>
    fun upsertPerson(person: Person)
    fun removePerson(personId: String)
    fun upsertEvent(event: Event)
    fun removeEvent(eventId: String)

    /** Swaps every event imported from [sourceId] for [events]. */
    fun replaceSourceEvents(sourceId: String, events: List<Event>)
}
