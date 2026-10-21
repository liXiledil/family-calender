package app.familycalendar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Keeps the family's people and events in a JSON file on the device. */
class FamilyStore(private val file: File) : FamilyRepository {
    private val _data = MutableStateFlow(load())
    override val data: StateFlow<FamilyData> = _data.asStateFlow()

    override fun upsertPerson(person: Person) = update { d ->
        d.copy(people = d.people.upsert(person) { it.id == person.id })
    }

    override fun removePerson(personId: String) = update { d ->
        d.copy(
            people = d.people.filter { it.id != personId },
            events = d.events.map { e -> e.copy(personIds = e.personIds - personId) },
        )
    }

    override fun upsertEvent(event: Event) = update { d ->
        d.copy(events = d.events.upsert(event) { it.id == event.id })
    }

    override fun removeEvent(eventId: String) = update { d ->
        d.copy(events = d.events.filter { it.id != eventId })
    }

    override fun replaceSourceEvents(sourceId: String, events: List<Event>) = update { d ->
        d.copy(events = d.events.filter { it.sourceId != sourceId } + events)
    }

    private fun update(change: (FamilyData) -> FamilyData) {
        val next = change(_data.value)
        _data.value = next
        file.writeText(encode(next))
    }

    private fun load(): FamilyData =
        if (file.exists()) runCatching { decode(file.readText()) }.getOrDefault(FamilyData()) else FamilyData()

    private fun <T> List<T>.upsert(item: T, sameAs: (T) -> Boolean): List<T> =
        if (any(sameAs)) map { if (sameAs(it)) item else it } else this + item

    private fun encode(d: FamilyData): String {
        val people = JSONArray()
        d.people.forEach { p ->
            people.put(JSONObject().put("id", p.id).put("name", p.name).put("color", p.color))
        }
        val events = JSONArray()
        d.events.forEach { e ->
            events.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("personIds", JSONArray(e.personIds))
                    .put("date", e.date.toString())
                    .put("start", e.start?.toString())
                    .put("end", e.end?.toString())
                    .put("notes", e.notes)
                    .put("major", e.major)
                    .put("repeatsYearly", e.repeatsYearly)
                    .put("sourceId", e.sourceId)
            )
        }
        return JSONObject().put("version", 1).put("people", people).put("events", events).toString()
    }

    private fun decode(text: String): FamilyData {
        val root = JSONObject(text)
        val people = root.optJSONArray("people") ?: JSONArray()
        val events = root.optJSONArray("events") ?: JSONArray()
        return FamilyData(
            people = (0 until people.length()).map { i ->
                val o = people.getJSONObject(i)
                Person(id = o.getString("id"), name = o.getString("name"), color = o.getLong("color"))
            },
            events = (0 until events.length()).map { i ->
                val o = events.getJSONObject(i)
                val ids = o.optJSONArray("personIds") ?: JSONArray()
                Event(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    personIds = (0 until ids.length()).map { ids.getString(it) },
                    date = LocalDate.parse(o.getString("date")),
                    start = o.optString("start").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                    end = o.optString("end").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                    notes = o.optString("notes"),
                    major = o.optBoolean("major"),
                    repeatsYearly = o.optBoolean("repeatsYearly"),
                    sourceId = o.optString("sourceId").takeIf { it.isNotEmpty() },
                )
            },
        )
    }
}
