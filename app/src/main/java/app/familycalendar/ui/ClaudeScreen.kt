package app.familycalendar.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.familycalendar.data.ApiKeyStore
import app.familycalendar.data.Assistant
import app.familycalendar.data.Attachment
import app.familycalendar.data.AttachmentLoader
import app.familycalendar.data.AuditAction
import app.familycalendar.data.AuditProposal
import app.familycalendar.data.ClaudeClient
import app.familycalendar.data.Event
import app.familycalendar.data.FamilyRepository
import app.familycalendar.data.Person
import app.familycalendar.data.ProposedEvent
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Mode(val label: String) { Scan("Scan"), Audit("Audit"), Ask("Ask") }

private val rowDate = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
fun ClaudeScreen(
    people: List<Person>,
    events: List<Event>,
    repository: FamilyRepository,
    claude: ClaudeClient,
    settings: ApiKeyStore,
    incoming: MutableList<Uri>,
) {
    var hasKey by remember { mutableStateOf(settings.hasKey()) }
    var mode by remember { mutableStateOf(Mode.Scan) }
    var showSettings by remember { mutableStateOf(!settings.hasKey()) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Claude", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (hasKey) TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Hide settings" else "Settings") }
        }
        if (showSettings || !hasKey) {
            KeySettings(settings, onChanged = { hasKey = settings.hasKey(); if (hasKey) showSettings = false })
        }
        if (!hasKey) return@Column

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { m -> FilterChip(selected = m == mode, onClick = { mode = m }, label = { Text(m.label) }) }
        }
        when (mode) {
            Mode.Scan -> ScanPanel(people, events, repository, claude, incoming)
            Mode.Audit -> AuditPanel(people, events, repository, claude)
            Mode.Ask -> AskPanel(people, events, claude)
        }
    }
}

@Composable
private fun KeySettings(settings: ApiKeyStore, onChanged: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(settings.model) }
    var cap by remember { mutableStateOf("%.0f".format(settings.monthlyCapUsd)) }
    var usage by remember { mutableStateOf(settings.usageThisMonth()) }
    Text(
        "Firefly uses your own Anthropic API key (console.anthropic.com > API keys). It is stored encrypted on this phone only, " +
            "never uploaded, so enter it once on each phone where you want Claude. Claude only suggests changes; you approve every one.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(if (settings.hasKey()) "Replace API key (sk-ant-...)" else "API key (sk-ant-...)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            cap, { cap = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly limit ($)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
        )
    }
    Text(
        "This month on this phone: about $${"%.2f".format(usage.estimatedUsd)} of your $${cap.ifEmpty { "0" }} limit " +
            "(an estimate from token counts; your Anthropic console has the exact bill).",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = key.isNotBlank() || settings.hasKey(), onClick = {
            if (key.isNotBlank()) settings.save(key)
            settings.model = model
            cap.toDoubleOrNull()?.let { settings.monthlyCapUsd = it }
            key = ""
            usage = settings.usageThisMonth()
            onChanged()
        }) { Text("Save") }
        if (settings.hasKey()) OutlinedButton(onClick = { settings.clear(); onChanged() }) { Text("Remove key") }
    }
    HorizontalDivider()
}

// ---------------------------------------------------------------- Scan

