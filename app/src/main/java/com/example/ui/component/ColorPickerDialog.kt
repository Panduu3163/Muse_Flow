package com.example.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A custom accent-colour picker: hue bar plus a saturation/value field.
 *
 * HSV rather than RGB sliders because picking a *shade* is the actual task — hue first, then how
 * vivid and how bright — and that maps directly onto one bar and one square. Any colour chosen here
 * becomes a MaterialKolor seed, so the app still generates a complete, contrast-correct palette
 * from it rather than pasting the raw colour onto controls.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) { initialColor.toHsv() }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val selected = Color.hsv(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent") },
        text = {
            Column(
                modifier = Modifier.testTag("color_picker_dialog"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SaturationValueField(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                    },
                )

                HueBar(hue = hue, onHueChange = { hue = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(selected)
                            .testTag("color_preview")
                    )
                    Text(
                        text = selected.toHexString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Saturation left→right, brightness top→bottom, over the currently selected hue. */
@Composable
private fun SaturationValueField(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(12.dp))
            .testTag("saturation_value_field"),
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    // Tap and drag share one handler so a single tap jumps the selection rather
                    // than requiring a drag to register.
                    detectTapGestures { offset ->
                        onChange(
                            (offset.x / size.width).coerceIn(0f, 1f),
                            1f - (offset.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onChange(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            1f - (change.position.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                }
        ) {
            // White→hue horizontally, then transparent→black vertically: the standard HSV field.
            drawRect(
                Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f)))
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
            )
        }

        // Selection ring, drawn as a layout offset so it tracks the chosen point. No pointerInput
        // of its own - it used to carry an empty one, which was enough to register this Box as a
        // pointer-input node sitting on top of the gesture-handling Canvas below and swallow every
        // tap/drag before that Canvas ever saw it. That's why only the hue bar (no such overlay)
        // responded to touch while this field looked entirely dead.
        Box(modifier = Modifier.matchParentSize()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val x = saturation * size.width
                val y = (1f - value) * size.height
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(x, y),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .testTag("hue_bar"),
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                }
        ) {
            drawRect(
                Brush.horizontalGradient(
                    (0..360 step 60).map { Color.hsv(it.toFloat(), 1f, 1f) }
                )
            )
            val x = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(x, size.height / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

/** `[hue, saturation, value]`, matching what [Color.hsv] takes back. */
private fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (red * 255).roundToInt(),
            (green * 255).roundToInt(),
            (blue * 255).roundToInt(),
        ),
        hsv,
    )
    return hsv
}

private fun Color.toHexString(): String = "#%02X%02X%02X".format(
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
