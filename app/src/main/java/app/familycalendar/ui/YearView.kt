package app.familycalendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Event
import app.familycalendar.data.Person
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val yearDayFormat = DateTimeFormatter.ofPattern("EEE d")

/** Major dates only: birthdays, holidays, trips and deadlines, month by month. */
@Composable
fun YearView(
    date: LocalDate,
    people: List<Person>,
    events: List<Event>,
    onDateChange: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val year = date.year
    val colors = people.associate { it.id to it.color }
    val major = events.filter { (it.major || it.repeatsYearly) && it.date.year == year }.sortedBy { it.date }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth()) {
        DateHeader(
            title = year.toString(),
            onPrev = { onDateChange(date.minusYears(1)) },
            onNext = { onDateChange(date.plusYears(1)) },
            onToday = { onDateChange(today) },
        )
        if (major.isEmpty()) {
            Text(
                "No major dates yet. When you add an event, turn on \"Major date\" or \"Repeats every year\" to show it here.",
                Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            Month.values().forEach { month ->
                val inMonth = major.filter { it.date.month == month }
                if (inMonth.isEmpty()) return@forEach
                item(key = "m$month") {
                    Text(
                        month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(inMonth, key = { "${it.id}-${it.date}" }) { event ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onDayClick(event.date) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(event.date.format(yearDayFormat), Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            event.personIds.mapNotNull { colors[it] }.forEach { ColorDot(it) }
                        }
                        Text(event.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        val days = ChronoUnit.DAYS.between(today, event.date)
                        if (days in 0..60) {
                            Text(
                                if (days == 0L) "Today" else "in $days d",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "d$month") { HorizontalDivider(Modifier.padding(top = 4.dp)) }
            }
        }
    }
}
