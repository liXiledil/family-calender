package app.familycalendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Event
import app.familycalendar.data.Person
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthTitleFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val weekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private const val MAX_DOTS = 4

@Composable
fun MonthView(
    date: LocalDate,
    people: List<Person>,
    events: List<Event>,
    conflictIds: Set<String>,
    onDateChange: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(date)
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7).map { it + List(7 - it.size) { null } }
    val colors = people.associate { it.id to it.color }
    val byDay = events.groupBy { it.date }

    Column(Modifier.fillMaxWidth()) {
        DateHeader(
            title = month.format(monthTitleFormat),
            onPrev = { onDateChange(date.minusMonths(1).withDayOfMonth(1)) },
            onNext = { onDateChange(date.plusMonths(1).withDayOfMonth(1)) },
            onToday = { onDateChange(LocalDate.now()) },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdayLetters.forEach {
                    Text(
                        it,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f).height(68.dp).padding(2.dp)) {
                            if (day != null) {
                                val dayEvents = byDay[day].orEmpty()
                                MonthCell(
                                    day = day,
                                    dotColors = dayEvents.flatMap { it.personIds }.distinct().mapNotNull { colors[it] },
                                    hasConflict = dayEvents.any { it.id in conflictIds },
                                    isSelected = day == date,
                                    onClick = { onDayClick(day) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun MonthCell(day: LocalDate, dotColors: List<Long>, hasConflict: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val border = when {
        hasConflict -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val isToday = day == LocalDate.now()
    Column(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dotColors.take(MAX_DOTS).forEach { ColorDot(it, 7.dp) }
        }
        if (dotColors.size > MAX_DOTS) {
            Text("+${dotColors.size - MAX_DOTS}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
