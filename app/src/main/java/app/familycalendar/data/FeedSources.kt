package app.familycalendar.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A linked calendar whose events are copied into Firefly: an .ics link, or a calendar on this phone. */
data class FeedSource(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind,
    val name: String,
    /** The .ics address, or the phone calendar's id for [Kind.PHONE]. */
    val target: String,
    val personId: String,
    val lastRefreshed: Long = 0,
    val lastCount: Int = 0,
    val lastError: String? = null,
) {
    enum class Kind { ICS, PHONE }
}

/**
 * The list of linked calendars lives on this phone only: an Outlook publish link is a private
 * address, so it is never uploaded to the family's shared data. The imported events are shared.
 */
class FeedSourceStore(context: Context) {
    private val prefs = context.getSharedPreferences("feed_sources", Context.MODE_PRIVATE)
    private val _sources = MutableStateFlow(load())
    val sources: StateFlow<List<FeedSource>> = _sources.asStateFlow()

    fun upsert(source: FeedSource) = save(_sources.value.filter { it.id != source.id } + source)

    fun remove(id: String) = save(_sources.value.filter { it.id != id })

    private fun save(list: List<FeedSource>) {
        _sources.value = list
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().put("id", s.id).put("kind", s.kind.name).put("name", s.name).put("target", s.target)
                    .put("personId", s.personId).put("lastRefreshed", s.lastRefreshed)
                    .put("lastCount", s.lastCount).put("lastError", s.lastError)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun load(): List<FeedSource> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FeedSource(
                id = o.getString("id"),
                kind = FeedSource.Kind.valueOf(o.getString("kind")),
                name = o.getString("name"),
                target = o.getString("target"),
                personId = o.getString("personId"),
                lastRefreshed = o.optLong("lastRefreshed"),
                lastCount = o.optInt("lastCount"),
                lastError = o.optString("lastError").takeIf { it.isNotEmpty() && it != "null" },
            )
        }
    }.getOrDefault(emptyList())
}
