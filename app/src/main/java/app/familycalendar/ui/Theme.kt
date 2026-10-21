@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.familycalendar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import app.familycalendar.R
import app.familycalendar.data.ThemeMode
import app.familycalendar.data.ThemePrefs

/** A named set of soft, low-glare colors with a matching dark version. */
class AppPalette(
    val id: String,
    val name: String,
    val bg: Long, val surface: Long, val variant: Long, val text: Long, val muted: Long, val primary: Long, val tertiary: Long,
    val darkBg: Long, val darkSurface: Long, val darkVariant: Long, val darkText: Long, val darkMuted: Long, val darkPrimary: Long,
)

val AppPalettes = listOf(
    AppPalette("hearth", "Hearth", 0xFFFBF7F2, 0xFFFFFFFF, 0xFFF3ECE3, 0xFF3A3330, 0xFF7D726B, 0xFF4F8A7E, 0xFFF4A261,
        0xFF1E1B19, 0xFF262220, 0xFF332D2A, 0xFFF1EAE4, 0xFFB5A9A0, 0xFF7FB5A9),
    AppPalette("seaside", "Seaside", 0xFFF3F8FA, 0xFFFFFFFF, 0xFFE3EEF3, 0xFF24343B, 0xFF5F7480, 0xFF3B7C9B, 0xFFF2A65A,
        0xFF161D21, 0xFF1D262B, 0xFF28343B, 0xFFE6EEF2, 0xFF9FB2BC, 0xFF7DB6D0),
    AppPalette("blossom", "Blossom", 0xFFFCF5F6, 0xFFFFFFFF, 0xFFF6E7EA, 0xFF3B2C31, 0xFF7E6A71, 0xFFB65C7A, 0xFFE9A86B,
        0xFF21181B, 0xFF291E22, 0xFF38292E, 0xFFF3E8EB, 0xFFBCA6AE, 0xFFDD8FA8),
    AppPalette("meadow", "Meadow", 0xFFF5F8F0, 0xFFFFFFFF, 0xFFE6EEDD, 0xFF2F3A2B, 0xFF69775F, 0xFF5E8B4E, 0xFFD9A441,
        0xFF191E16, 0xFF20271C, 0xFF2D3727, 0xFFE9F0E3, 0xFFA8B79D, 0xFF8DBB7C),
)

fun paletteFor(id: String) = AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()

/** Swatches offered for the app's main color. */
val AccentColors: List<Long> = listOf(
    0xFF4F8A7E, 0xFF3B7C9B, 0xFF5E8B4E, 0xFFB65C7A, 0xFFC0714A, 0xFF8A6BBE,
    0xFF4A6FA5, 0xFFB08A2E, 0xFF2F8F83, 0xFFC25B5B, 0xFF7A6A58, 0xFF5B6B8C,
)

class FontPair(val id: String, val name: String, val note: String, val heading: FontFamily, val body: FontFamily)

private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)

private val Nunito = variable(R.font.nunito, 400, 500, 600, 700)
private val Fredoka = variable(R.font.fredoka, 400, 500, 600)
private val Lora = variable(R.font.lora, 400, 500, 600, 700)
private val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_regular, FontWeight.Medium),
    Font(R.font.atkinson_bold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

val FontPairs = listOf(
    FontPair("cozy", "Cozy", "Round, friendly headings with soft body text", Fredoka, Nunito),
    FontPair("classic", "Classic", "Gentle serif headings, easy sans body", Lora, Nunito),
    FontPair("clear", "Clear", "Designed for easy reading, high contrast shapes", Atkinson, Atkinson),
    FontPair("system", "Plain", "Your phone's own font", FontFamily.Default, FontFamily.Default),
)

fun fontPairFor(id: String) = FontPairs.firstOrNull { it.id == id } ?: FontPairs.first()

val TextScales = listOf("Small" to 0.9f, "Normal" to 1f, "Large" to 1.15f, "Larger" to 1.3f)

private fun onColor(c: Color) = if (c.luminance() > 0.45f) Color(0xFF241F1C) else Color.White

private fun scheme(p: AppPalette, prefs: ThemePrefs, dark: Boolean): ColorScheme {
    val bg = Color(if (dark) p.darkBg else p.bg)
    val surface = Color(if (dark) p.darkSurface else p.surface)
    val variant = Color(if (dark) p.darkVariant else p.variant)
    val text = Color(if (dark) p.darkText else p.text)
    val muted = Color(if (dark) p.darkMuted else p.muted)
    val base = Color(if (dark) p.darkPrimary else p.primary)
    val primary = when {
        prefs.accent == 0L -> base
        dark -> lerp(Color(prefs.accent), Color.White, 0.25f)
        else -> Color(prefs.accent)
    }
    val tertiary = Color(p.tertiary)
    val primaryContainer = lerp(bg, primary, if (dark) 0.35f else 0.22f)
    val tertiaryContainer = lerp(bg, tertiary, if (dark) 0.35f else 0.28f)
    val secondary = lerp(muted, primary, 0.4f)
    val error = Color(if (dark) 0xFFF2B8B5 else 0xFFB3261E)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary, onPrimary = onColor(primary), primaryContainer = primaryContainer, onPrimaryContainer = text,
        secondary = secondary, onSecondary = onColor(secondary), secondaryContainer = variant, onSecondaryContainer = text,
        tertiary = tertiary, onTertiary = onColor(tertiary), tertiaryContainer = tertiaryContainer, onTertiaryContainer = text,
        background = bg, onBackground = text,
        surface = surface, onSurface = text, surfaceVariant = variant, onSurfaceVariant = muted,
        surfaceTint = primary,
        surfaceBright = surface, surfaceDim = bg,
        surfaceContainerLowest = surface, surfaceContainerLow = lerp(bg, surface, 0.6f), surfaceContainer = variant,
        surfaceContainerHigh = lerp(variant, text, 0.04f), surfaceContainerHighest = lerp(variant, text, 0.08f),
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = primary,
        error = error, onError = onColor(error),
        outline = muted.copy(alpha = 0.7f), outlineVariant = muted.copy(alpha = 0.3f),
    )
}

private fun typography(pair: FontPair, scale: Float): Typography {
    val b = Typography()
    fun TextStyle.h() = copy(fontFamily = pair.heading, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    fun TextStyle.t() = copy(fontFamily = pair.body, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = b.displayLarge.h(), displayMedium = b.displayMedium.h(), displaySmall = b.displaySmall.h(),
        headlineLarge = b.headlineLarge.h(), headlineMedium = b.headlineMedium.h(), headlineSmall = b.headlineSmall.h(),
        titleLarge = b.titleLarge.h(), titleMedium = b.titleMedium.h(), titleSmall = b.titleSmall.h(),
        bodyLarge = b.bodyLarge.t(), bodyMedium = b.bodyMedium.t(), bodySmall = b.bodySmall.t(),
        labelLarge = b.labelLarge.t(), labelMedium = b.labelMedium.t(), labelSmall = b.labelSmall.t(),
    )
}

@Composable
fun FamilyCalendarTheme(prefs: ThemePrefs = ThemePrefs(), content: @Composable () -> Unit) {
    val dark = when (prefs.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = scheme(paletteFor(prefs.palette), prefs, dark),
        typography = typography(fontPairFor(prefs.fonts), prefs.textScale),
        content = content,
    )
}
