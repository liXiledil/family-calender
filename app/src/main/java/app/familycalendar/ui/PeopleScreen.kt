package app.familycalendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.familycalendar.data.Person
import app.familycalendar.data.PersonColors

@Composable
fun PeopleScreen(people: List<Person>, onSave: (Person) -> Unit, onDelete: (String) -> Unit) {
    var editing by remember { mutableStateOf<Person?>(null) }
    var confirmDelete by remember { mutableStateOf<Person?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Family members", style = MaterialTheme.typography.titleMedium)
        if (people.isEmpty()) {
            Text("No one yet. Add yourself and your family below.")
        }
        people.forEach { person ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorDot(person.color, 18.dp)
                Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { editing = person }) { Text("Edit") }
                TextButton(onClick = { confirmDelete = person }) { Text("Remove") }
            }
        }
        HorizontalDivider()
        val nextColor = PersonColors.firstOrNull { c -> people.none { it.color == c } } ?: PersonColors.first()
        // Keyed on the count so the form resets with a fresh color after each add.
        key(people.size) {
            PersonForm(
                title = "Add a family member",
                initialName = "",
                initialColor = nextColor,
                actionLabel = "Add",
                onSubmit = { name, color -> onSave(Person(name = name, color = color)) },
            )
        }
    }

    editing?.let { person ->
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            text = {
                PersonForm(
                    title = "Edit ${person.name}",
                    initialName = person.name,
                    initialColor = person.color,
                    actionLabel = "Save",
                    onSubmit = { name, color ->
                        onSave(person.copy(name = name, color = color))
                        editing = null
                    },
                )
            },
        )
    }

    confirmDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${person.name}?") },
            text = { Text("Their events stay on the calendar but will no longer be assigned to them.") },
            confirmButton = {
                TextButton(onClick = { onDelete(person.id); confirmDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PersonForm(
    title: String,
    initialName: String,
    initialColor: Long,
    actionLabel: String,
    onSubmit: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ColorSwatchPicker(selected = color, palette = PersonColors, onSelect = { color = it })
        Button(onClick = { onSubmit(name.trim(), color) }, enabled = name.isNotBlank()) { Text(actionLabel) }
    }
}
