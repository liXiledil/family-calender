package app.familycalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTrackerTest {
    private val school = Place("school", "School", 41.8800, -87.6300, 150)
    private val home = Place("home", "Home", 41.9000, -87.6500, 150)
    private val places = listOf(school, home)

    // About 0.0009 degrees of latitude is roughly 100 metres.
    private fun nearSchool(offsetDeg: Double) = school.lat + offsetDeg to school.lng

    @Test
    fun distanceIsAboutRight() {
        val d = PlaceTracker.distanceMeters(41.88, -87.63, 41.8809, -87.63)
        assertTrue("was $d", d in 95.0..105.0)
    }

    @Test
    fun arrivingInsideTheRadiusCounts() {
        val (lat, lng) = nearSchool(0.0005)
        val now = PlaceTracker.inside(emptySet(), lat, lng, 10f, places)
        assertEquals(setOf("school"), now)
        assertEquals(setOf("school") to emptySet<String>(), PlaceTracker.transitions(emptySet(), now))
    }

    @Test
    fun staysInsideUntilWellOutsideTheRadius() {
        val (lat, lng) = nearSchool(0.0016) // ~178 m: outside 150 m, inside 195 m
        assertEquals(setOf("school"), PlaceTracker.inside(setOf("school"), lat, lng, 10f, places))
        assertEquals(emptySet<String>(), PlaceTracker.inside(emptySet(), lat, lng, 10f, places))
    }

    @Test
    fun leavingIsReportedOnce() {
        val (lat, lng) = nearSchool(0.0030) // ~333 m
        val now = PlaceTracker.inside(setOf("school"), lat, lng, 10f, places)
        assertEquals(emptySet<String>() to setOf("school"), PlaceTracker.transitions(setOf("school"), now))
        assertEquals(emptySet<String>() to emptySet<String>(), PlaceTracker.transitions(now, PlaceTracker.inside(now, lat, lng, 10f, places)))
    }

    @Test
    fun aVeryRoughFixNearTheEdgeDoesNotCountAsArriving() {
        val (lat, lng) = nearSchool(0.0012) // ~133 m, inside the radius on paper
        assertEquals(emptySet<String>(), PlaceTracker.inside(emptySet(), lat, lng, 300f, places))
    }

    @Test
    fun placeAtFindsTheNearestOrNothing() {
        assertEquals("Home", PlaceTracker.placeAt(home.lat, home.lng, places)?.name)
        assertNull(PlaceTracker.placeAt(10.0, 10.0, places))
    }
}
