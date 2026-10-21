package app.familycalendar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.familycalendar.data.FamilyLocations
import app.familycalendar.data.Person
import app.familycalendar.data.SyncState

/** Full-screen live map: pins move as family phones report in. */
@Composable
fun MapScreen(people: List<Person>, state: SyncState, locations: FamilyLocations?) {
    if (state !is SyncState.InFamily || locations == null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Family map", style = MaterialTheme.typography.titleMedium)
            Text("Sign in and create or join your family on the Sync tab first. Then turn on location sharing on the Family tab.")
        }
        return
    }
    val memberLocations by locations.locations.collectAsState()
    val places by locations.places.collectAsState()
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FamilyMap(people, memberLocations, places, Modifier.weight(1f), fill = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { p ->
                val loc = memberLocations.firstOrNull { it.personId == p.id }
                AssistChip(
                    onClick = {},
                    leadingIcon = { ColorDot(p.color) },
                    label = { Text(p.name + " · " + (loc?.let { mapAgo(it.updatedAtMillis) } ?: "not sharing")) },
                )
            }
        }
    }
}

internal fun mapAgo(millis: Long): String {
    if (millis <= 0) return "just now"
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}
