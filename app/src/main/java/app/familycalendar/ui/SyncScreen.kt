package app.familycalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.familycalendar.data.SyncManager
import app.familycalendar.data.SyncState

@Composable
fun SyncScreen(sync: SyncManager) {
    val state by sync.state.collectAsState()
    val busy by sync.busy.collectAsState()
    val error by sync.error.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sync across devices", style = MaterialTheme.typography.titleMedium)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = sync::clearError) { Text("Dismiss") }
        }
        when (val s = state) {
            SyncState.NotConfigured -> Text(
                "Cloud sync isn't set up in this version of the app yet, so your calendar is saved on this phone only.",
            )
            SyncState.SignedOut -> SignInForm(busy, onSignIn = sync::signIn, onCreate = sync::createAccount)
            is SyncState.NoFamily -> FamilySetup(s.email, busy, sync)
            is SyncState.InFamily -> InFamily(s, sync)
        }
    }
}

@Composable
private fun SignInForm(busy: Boolean, onSignIn: (String, String) -> Unit, onCreate: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val ready = !busy && email.isNotBlank() && password.length >= 6
    Text("Sign in so everyone in the family sees the same calendar on their own phone.")
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password (6+ characters)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onSignIn(email, password) }, enabled = ready) { Text("Sign in") }
        OutlinedButton(onClick = { onCreate(email, password) }, enabled = ready) { Text("Create account") }
    }
}

@Composable
private fun FamilySetup(email: String, busy: Boolean, sync: SyncManager) {
    var familyName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Text("Signed in as $email")
    HorizontalDivider()
    Text("Start a family", fontWeight = FontWeight.SemiBold)
    Text("Copies the people and events on this phone into a shared family calendar. You'll get a code to invite others.")
    OutlinedTextField(
        value = familyName,
        onValueChange = { familyName = it },
        label = { Text("Family name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { sync.createFamily(familyName) }, enabled = !busy && familyName.isNotBlank()) {
        Text("Create family")
    }
    HorizontalDivider()
    Text("Join a family", fontWeight = FontWeight.SemiBold)
    Text("Enter the invite code from the Sync tab on a family member's phone.")
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.uppercase() },
        label = { Text("Invite code") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { sync.joinFamily(code) }, enabled = !busy && code.trim().length == 8) { Text("Join") }
    HorizontalDivider()
    TextButton(onClick = sync::signOut) { Text("Sign out") }
}

@Composable
private fun InFamily(state: SyncState.InFamily, sync: SyncManager) {
    Text(
        if (state.familyName.isBlank()) "Syncing with your family" else "Syncing with ${state.familyName}",
        fontWeight = FontWeight.SemiBold,
    )
    Text("Signed in as ${state.email}. Changes show up on every family member's phone, and edits made offline upload when you're back online.")
    HorizontalDivider()
    Text("Invite someone", fontWeight = FontWeight.SemiBold)
    Text("On their phone: install the app, open Sync, create an account, then enter this code under Join a family.")
    Text(state.inviteCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    HorizontalDivider()
    TextButton(onClick = sync::signOut) { Text("Sign out") }
}
