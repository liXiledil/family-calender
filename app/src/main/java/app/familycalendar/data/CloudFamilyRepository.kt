package app.familycalendar.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime

private data class OrderedPerson(val person: Person, val order: Int)

/**
 * The shared family calendar in Firestore. Firestore caches on the device, so the calendar
 * still works offline and edits upload when the phone is back online.
 */
class CloudFamilyRepository(private val db: FirebaseFirestore, val familyId: String) : FamilyRepository {
    private val family = db.collection("families").document(familyId)
    private val people = family.collection("people")
    private val events = family.collection("events")

    private val _data = MutableStateFlow(FamilyData())
    override val data: StateFlow<FamilyData> = _data.asStateFlow()

    private val listeners: List<ListenerRegistration> = listOf(
        people.addSnapshotListener { snap, _ ->
            if (snap != null) {
                _data.value = _data.value.copy(
                    people = snap.documents.mapNotNull(::toPerson).sortedBy { it.order }.map { it.person },
                )
            }
        },
        events.addSnapshotListener { snap, _ ->
            if (snap != null) _data.value = _data.value.copy(events = snap.documents.mapNotNull(::toEvent))
        },
    )

    override fun upsertPerson(person: Person) {
        val order = _data.value.people.indexOfFirst { it.id == person.id }.takeIf { it >= 0 } ?: _data.value.people.size
        people.document(person.id).set(personFields(person, order))
    }

    override fun removePerson(personId: String) {
        people.document(personId).delete()
        _data.value.events.filter { personId in it.personIds }.forEach {
            events.document(it.id).update("personIds", FieldValue.arrayRemove(personId))
        }
    }

    override fun upsertEvent(event: Event) {
        events.document(event.id).set(eventFields(event))
    }

    override fun removeEvent(eventId: String) {
        events.document(eventId).delete()
    }

    override fun replaceSourceEvents(sourceId: String, events: List<Event>) {
        val keep = events.map { it.id }.toSet()
        val stale = _data.value.events.filter { it.sourceId == sourceId && it.id !in keep }
        val ops: List<Pair<String, Event?>> = stale.map { it.id to null } + events.map { it.id to it }
        // Firestore batches hold at most 500 writes.
        ops.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (id, event) ->
                val ref = this.events.document(id)
                if (event == null) batch.delete(ref) else batch.set(ref, eventFields(event))
            }
            batch.commit()
        }
    }

    fun close() = listeners.forEach { it.remove() }

    companion object {
        fun personFields(person: Person, order: Int): Map<String, Any> =
            mapOf("name" to person.name, "color" to person.color, "order" to order)

        fun eventFields(event: Event): Map<String, Any?> = mapOf(
            "title" to event.title,
            "personIds" to event.personIds,
            "date" to event.date.toString(),
            "start" to event.start?.toString(),
            "end" to event.end?.toString(),
            "notes" to event.notes,
            "major" to event.major,
            "repeatsYearly" to event.repeatsYearly,
            "sourceId" to event.sourceId,
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        private fun toPerson(doc: DocumentSnapshot): OrderedPerson? {
            val name = doc.getString("name") ?: return null
            val color = doc.getLong("color") ?: return null
            return OrderedPerson(Person(doc.id, name, color), doc.getLong("order")?.toInt() ?: 0)
        }

        @Suppress("UNCHECKED_CAST")
        private fun toEvent(doc: DocumentSnapshot): Event? {
            val title = doc.getString("title") ?: return null
            val date = doc.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return Event(
                id = doc.id,
                title = title,
                personIds = (doc.get("personIds") as? List<String>).orEmpty(),
                date = date,
                start = doc.getString("start")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                end = doc.getString("end")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                notes = doc.getString("notes").orEmpty(),
                major = doc.getBoolean("major") ?: false,
                repeatsYearly = doc.getBoolean("repeatsYearly") ?: false,
                sourceId = doc.getString("sourceId"),
            )
        }
    }
}
