package com.example.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.sin

/** A broad, continuous expressive wave with a clean thumb gap and full touch target. */
@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    playing: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragProgress >= 0f) dragProgress else progress.coerceIn(0f, 1f)
    val seekAction by rememberUpdatedState(onSeek)
    val amplitude by animateFloatAsState(
        if (playing && dragProgress < 0f && !LocalReducedMotion.current) 1f else 0f,
        tween(250), label = "wavy_seek_amplitude",
    )
    val phase = if (LocalReducedMotion.current || !playing || dragProgress >= 0f) 0f else {
        val transition = rememberInfiniteTransition(label = "wavy_seek_phase")
        val animated by transition.animateFloat(0f, (2f * PI).toFloat(),
            infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "wavy_seek_phase_value")
        animated
    }
    Box(modifier.fillMaxWidth().height(48.dp).testTag("wavy_seek_bar")
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            if (interactive) setProgress { value -> seekAction(value.coerceIn(0f, 1f)); true }
        }
        .then(if (!interactive) Modifier else Modifier
            .pointerInput(Unit) {
                detectTapGestures { seekAction((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragProgress >= 0f) seekAction(dragProgress)
                        dragProgress = -1f
                    },
                    onDragCancel = { dragProgress = -1f },
                ) { change, _ ->
                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val centerY = size.height / 2f
            val thumbX = size.width * shown
            val gap = 12.dp.toPx()
            val stroke = 4.dp.toPx()
            val waveLength = maxOf(90.dp.toPx(), size.width / 3f)
            val waveHeight = 5.dp.toPx() * amplitude
            val activeEnd = (thumbX - gap).coerceAtLeast(0f)
            val inactiveStart = (thumbX + gap).coerceAtMost(size.width)
            if (inactiveStart < size.width) {
                drawLine(inactiveColor, Offset(inactiveStart, centerY),
                    Offset(size.width, centerY), stroke, StrokeCap.Round)
            }
            if (activeEnd > 0f) {
                if (waveHeight < .5f) {
                    drawLine(activeColor, Offset(0f, centerY), Offset(activeEnd, centerY),
                        stroke, StrokeCap.Round)
                } else {
                    val path = Path().apply {
                        moveTo(0f, centerY)
                        var x = 0f
                        while (x < activeEnd + 2f) {
                            val taper = ((activeEnd - x) / (waveLength * .45f)).coerceIn(0f, 1f)
                            val y = centerY + sin(x / waveLength * (2f * PI).toFloat() - phase) * waveHeight * taper
                            lineTo(x, y)
                            x += 2f
                        }
                    }
                    clipRect(right = activeEnd, bottom = size.height) {
                        drawPath(path, activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
                    }
                }
            }
            drawCircle(activeColor, radius = 8.dp.toPx(), center = Offset(thumbX, centerY))
        }
    }
}
