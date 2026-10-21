package app.familycalendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** A grid of color swatches with an optional "mix your own" hue slider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchPicker(
    selected: Long,
    palette: List<Long>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    allowCustom: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { c -> Swatch(c, c == selected) { onSelect(c) } }
        }
        if (allowCustom) {
            var hue by remember { mutableFloatStateOf(hueOf(selected)) }
            Text("Mix your own", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = hue,
                    onValueChange = { hue = it; onSelect(fromHue(it)) },
                    valueRange = 0f..360f,
                    modifier = Modifier.weight(1f),
                )
                ColorDot(fromHue(hue), 28.dp)
            }
        }
    }
}

@Composable
private fun Swatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .border(BorderStroke(3.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent), CircleShape)
            .padding(4.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { ColorDot(color, 28.dp) }
}

private fun fromHue(hue: Float): Long = Color.hsv(hue.coerceIn(0f, 359.9f), 0.55f, 0.82f).toArgb().toLong() and 0xFFFFFFFFL

private fun hueOf(color: Long): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toInt(), hsv)
    return hsv[0]
}
