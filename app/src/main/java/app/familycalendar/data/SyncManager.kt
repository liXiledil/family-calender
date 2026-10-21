package app.familycalendar.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

sealed interface SyncState {
    /** No Firebase project is configured in this build, so the calendar stays on this phone. */
    data object NotConfigured : SyncState
    data object SignedOut : SyncState
    data class NoFamily(val email: String) : SyncState
    data class InFamily(
        val email: String,
        val familyName: String,
        val inviteCode: String,
        val familyId: String,
        val uid: String,
    ) : SyncState
}

/**
 * Signs the user in and points the app at the right calendar: the local file until they
 * create or join a family, then the shared family in Firestore.
 */
class SyncManager(context: Context, private val local: FamilyStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val auth: FirebaseAuth?
    private val db: FirebaseFirestore?

    private val _state = MutableStateFlow<SyncState>(SyncState.NotConfigured)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _repository = MutableStateFlow<FamilyRepository>(local)
    val repository: StateFlow<FamilyRepository> = _repository.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        val app = FirebaseBootstrap.app(context)
        auth = app?.let { FirebaseAuth.getInstance(it) }
        db = app?.let { FirebaseFirestore.getInstance(it) }
        if (auth != null) {
            _state.value = SyncState.SignedOut
            auth.addAuthStateListener { scope.launch { refresh() } }
        }
    }

    fun signIn(email: String, password: String) = launchTask {
        auth!!.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun createAccount(email: String, password: String) = launchTask {
        auth!!.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() {
        auth?.signOut()
    }

    /** Creates a family and copies this phone's people and events into it. */
    fun createFamily(name: String) = launchTask {
        val user = auth!!.currentUser ?: throw IllegalStateException("Not signed in")
        val db = db!!
        val family = db.collection("families").document()
        val code = newInviteCode()
        val batch = db.batch()
        batch.set(family, mapOf("name" to name.trim(), "owner" to user.uid, "inviteCode" to code))
        batch.set(family.collection("members").document(user.uid), mapOf("email" to (user.email ?: "")))
        batch.set(db.collection("invites").document(code), mapOf("familyId" to family.id))
        batch.set(db.collection("users").document(user.uid), mapOf("familyId" to family.id))
        val data = local.data.value
        data.people.forEachIndexed { i, p ->
            batch.set(family.collection("people").document(p.id), CloudFamilyRepository.personFields(p, i))
        }
        data.events.forEach { e ->
            batch.set(family.collection("events").document(e.id), CloudFamilyRepository.eventFields(e))
        }
        batch.commit().await()
        refresh()
    }

    fun joinFamily(code: String) = launchTask {
        val user = auth!!.currentUser ?: throw IllegalStateException("Not signed in")
        val db = db!!
        val cleaned = code.trim().uppercase()
        val invite = db.collection("invites").document(cleaned).get().await()
        val familyId = invite.getString("familyId") ?: throw IllegalStateException("That invite code wasn't found.")
        val family = db.collection("families").document(familyId)
        family.collection("members").document(user.uid)
            .set(mapOf("email" to (user.email ?: ""), "code" to cleaned)).await()
        db.collection("users").document(user.uid).set(mapOf("familyId" to familyId)).await()
        refresh()
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun refresh() {
        val auth = auth ?: return
        val db = db ?: return
        val user = auth.currentUser
        if (user == null) {
            attach(local)
            _state.value = SyncState.SignedOut
            return
        }
        val email = user.email ?: ""
        val familyId = runCatching {
            db.collection("users").document(user.uid).get().await().getString("familyId")
        }.getOrNull()
        if (familyId == null) {
            attach(local)
            _state.value = SyncState.NoFamily(email)
            return
        }
        val family = runCatching { db.collection("families").document(familyId).get().await() }.getOrNull()
        val current = _repository.value
        if (current !is CloudFamilyRepository || current.familyId != familyId) {
            attach(CloudFamilyRepository(db, familyId))
        }
        _state.value = SyncState.InFamily(
            email = email,
            familyName = family?.getString("name").orEmpty(),
            inviteCode = family?.getString("inviteCode").orEmpty(),
            familyId = familyId,
            uid = user.uid,
        )
    }

    private fun attach(repository: FamilyRepository) {
        val previous = _repository.value
        if (previous === repository) return
        if (previous is CloudFamilyRepository) previous.close()
        _repository.value = repository
    }

    private fun launchTask(block: suspend () -> Unit) {
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                block()
            } catch (e: Exception) {
                _error.value = e.message ?: "Something went wrong."
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun newInviteCode(random: SecureRandom = SecureRandom()): String =
            (1..8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }
}
