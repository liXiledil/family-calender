package app.familycalendar.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.familycalendar.LocationShareService
import app.familycalendar.data.FamilyLocations
import app.familycalendar.data.FirebaseBootstrap
import app.familycalendar.data.Person
import app.familycalendar.data.PlaceTracker
import app.familycalendar.data.SharingPrefs
import app.familycalendar.data.SyncState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore

/** Where everyone is, the places that trigger alerts, and the switches for sharing and being alerted. */
@Composable
fun FamilyScreen(people: List<Person>, state: SyncState) {
    if (state !is SyncState.InFamily) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Family locations", style = MaterialTheme.typography.titleMedium)
            Text("Sign in and create or join your family on the Sync tab first. Locations are shared only inside your family.")
        }
        return
    }
    val context = LocalContext.current
    val db = remember { FirebaseBootstrap.app(context)?.let { FirebaseFirestore.getInstance(it) } } ?: return
    val locations = remember(state.familyId) { FamilyLocations(db, state.familyId) }
    DisposableEffect(locations) { onDispose { locations.close() } }
    val prefs = remember { SharingPrefs(context) }
    var personId by remember { mutableStateOf(prefs.personId) }
    var share by remember { mutableStateOf(prefs.shareLocation) }
    var alerts by remember { mutableStateOf(prefs.receiveAlerts) }
    val places by locations.places.collectAsState()
    val memberLocations by locations.locations.collectAsState()
    val events by locations.events.collectAsState()
    val me = people.firstOrNull { it.id == personId }

    fun apply() {
        prefs.familyId = state.familyId
        prefs.uid = state.uid
        prefs.personId = personId
        prefs.personName = me?.name.orEmpty()
        prefs.shareLocation = share
        prefs.receiveAlerts = alerts
        if (share || alerts) LocationShareService.start(context) else LocationShareService.stop(context)
        if (!share) locations.clearLocation(state.uid)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val locationOk = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (share && !locationOk) share = false
        apply()
    }

    fun request(needLocation: Boolean) {
        val wanted = buildList {
            if (needLocation) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isEmpty()) apply() else permissionLauncher.launch(wanted.toTypedArray())
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Family locations", style = MaterialTheme.typography.titleMedium)
        FamilyMap(people, memberLocations, places)

        Text("This phone belongs to:", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { p ->
                FilterChip(selected = p.id == personId, onClick = { personId = p.id; prefs.personId = p.id; prefs.personName = p.name },
                    label = { Text(p.name) }, leadingIcon = { ColorDot(p.color) })
            }
        }
        if (me == null) Text("Choose who uses this phone to turn sharing on.", style = MaterialTheme.typography.bodySmall)

        SwitchRow("Share my location with my family", "Updates about once a minute while moving. A notification shows while it's on.", share, me != null) {
            share = it; if (it) request(true) else apply()
        }
        SwitchRow("Alert me when family members arrive or leave places", "Alerts arrive while Firefly's notification is showing on this phone.", alerts, me != null) {
            alerts = it; if (it) request(false) else apply()
        }
        if ((share || alerts) && Build.VERSION.SDK_INT >= 31) {
            Text(
                "If you restart your phone, open Firefly once to resume. For the most reliable alerts, set Firefly's battery use to \"Unrestricted\" in Android settings.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()
        Text("Where everyone is", fontWeight = FontWeight.SemiBold)
        people.forEach { p ->
            val loc = memberLocations.firstOrNull { it.personId == p.id }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(p.color, 12.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Medium)
                    if (loc == null) {
                        Text("Not sharing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val place = PlaceTracker.placeAt(loc.lat, loc.lng, places)
                        Text(
                            (if (place != null) "At ${place.name}" else "Away") + " · ${ago(loc.updatedAtMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (loc != null) TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(${Uri.encode(p.name)})")))
                }) { Text("Map") }
            }
        }

        HorizontalDivider()
        Text("Places", fontWeight = FontWeight.SemiBold)
        Text("Alerts fire when a family member who is sharing arrives at or leaves one of these.", style = MaterialTheme.typography.bodySmall)
        places.forEach { place ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name)
                    Text("${place.radiusMeters} m radius", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { locations.deletePlace(place.id) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
        AddPlace(onAdd = { name, radius, onDone, onError ->
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                onError("Allow location first by turning on sharing above.")
            } else {
                try {
                    LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (loc == null) onError("Couldn't get your location. Try again outdoors or with location turned on.")
                            else { locations.savePlace(name, loc.latitude, loc.longitude, radius); onDone() }
                        }
                        .addOnFailureListener { onError("Couldn't get your location.") }
                } catch (_: SecurityException) {
                    onError("Location permission is off for Firefly.")
                }
            }
        })

        if (events.isNotEmpty()) {
            HorizontalDivider()
            Text("Recent", fontWeight = FontWeight.SemiBold)
            events.take(10).forEach {
                Text("${it.personName.ifEmpty { "Someone" }} ${if (it.arrived) "arrived at" else "left"} ${it.placeName} · ${ago(it.atMillis)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AddPlace(onAdd: (String, Int, () -> Unit, (String) -> Unit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf(150) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text("Add the place you're standing in", style = MaterialTheme.typography.labelMedium)
    OutlinedTextField(name, { name = it }, label = { Text("Name (Home, School, Grandma's)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(100, 150, 250, 500).forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r m") }) }
    }
    Button(enabled = name.isNotBlank() && !busy, onClick = {
        busy = true; message = null
        onAdd(name, radius, { busy = false; name = ""; message = "Saved." }, { busy = false; message = it })
    }) { Text(if (busy) "Finding you..." else "Save this place") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
