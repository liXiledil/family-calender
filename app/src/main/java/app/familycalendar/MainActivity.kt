package app.familycalendar

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import app.familycalendar.data.ApiKeyStore
import app.familycalendar.data.ClaudeClient
import app.familycalendar.data.FamilyStore
import app.familycalendar.data.FeedSourceStore
import app.familycalendar.data.Importer
import app.familycalendar.data.SharingPrefs
import app.familycalendar.data.SyncManager
import app.familycalendar.ui.FamilyCalendarApp
import app.familycalendar.ui.FamilyCalendarTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val sync by lazy { SyncManager(applicationContext, FamilyStore(File(filesDir, "family.json"))) }
    private val feedSources by lazy { FeedSourceStore(applicationContext) }
    private val importer by lazy { Importer(applicationContext, feedSources) }
    private val apiKeys by lazy { ApiKeyStore(applicationContext) }
    private val claude by lazy { ClaudeClient(apiKeys) }
    private val sharedUris = mutableStateListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectShared(intent)
        // Resume location sharing after a restart if this phone had it switched on.
        val sharing = SharingPrefs(applicationContext)
        if (sharing.shareLocation || sharing.receiveAlerts) runCatching { LocationShareService.start(applicationContext) }
        setContent {
            FamilyCalendarTheme {
                FamilyCalendarApp(sync, feedSources, importer, apiKeys, claude, sharedUris)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        collectShared(intent)
    }

    @Suppress("DEPRECATION")
    private fun collectShared(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { sharedUris += it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.let { sharedUris.addAll(it) }
            }
        }
    }
}
