package app.familycalendar.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(val id: String, val name: String, val lat: Double, val lng: Double, val radiusMeters: Int)

/** Where a family member last reported being. */
data class MemberLocation(val uid: String, val personId: String, val lat: Double, val lng: Double, val accuracyMeters: Float, val updatedAtMillis: Long)

data class PlaceEvent(
    val id: String,
    val uid: String,
    val personId: String,
    val personName: String,
    val placeName: String,
    val arrived: Boolean,
    val atMillis: Long,
)

object PlaceTracker {
    /** A phone must move this far beyond a place's radius before it counts as having left, so GPS jitter doesn't cause repeated alerts. */
    private const val LEAVE_MARGIN = 1.3

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** The ids of the places the phone is inside, given where it was before. */
    fun inside(previous: Set<String>, lat: Double, lng: Double, accuracyMeters: Float, places: List<Place>): Set<String> =
        places.filter { p ->
            val d = distanceMeters(lat, lng, p.lat, p.lng)
            if (p.id in previous) d <= p.radiusMeters * LEAVE_MARGIN
            // A very rough fix must be well inside the place before it counts as arriving.
            else d + accuracyMeters.coerceAtMost(p.radiusMeters.toFloat()) / 2 <= p.radiusMeters
        }.map { it.id }.toSet()

    /** (arrived, left) between two readings. */
    fun transitions(before: Set<String>, after: Set<String>): Pair<Set<String>, Set<String>> = (after - before) to (before - after)

    /** The name of the place a location is in, if any. */
    fun placeAt(lat: Double, lng: Double, places: List<Place>): Place? =
        places.filter { distanceMeters(lat, lng, it.lat, it.lng) <= it.radiusMeters * LEAVE_MARGIN }
            .minByOrNull { distanceMeters(lat, lng, it.lat, it.lng) }
}
