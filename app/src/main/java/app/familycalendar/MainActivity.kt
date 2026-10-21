package app.familycalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.familycalendar.data.FamilyStore
import app.familycalendar.ui.FamilyCalendarApp
import app.familycalendar.ui.FamilyCalendarTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val store by lazy { FamilyStore(File(filesDir, "family.json")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyCalendarTheme {
                FamilyCalendarApp(store)
            }
        }
    }
}
