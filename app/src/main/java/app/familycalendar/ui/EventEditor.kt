package app.familycalendar.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Event
import app.familycalendar.data.Person
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val editorDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

@Composable
fun EventEditorDialog(
    initial: Event?,
    defaultDate: LocalDate,
    people: List<Person>,
    onSave: (Event) -> Unit,
    onDelete: (Event) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var selected by remember { mutableStateOf(initial?.personIds?.toSet() ?: people.take(1).map { it.id }.toSet()) }
    var date by remember { mutableStateOf(initial?.date ?: defaultDate) }
    var allDay by remember { mutableStateOf(initial?.isAllDay ?: false) }
    var start by remember { mutableStateOf(initial?.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(initial?.effectiveEnd ?: LocalTime.of(10, 0)) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var major by remember { mutableStateOf(initial?.major ?: false) }
    var repeatsYearly by remember { mutableStateOf(initial?.repeatsYearly ?: false) }

    val canSave = title.isNotBlank() && selected.isNotEmpty() && (allDay || end > start)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New event" else "Edit event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    ).show()
                }) { Text(date.format(editorDateFormat)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All day", Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, min ->
                                val newStart = LocalTime.of(h, min)
                                if (end <= newStart) end = if (newStart >= LocalTime.of(23, 0)) LocalTime.of(23, 59) else newStart.plusHours(1)
                                start = newStart
                            }, start.hour, start.minute, false).show()
                        }) { Text(formatTime(start)) }
                        Text("to", Modifier.padding(horizontal = 8.dp))
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, min -> end = LocalTime.of(h, min) }, end.hour, end.minute, false).show()
                        }) { Text(formatTime(end)) }
                    }
                    if (end <= start) {
                        Text("End time must be after the start.", color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Major date (shows in Year view)", Modifier.weight(1f))
                    Switch(checked = major, onCheckedChange = { major = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeats every year", Modifier.weight(1f))
                    Switch(checked = repeatsYearly, onCheckedChange = { repeatsYearly = it })
                }
                Text("Who's involved", style = MaterialTheme.typography.titleSmall)
                people.forEach { person ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (person.id in selected) selected - person.id else selected + person.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = person.id in selected,
                            onCheckedChange = { checked -> selected = if (checked) selected + person.id else selected - person.id },
                        )
                        ColorDot(person.color, 12.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(person.name)
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                val base = initial ?: Event(title = "", personIds = emptyList(), date = date)
                onSave(
                    base.copy(
                        title = title.trim(),
                        personIds = people.map { it.id }.filter { it in selected },
                        date = date,
                        start = if (allDay) null else start,
                        end = if (allDay) null else end,
                        notes = notes.trim(),
                        major = major,
                        repeatsYearly = repeatsYearly,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
