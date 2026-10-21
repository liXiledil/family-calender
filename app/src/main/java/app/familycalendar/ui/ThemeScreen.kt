package app.familycalendar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.familycalendar.data.ThemeMode
import app.familycalendar.data.ThemeStore

/** Changes apply to the whole app straight away, so the screen itself is the preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(store: ThemeStore) {
    val prefs = store.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Look and feel", style = MaterialTheme.typography.titleLarge)
        Text("Changes apply to the whole app right away, on this phone only.", style = MaterialTheme.typography.bodySmall)

        Section("Header text")
        OutlinedTextField(
            value = prefs.title,
            onValueChange = { v -> store.update { it.copy(title = v.take(30)) } },
            label = { Text("Shown at the top of the app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Header picture")
        val context = LocalContext.current
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && saveHeaderImage(context, uri)) store.update { it.copy(headerImage = System.currentTimeMillis()) }
        }
        Text("A picture replaces the header text. Use a wide, transparent PNG for the best result.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (prefs.headerImage == 0L) "Choose a picture" else "Choose another")
            }
            if (prefs.headerImage != 0L) {
                OutlinedButton(onClick = { deleteHeaderImage(context); store.update { it.copy(headerImage = 0L) } }) { Text("Remove picture") }
            }
        }

        Section("Light or dark")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.System to "Match phone", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark").forEach { (mode, label) ->
                FilterChip(selected = prefs.mode == mode, onClick = { store.update { it.copy(mode = mode) } }, label = { Text(label) })
            }
        }

        Section("Color palette")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppPalettes.forEach { p ->
                val selected = p.id == prefs.palette
                Card(
                    modifier = Modifier.width(150.dp).clickable { store.update { it.copy(palette = p.id) } },
                    border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))) {
                            listOf(p.bg, p.variant, p.primary, p.tertiary).forEach { c ->
                                Box(Modifier.weight(1f).height(28.dp).background(Color(c)))
                            }
                        }
                        Text(p.name, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Section("Main color")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = prefs.accent == 0L, onClick = { store.update { it.copy(accent = 0L) } }, label = { Text("Palette's own") })
        }
        ColorSwatchPicker(selected = prefs.accent, palette = AccentColors, onSelect = { c -> store.update { it.copy(accent = c) } })

        Section("Font")
        FontPairs.forEach { pair ->
            val selected = pair.id == prefs.fonts
            Card(
                modifier = Modifier.fillMaxWidth().clickable { store.update { it.copy(fonts = pair.id) } },
                border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Soccer practice · Thursday",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = pair.heading),
                    )
                    Text(
                        "Maya at 5:00, pick-up at Riverside field. ${pair.note}.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = pair.body),
                    )
                    Text(pair.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Section("Text size")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextScales.forEach { (label, scale) ->
                FilterChip(selected = prefs.textScale == scale, onClick = { store.update { it.copy(textScale = scale) } }, label = { Text(label) })
            }
        }

        OutlinedButton(onClick = { deleteHeaderImage(context); store.reset() }) { Text("Back to the default look") }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
