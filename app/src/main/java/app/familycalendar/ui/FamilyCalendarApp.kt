package app.familycalendar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.familycalendar.data.ApiKeyStore
import app.familycalendar.data.ClaudeClient
import app.familycalendar.data.ThemeStore
import app.familycalendar.data.FamilyLocations
import app.familycalendar.data.FirebaseBootstrap
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import app.familycalendar.data.SyncState
import com.google.firebase.firestore.FirebaseFirestore
import app.familycalendar.data.FeedSourceStore
import app.familycalendar.data.Importer
import app.familycalendar.data.expandYearly
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Event
import app.familycalendar.data.SyncManager
import app.familycalendar.data.Person
import app.familycalendar.data.conflictingEventIds
import app.familycalendar.data.findConflicts
import java.time.LocalDate

private enum class Screen(val label: String) {
    Day("Day"), Week("Week"), Month("Month"), Year("Year"), Live("Map"), Import("Import"), Claude("Claude"), Family("Family"), Look("Look"), People("People"), Sync("Sync")
}

/** Tabs that are not calendar views: no person filter, no add button. */
private val toolScreens = setOf(Screen.Live, Screen.Import, Screen.Claude, Screen.Family, Screen.Look, Screen.People, Screen.Sync)

/** Which event the editor is open for; [event] is null when adding a new one. */
private data class EditorRequest(val event: Event?, val date: LocalDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyCalendarApp(
    sync: SyncManager,
    feedSources: FeedSourceStore,
    importer: Importer,
    apiKeys: ApiKeyStore,
    claude: ClaudeClient,
    sharedUris: SnapshotStateList<Uri>,
    themeStore: ThemeStore,
) {
    val store by sync.repository.collectAsState()
    val syncState by sync.state.collectAsState()
    // One live connection to the family's locations, shared by the Map and Family tabs.
    val appContext = LocalContext.current.applicationContext
    val familyId = (syncState as? SyncState.InFamily)?.familyId
    val locations = remember(familyId) {
        familyId?.let { id -> FirebaseBootstrap.app(appContext)?.let { FamilyLocations(FirebaseFirestore.getInstance(it), id) } }
    }
    DisposableEffect(locations) { onDispose { locations?.close() } }
    val data by store.data.collectAsState()
    var screenIndex by rememberSaveable { mutableIntStateOf(Screen.Day.ordinal) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }

    // A photo or PDF shared into Firefly opens the Claude tab.
    LaunchedEffect(sharedUris.size) { if (sharedUris.isNotEmpty()) screenIndex = Screen.Claude.ordinal }

    val screen = Screen.entries[screenIndex]
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val visiblePeople = data.people.filter { it.id !in hidden }
    val visibleEvents = data.events.filter { e -> e.personIds.isEmpty() || e.personIds.any { it !in hidden } }
    val conflicts = remember(data.events) { findConflicts(data.events) }
    val visibleConflicts = conflicts.filter { it.personId !in hidden }
    val conflictIds = conflictingEventIds(visibleConflicts)
    val openEvent: (Event) -> Unit = { editor = EditorRequest(it, it.date) }
    val selectDate: (LocalDate) -> Unit = { selectedEpochDay = it.toEpochDay() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = {
                    val look = themeStore.current
                    val name = look.title.trim().ifEmpty { "Firefly" }
                    val picture = remember(look.headerImage) { if (look.headerImage != 0L) loadHeaderImage(appContext) else null }
                    if (picture != null) {
                        Image(picture, contentDescription = name, modifier = Modifier.height(36.dp), contentScale = ContentScale.Fit)
                    } else {
                        Text(name)
                    }
                })
                ScrollableTabRow(selectedTabIndex = screenIndex, edgePadding = 8.dp) {
                    Screen.entries.forEach { s ->
                        Tab(selected = s == screen, onClick = { screenIndex = s.ordinal }, text = { Text(s.label) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (screen !in toolScreens && data.people.isNotEmpty()) {
                FloatingActionButton(onClick = { editor = EditorRequest(null, selectedDate) }) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (screen == Screen.Sync) {
                SyncScreen(sync)
                return@Column
            }
            if (screen == Screen.Look) {
                ThemeScreen(themeStore)
                return@Column
            }
            if (screen == Screen.Family) {
                FamilyScreen(data.people, syncState, locations)
                return@Column
            }
            if (screen == Screen.Live) {
                MapScreen(data.people, syncState, locations)
                return@Column
            }
            if (screen == Screen.People) {
                PeopleScreen(
                    people = data.people,
                    onSave = { store.upsertPerson(it) },
                    onDelete = { store.removePerson(it) },
                )
                return@Column
            }
            if (data.people.isEmpty()) {
                NoPeoplePrompt(onAddPeople = { screenIndex = Screen.People.ordinal })
                return@Column
            }
            if (screen == Screen.Import) {
                ImportScreen(data.people, store, feedSources, importer)
                return@Column
            }
            if (screen == Screen.Claude) {
                ClaudeScreen(data.people, data.events, store, claude, apiKeys, sharedUris)
                return@Column
            }
            PersonFilterRow(data.people, hidden) { id -> hidden = if (id in hidden) hidden - id else hidden + id }
            when (screen) {
                Screen.Day -> DayView(
                    date = selectedDate,
                    people = visiblePeople,
                    allPeople = data.people,
                    events = visibleEvents.filter { it.date == selectedDate },
                    conflicts = visibleConflicts.filter { it.first.date == selectedDate },
                    conflictIds = conflictIds,
                    onDateChange = selectDate,
                    onEventClick = openEvent,
                )
                Screen.Week -> WeekView(
                    date = selectedDate,
                    people = data.people,
                    events = visibleEvents,
                    conflictIds = conflictIds,
                    onDateChange = selectDate,
                    onDayClick = { selectDate(it); screenIndex = Screen.Day.ordinal },
                    onEventClick = openEvent,
                )
                Screen.Month -> MonthView(
                    date = selectedDate,
                    people = data.people,
                    events = visibleEvents,
                    conflictIds = conflictIds,
                    onDateChange = selectDate,
                    onDayClick = { selectDate(it); screenIndex = Screen.Day.ordinal },
                )
                Screen.Year -> YearView(
                    date = selectedDate,
                    people = data.people,
                    events = remember(visibleEvents, selectedDate.year) { expandYearly(visibleEvents, selectedDate.year..selectedDate.year) },
                    onDateChange = selectDate,
                    onDayClick = { selectDate(it); screenIndex = Screen.Day.ordinal },
                )
                else -> Unit
            }
        }
    }

    editor?.let { request ->
        EventEditorDialog(
            initial = request.event,
            defaultDate = request.date,
            people = data.people,
            onSave = { store.upsertEvent(it); editor = null },
            onDelete = { store.removeEvent(it.id); editor = null },
            onDismiss = { editor = null },
        )
    }
}

@Composable
private fun PersonFilterRow(people: List<Person>, hidden: Set<String>, onToggle: (String) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        people.forEach { p ->
            FilterChip(
                selected = p.id !in hidden,
                onClick = { onToggle(p.id) },
                label = { Text(p.name) },
                leadingIcon = { ColorDot(p.color) },
            )
        }
    }
}

@Composable
private fun NoPeoplePrompt(onAddPeople: () -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text("Start by adding your family.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Each person gets a color so you can see everyone's schedule and spot clashes at a glance.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddPeople) { Text("Add family members") }
    }
}