@Composable
private fun ScanPanel(people: List<Person>, events: List<Event>, repository: FamilyRepository, claude: ClaudeClient, incoming: MutableList<Uri>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments = remember { mutableStateListOf<Pair<String, Attachment>>() }
    val proposals = remember { mutableStateListOf<ReviewRow>() }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf<List<String>>(emptyList()) }
    var editing by remember { mutableStateOf<Int?>(null) }

    fun addUris(uris: List<Uri>) {
        scope.launch {
            uris.forEach { uri ->
                AttachmentLoader.load(context, uri)
                    .onSuccess { attachments += (uri.lastPathSegment ?: "file") to it }
                    .onFailure { error = it.message }
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addUris(it) }
    LaunchedEffect(incoming.size) {
        if (incoming.isNotEmpty()) {
            val uris = incoming.toList()
            incoming.clear()
            addUris(uris)
        }
    }

    Text(
        "Add a screenshot or photo of a schedule (Outlook, Teams, a school flyer), a PDF, or just type or paste events. " +
            "You can also share an image to Firefly from any app.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) { Text("Add photos or PDFs") }
    }
    attachments.forEachIndexed { i, (name, a) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${if (a.mimeType == "application/pdf") "PDF" else "Image"}: $name", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { attachments.removeAt(i) }) { Text("Remove") }
        }
    }
    OutlinedTextField(
        text, { text = it }, label = { Text("Or type events, e.g. \"Maya soccer Tuesdays 5-6pm until Dec 12\"") },
        modifier = Modifier.fillMaxWidth(), minLines = 2,
    )
    Button(enabled = !busy && (attachments.isNotEmpty() || text.isNotBlank()), onClick = {
        scope.launch {
            busy = true; error = null; proposals.clear(); added = emptyList()
            claude.scan(attachments.map { it.second }, text, people, events, LocalDate.now())
                .onSuccess { found ->
                    found.forEach { proposals += ReviewRow(it.event, it.confidence, it.possibleDuplicate, checked = !it.possibleDuplicate) }
                    if (found.isEmpty()) error = "Claude didn't find any events in that."
                }
                .onFailure { error = it.message }
            busy = false
        }
    }) { Text(if (busy) "Reading..." else "Read with Claude") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (proposals.isNotEmpty()) {
        HorizontalDivider()
        Text("Review, then add. Tap a row to edit it.", fontWeight = FontWeight.SemiBold)
        proposals.forEachIndexed { i, row ->
            Row(
                Modifier.fillMaxWidth().clickable { editing = i },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = row.checked, onCheckedChange = { proposals[i] = row.copy(checked = it) })
                Column(Modifier.weight(1f)) {
                    Text(row.event.title, fontWeight = FontWeight.Medium)
                    Text("${row.event.date.format(rowDate)} · ${timeLabel(row.event)} · " +
                        (row.event.personIds.mapNotNull { id -> people.firstOrNull { it.id == id }?.name }.joinToString().ifEmpty { "no one assigned" }),
                        style = MaterialTheme.typography.bodySmall)
                    if (row.duplicate) Text("Looks like one already on the calendar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    else if (row.confidence == "low") Text("Hard to read: please check", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        val chosen = proposals.filter { it.checked }
        Button(enabled = chosen.isNotEmpty() && chosen.all { it.event.personIds.isNotEmpty() }, onClick = {
            chosen.forEach { repository.upsertEvent(it.event) }
            added = chosen.map { it.event.id }
            proposals.clear(); attachments.clear(); text = ""
        }) { Text("Add ${chosen.size} event${if (chosen.size == 1) "" else "s"}") }
        if (chosen.any { it.event.personIds.isEmpty() }) {
            Text("Tap a row and choose who each event is for before adding.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (added.isNotEmpty()) {
        Text("Added ${added.size} event${if (added.size == 1) "" else "s"} to the calendar.", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { added.forEach(repository::removeEvent); added = emptyList() }) { Text("Undo") }
    }

    editing?.let { i ->
        val row = proposals.getOrNull(i)
        if (row == null) editing = null else EventEditorDialog(
            initial = row.event,
            defaultDate = row.event.date,
            people = people,
            onSave = { proposals[i] = row.copy(event = it, checked = true); editing = null },
            onDelete = { proposals.removeAt(i); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private data class ReviewRow(val event: Event, val confidence: String, val duplicate: Boolean, val checked: Boolean)

// ---------------------------------------------------------------- Audit

@Composable
private fun AuditPanel(people: List<Person>, events: List<Event>, repository: FamilyRepository, claude: ClaudeClient) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    val proposals = remember { mutableStateListOf<AuditProposal>() }
    val selected = remember { mutableStateListOf<AuditProposal>() }
    var undo by remember { mutableStateOf<Pair<List<Event>, List<Event>>?>(null) }
    val byId = events.associateBy { it.id }

    fun describe(p: AuditProposal): String {
        val first = byId[p.eventIds.firstOrNull()]
        val label = first?.let { "\"${it.title}\" on ${it.date.format(rowDate)}" } ?: "an event"
        return when (p.action) {
            AuditAction.DELETE -> "Delete $label" + if (p.eventIds.size > 1) " and ${p.eventIds.size - 1} more" else ""
            AuditAction.MERGE -> "Merge ${p.eventIds.size} copies of $label into one"
            AuditAction.EDIT -> "Change $label" + listOfNotNull(
                p.title.takeIf { it.isNotEmpty() }?.let { "title to \"$it\"" },
                p.date?.let { "date to ${it.format(rowDate)}" },
                p.start?.let { "start to ${formatTime(it)}" },
                p.end?.let { "end to ${formatTime(it)}" },
                p.personIds.takeIf { it.isNotEmpty() }?.let { ids -> "people to " + ids.mapNotNull { id -> people.firstOrNull { it.id == id }?.name }.joinToString() },
            ).joinToString(prefix = ": ")
        }
    }

    Text(
        "Claude reviews your calendar for duplicates, inconsistent names, events with no one assigned, and wrong times, and suggests tidy-ups. " +
            "Linked-calendar events are never changed.",
        style = MaterialTheme.typography.bodySmall,
    )
    Button(enabled = !busy && events.isNotEmpty(), onClick = {
        scope.launch {
            busy = true; error = null; summary = null; proposals.clear(); selected.clear(); undo = null
            claude.audit(events, people, LocalDate.now())
                .onSuccess { r -> summary = r.summary.ifEmpty { "Done." }; proposals += r.proposals; selected += r.proposals }
                .onFailure { error = it.message }
            busy = false
        }
    }) { Text(if (busy) "Reviewing..." else "Audit my calendar") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    summary?.let { Text(it) }
    if (proposals.isNotEmpty()) {
        HorizontalDivider()
        proposals.forEach { p ->
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = p in selected, onCheckedChange = { if (it) selected += p else selected -= p })
                Column(Modifier.weight(1f).padding(top = 12.dp)) {
                    Text(describe(p), fontWeight = FontWeight.Medium)
                    if (p.reason.isNotEmpty()) Text(p.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(enabled = selected.isNotEmpty(), onClick = {
            val changes = Assistant.changesFor(selected.toList(), events)
            undo = changes.upserts.mapNotNull { byId[it.id] } to changes.deletes
            changes.deletes.forEach { repository.removeEvent(it.id) }
            changes.upserts.forEach(repository::upsertEvent)
            proposals.clear(); selected.clear()
        }) { Text("Apply ${selected.size} selected") }
    }
    undo?.let { (originals, deleted) ->
        Text("Applied. Everything is saved to your family calendar.", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { (originals + deleted).forEach(repository::upsertEvent); undo = null }) { Text("Undo") }
    }
}

// ---------------------------------------------------------------- Ask

@Composable
private fun AskPanel(people: List<Person>, events: List<Event>, claude: ClaudeClient) {
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text("Ask about the calendar in plain words: \"Who's free Thursday at 5?\" or \"What's coming up for Maya this month?\"", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(question, { question = it }, label = { Text("Your question") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Button(enabled = !busy && question.isNotBlank(), onClick = {
        scope.launch {
            busy = true; error = null; answer = null
            claude.ask(question, events, people, LocalDate.now()).onSuccess { answer = it }.onFailure { error = it.message }
            busy = false
        }
    }) { Text(if (busy) "Thinking..." else "Ask") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    answer?.let { Text(it) }
}
