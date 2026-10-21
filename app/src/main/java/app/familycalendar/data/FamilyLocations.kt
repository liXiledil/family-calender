package app.familycalendar.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** The family's saved places, last known locations and arrival/leave alerts, kept in Firestore. */
class FamilyLocations(db: FirebaseFirestore, familyId: String, private val onNewAlert: ((PlaceEvent) -> Unit)? = null) {
    private val family = db.collection("families").document(familyId)
    private val placesRef = family.collection("places")
    private val locationsRef = family.collection("locations")
    private val eventsRef = family.collection("placeEvents")
    private val startedAt = Timestamp.now()

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places.asStateFlow()
    private val _locations = MutableStateFlow<List<MemberLocation>>(emptyList())
    val locations: StateFlow<List<MemberLocation>> = _locations.asStateFlow()
    private val _events = MutableStateFlow<List<PlaceEvent>>(emptyList())
    val events: StateFlow<List<PlaceEvent>> = _events.asStateFlow()

    private val listeners: List<ListenerRegistration> = listOf(
        placesRef.addSnapshotListener { snap, _ ->
            if (snap != null) _places.value = snap.documents.mapNotNull { d ->
                Place(d.id, d.getString("name") ?: return@mapNotNull null, d.getDouble("lat") ?: return@mapNotNull null,
                    d.getDouble("lng") ?: return@mapNotNull null, d.getLong("radius")?.toInt() ?: 150)
            }.sortedBy { it.name.lowercase() }
        },
        locationsRef.addSnapshotListener { snap, _ ->
            if (snap != null) _locations.value = snap.documents.mapNotNull { d ->
                MemberLocation(
                    uid = d.id,
                    personId = d.getString("personId") ?: return@mapNotNull null,
                    lat = d.getDouble("lat") ?: return@mapNotNull null,
                    lng = d.getDouble("lng") ?: return@mapNotNull null,
                    accuracyMeters = d.getDouble("accuracy")?.toFloat() ?: 0f,
                    updatedAtMillis = d.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
                )
            }
        },
        eventsRef.orderBy("at", Query.Direction.DESCENDING).limit(30).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val parsed = snap.documents.mapNotNull(::toEvent)
            _events.value = parsed
            // Alert only for changes that arrive live and happened after this listener started.
            snap.documentChanges.filter { it.type == DocumentChange.Type.ADDED }
                .mapNotNull { toEvent(it.document) }
                .filter { it.atMillis >= startedAt.toDate().time - 60_000 }
                .forEach { onNewAlert?.invoke(it) }
        },
    )

    private fun toEvent(d: com.google.firebase.firestore.DocumentSnapshot): PlaceEvent? {
        val at = d.getTimestamp("at")?.toDate()?.time ?: return null
        return PlaceEvent(
            id = d.id,
            uid = d.getString("uid") ?: return null,
            personId = d.getString("personId").orEmpty(),
            personName = d.getString("personName").orEmpty(),
            placeName = d.getString("placeName") ?: return null,
            arrived = d.getString("type") == "arrived",
            atMillis = at,
        )
    }

    fun savePlace(name: String, lat: Double, lng: Double, radiusMeters: Int) {
        val id = UUID.randomUUID().toString()
        placesRef.document(id).set(mapOf("name" to name.trim(), "lat" to lat, "lng" to lng, "radius" to radiusMeters))
    }

    fun deletePlace(id: String) {
        placesRef.document(id).delete()
    }

    fun publishLocation(uid: String, personId: String, lat: Double, lng: Double, accuracyMeters: Float) {
        locationsRef.document(uid).set(
            mapOf("personId" to personId, "lat" to lat, "lng" to lng, "accuracy" to accuracyMeters.toDouble(), "updatedAt" to FieldValue.serverTimestamp())
        )
    }

    fun clearLocation(uid: String) {
        locationsRef.document(uid).delete()
    }

    fun publishPlaceEvent(uid: String, personId: String, personName: String, placeName: String, arrived: Boolean) {
        eventsRef.document(UUID.randomUUID().toString()).set(
            mapOf(
                "uid" to uid, "personId" to personId, "personName" to personName, "placeName" to placeName,
                "type" to if (arrived) "arrived" else "left", "at" to FieldValue.serverTimestamp(),
            )
        )
    }

    fun close() = listeners.forEach { it.remove() }
}
