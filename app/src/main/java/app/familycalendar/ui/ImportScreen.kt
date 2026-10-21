package app.familycalendar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.familycalendar.data.FamilyRepository
import app.familycalendar.data.FeedSource
import app.familycalendar.data.FeedSourceStore
import app.familycalendar.data.Importer
import app.familycalendar.data.Person
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ImportScreen(people: List<Person>, repository: FamilyRepository, store: FeedSourceStore, importer: Importer) {
    val sources by store.sources.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val names = people.associate { it.id to it.name }

    fun refresh(list: List<FeedSource>) {
        scope.launch {
            busy = true
            list.forEach { importer.refresh(it, repository) }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import calendars", style = MaterialTheme.typography.titleMedium)
        Text("Linked calendars are copied into Firefly, read-only, and refreshed each time you tap Refresh. Imported events are shared with your family.")

        if (sources.isNotEmpty()) {
            sources.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (s.kind == FeedSource.Kind.PHONE) "Phone calendar" else "Calendar link"} for ${names[s.personId] ?: "someone removed"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val status = when {
                        s.lastError != null -> "Problem: ${s.lastError}"
                        s.lastRefreshed == 0L -> "Not imported yet"
                        else -> "${s.lastCount} events, updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastRefreshed))}"
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { refresh(listOf(s)) }, enabled = !busy) { Text("Refresh") }
                        TextButton(onClick = {
                            scope.launch { repository.replaceSourceEvents(s.id, emptyList()); store.remove(s.id) }
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Button(onClick = { refresh(sources) }, enabled = !busy) { Text(if (busy) "Refreshing..." else "Refresh all") }
            HorizontalDivider()
        }

        if (people.isEmpty()) {
            Text("Add your family on the People tab first, so imported events can be assigned to someone.")
            return@Column
        }

        AddLink(people, onAdd = { source -> store.upsert(source); refresh(listOf(source)) })
        HorizontalDivider()
        AddPhoneCalendars(people, importer, onAdd = { list -> list.forEach(store::upsert); refresh(list) })
    }
}

@Composable
private fun AddLink(people: List<Person>, onAdd: (FeedSource) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var personId by remember { mutableStateOf(people.first().id) }
    Text("Add a calendar link (Outlook, Teams, Google, school)", fontWeight = FontWeight.SemiBold)
    Text(
        "Outlook on the web: Settings > Calendar > Shared calendars > Publish a calendar. Choose \"Can view when I'm busy\" " +
            "(titles hidden) or \"Can view all details\", then copy the ICS link. If your work doesn't allow publishing, " +
            "try the phone-calendar option below, or use Claude's scan.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Olan's work)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(url, { url = it }, label = { Text("Calendar link (.ics)") }, modifier = Modifier.fillMaxWidth())
    PersonChips(people, personId) { personId = it }
    Button(
        enabled = name.isNotBlank() && url.isNotBlank(),
        onClick = {
            onAdd(FeedSource(kind = FeedSource.Kind.ICS, name = name.trim(), target = url.trim(), personId = personId))
            name = ""; url = ""
        },
    ) { Text("Add and import") }
}

@Composable
private fun AddPhoneCalendars(people: List<Person>, importer: Importer, onAdd: (List<FeedSource>) -> Unit) {
    var granted by remember { mutableStateOf(importer.hasCalendarPermission()) }
    var calendars by remember { mutableStateOf(if (granted) importer.listPhoneCalendars() else emptyList()) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var personId by remember { mutableStateOf(people.first().id) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) calendars = importer.listPhoneCalendars()
    }
    Text("Use a calendar on this phone", fontWeight = FontWeight.SemiBold)
    Text(
        "If the Outlook app on this phone syncs your work calendar to Android (Outlook > Settings > your account > Sync calendars), " +
            "Firefly can read it from there, with no sign-in or IT approval.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!granted) {
        OutlinedButton(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow Firefly to read calendars") }
        return
    }
    if (calendars.isEmpty()) Text("No calendars found on this phone.")
    calendars.forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = c.id in chosen, onCheckedChange = { chosen = if (it) chosen + c.id else chosen - c.id })
            Column {
                Text(c.name)
                Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (calendars.isNotEmpty()) {
        PersonChips(people, personId) { personId = it }
        Button(enabled = chosen.isNotEmpty(), onClick = {
            onAdd(calendars.filter { it.id in chosen }.map {
                FeedSource(kind = FeedSource.Kind.PHONE, name = it.name, target = it.id, personId = personId)
            })
            chosen = emptySet()
        }) { Text("Add and import") }
    }
}

@Composable
internal fun PersonChips(people: List<Person>, selected: String, onSelect: (String) -> Unit) {
    Text("Whose calendar is this?", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        people.forEach { p ->
            FilterChip(selected = p.id == selected, onClick = { onSelect(p.id) }, label = { Text(p.name) }, leadingIcon = { ColorDot(p.color) })
        }
    }
}
