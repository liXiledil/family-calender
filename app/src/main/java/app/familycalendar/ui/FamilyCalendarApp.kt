package app.familycalendar.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Event
import app.familycalendar.data.SyncManager
import app.familycalendar.data.Person
import app.familycalendar.data.conflictingEventIds
import app.familycalendar.data.findConflicts
import java.time.LocalDate

private enum class Screen(val label: String) { Day("Day"), Week("Week"), Month("Month"), People("People"), Sync("Sync") }

/** Which event the editor is open for; [event] is null when adding a new one. */
private data class EditorRequest(val event: Event?, val date: LocalDate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyCalendarApp(sync: SyncManager) {
    val store by sync.repository.collectAsState()
    val data by store.data.collectAsState()
    var screenIndex by rememberSaveable { mutableIntStateOf(Screen.Day.ordinal) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }

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
                TopAppBar(title = { Text("Family Calendar") })
                TabRow(selectedTabIndex = screenIndex) {
                    Screen.entries.forEach { s ->
                        Tab(selected = s == screen, onClick = { screenIndex = s.ordinal }, text = { Text(s.label) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (screen != Screen.People && screen != Screen.Sync && data.people.isNotEmpty()) {
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
                Screen.People, Screen.Sync -> Unit
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
