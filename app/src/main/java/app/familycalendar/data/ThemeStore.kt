package app.familycalendar.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { System, Light, Dark }

/** How this phone looks. Kept on the phone only: everyone picks their own. */
data class ThemePrefs(
    val palette: String = "hearth",
    /** Overrides the palette's main color; 0 means use the palette's own. */
    val accent: Long = 0L,
    val mode: ThemeMode = ThemeMode.System,
    val fonts: String = "cozy",
    val textScale: Float = 1f,
)

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    var current by mutableStateOf(load())
        private set

    fun update(change: (ThemePrefs) -> ThemePrefs) {
        current = change(current).also(::save)
    }

    fun reset() = update { ThemePrefs() }

    private fun load() = ThemePrefs(
        palette = prefs.getString("palette", "hearth").orEmpty(),
        accent = prefs.getLong("accent", 0L),
        mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", "System").orEmpty()) }.getOrDefault(ThemeMode.System),
        fonts = prefs.getString("fonts", "cozy").orEmpty(),
        textScale = prefs.getFloat("textScale", 1f),
    )

    private fun save(p: ThemePrefs) = prefs.edit()
        .putString("palette", p.palette).putLong("accent", p.accent).putString("mode", p.mode.name)
        .putString("fonts", p.fonts).putFloat("textScale", p.textScale).apply()
}
