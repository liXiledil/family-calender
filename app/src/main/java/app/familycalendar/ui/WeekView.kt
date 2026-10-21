package app.familycalendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Event
import app.familycalendar.data.Person
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val weekDayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val weekRangeFormat = DateTimeFormatter.ofPattern("MMM d")

fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

@Composable
fun WeekView(
    date: LocalDate,
    people: List<Person>,
    events: List<Event>,
    conflictIds: Set<String>,
    onDateChange: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (Event) -> Unit,
) {
    val weekStart = startOfWeek(date)
    val days = (0L until 7L).map { weekStart.plusDays(it) }
    val colors = people.associate { it.id to it.color }
    Column(Modifier.fillMaxWidth()) {
        DateHeader(
            title = "${weekStart.format(weekRangeFormat)} – ${weekStart.plusDays(6).format(weekRangeFormat)}",
            onPrev = { onDateChange(date.minusWeeks(1)) },
            onNext = { onDateChange(date.plusWeeks(1)) },
            onToday = { onDateChange(LocalDate.now()) },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(days, key = { it.toEpochDay() }) { day ->
                val dayEvents = events.filter { it.date == day }
                    .sortedWith(compareBy<Event>({ !it.isAllDay }, { it.start }))
                val dayConflicts = dayEvents.count { it.id in conflictIds }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDayClick(day) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.format(weekDayFormat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                            color = if (day == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (dayConflicts > 0) {
                            Text("Conflict", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (dayEvents.isEmpty()) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    dayEvents.forEach { event ->
                        WeekEventRow(event, event.personIds.mapNotNull { colors[it] }, event.id in conflictIds) {
                            onEventClick(event)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WeekEventRow(event: Event, colors: List<Long>, isConflict: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { colors.forEach { ColorDot(it) } }
        Text(timeLabel(event), style = MaterialTheme.typography.labelMedium)
        Text(
            event.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
