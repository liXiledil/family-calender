package app.familycalendar.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.familycalendar.data.MemberLocation
import app.familycalendar.data.Person
import app.familycalendar.data.Place
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** OpenStreetMap view of where each family member last reported being, with the saved places drawn as circles. */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun FamilyMap(people: List<Person>, locations: List<MemberLocation>, places: List<Place>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fitRequest by remember { mutableIntStateOf(0) }
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = java.io.File(context.cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(4.0)
            // Keep the page from scrolling while the map is being dragged.
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    val points = locations.mapNotNull { loc -> people.firstOrNull { it.id == loc.personId }?.let { loc to it } }
    val fitKey = points.map { it.first.personId }.sorted().joinToString() + "|" + fitRequest

    Column(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)),
            factory = { mapView },
            update = { map ->
                map.overlays.clear()
                places.forEach { place ->
                    map.overlays.add(Polygon(map).apply {
                        this.points = Polygon.pointsAsCircle(GeoPoint(place.lat, place.lng), place.radiusMeters.toDouble())
                        fillPaint.color = 0x2A4F8A7E
                        outlinePaint.color = 0xAA4F8A7E.toInt()
                        outlinePaint.strokeWidth = 3f
                        title = place.name
                    })
                }
                points.forEach { (loc, person) ->
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(loc.lat, loc.lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(map.resources, pin(context, person))
                        title = person.name
                        snippet = ago(loc.updatedAtMillis)
                    })
                }
                if (map.getTag(TAG_FIT) != fitKey && points.isNotEmpty()) {
                    map.setTag(TAG_FIT, fitKey)
                    val geo = points.map { GeoPoint(it.first.lat, it.first.lng) }
                    map.post {
                        if (geo.size == 1) {
                            map.controller.setZoom(16.0)
                            map.controller.setCenter(geo[0])
                        } else {
                            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo).increaseByScale(1.6f), false)
                        }
                    }
                }
                map.invalidate()
            },
        )
        if (points.isEmpty()) {
            Text("Nobody is sharing a location yet. Pins appear here once someone turns sharing on.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        } else {
            OutlinedButton(onClick = { fitRequest++ }) { Text("Show everyone") }
        }
    }
}

private const val TAG_FIT = 0x7f0a0001

private fun pin(context: Context, person: Person): Bitmap {
    val size = (44 * context.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0x55000000
    c.drawCircle(size / 2f, size / 2f + size * 0.04f, size * 0.46f, paint)
    paint.color = (person.color.toInt() or 0xFF000000.toInt())
    c.drawCircle(size / 2f, size / 2f, size * 0.44f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * 0.07f
    paint.color = 0xFFFFFFFF.toInt()
    c.drawCircle(size / 2f, size / 2f, size * 0.42f, paint)
    paint.style = Paint.Style.FILL
    paint.color = 0xFFFFFFFF.toInt()
    paint.textSize = size * 0.46f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    val letter = person.name.trim().firstOrNull()?.uppercase() ?: "?"
    c.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    return bmp
}

private fun ago(millis: Long): String {
    if (millis <= 0) return "just now"
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}
