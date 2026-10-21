package app.familycalendar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Conflict
import app.familycalendar.data.Event
import app.familycalendar.data.Person
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, MMM d")
private const val UNASSIGNED_COLOR = 0xFF9E9E9E

/** One column per family member so overlaps across people line up side by side. */
@Composable
fun DayView(
    date: LocalDate,
    people: List<Person>,
    allPeople: List<Person>,
    events: List<Event>,
    conflicts: List<Conflict>,
    conflictIds: Set<String>,
    onDateChange: (LocalDate) -> Unit,
    onEventClick: (Event) -> Unit,
) {
    val names = allPeople.associate { it.id to it.name }
    Column(Modifier.fillMaxWidth()) {
        DateHeader(
            title = date.format(dayTitleFormat),
            onPrev = { onDateChange(date.minusDays(1)) },
            onNext = { onDateChange(date.plusDays(1)) },
            onToday = { onDateChange(LocalDate.now()) },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
            if (conflicts.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        if (conflicts.size == 1) "1 conflict" else "${conflicts.size} conflicts",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    conflicts.forEach { c ->
                        Text(
                            "${names[c.personId] ?: "Someone"}: ${c.first.title} overlaps ${c.second.title}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (events.isEmpty()) {
                Text("Nothing scheduled.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                people.forEach { person ->
                    PersonColumn(
                        name = person.name,
                        color = person.color,
                        events = events.filter { person.id in it.personIds },
                        conflictIds = conflictIds,
                        onEventClick = onEventClick,
                    )
                }
                val unassigned = events.filter { it.personIds.isEmpty() }
                if (unassigned.isNotEmpty()) {
                    PersonColumn("Unassigned", UNASSIGNED_COLOR, unassigned, conflictIds, onEventClick)
                }
            }
        }
    }
}

@Composable
private fun PersonColumn(
    name: String,
    color: Long,
    events: List<Event>,
    conflictIds: Set<String>,
    onEventClick: (Event) -> Unit,
) {
    Column(Modifier.width(170.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorDot(color, 12.dp)
            Text(name, style = MaterialTheme.typography.titleSmall)
        }
        events.sortedWith(compareBy<Event>({ !it.isAllDay }, { it.start })).forEach { event ->
            EventCard(event, color, event.id in conflictIds) { onEventClick(event) }
        }
        if (events.isEmpty()) {
            Text("Free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
