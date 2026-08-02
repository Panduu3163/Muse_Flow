package com.example.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlin.math.PI
import kotlin.math.sin

/**
 * A progress slider whose played portion is a travelling sine wave.
 *
 * The wave animates only while [playing], so a paused player shows a still line — motion is the
 * signal that audio is actually running, which is exactly the value of this style over a plain bar.
 *
 * Wave amplitude eases to zero while the user is scrubbing: a wobbling line under a moving finger
 * makes the exact seek position hard to judge, and flattening it turns the control into a precise
 * one for as long as it's being dragged.
 */
@Composable
fun SquigglySlider(
    progress: Float,
    onSeek: (Float) -> Unit,
    playing: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    /** Cycles visible across the full width - lower is a wider, gentler wave ("Wavy"), higher is
     * a tighter, busier one ("Squiggly"). Default matches "Wavy"'s own tuning, so anywhere this
     * is used as a bare visual reference (the style-picker's Wavy preview cell) stays in sync
     * with the real seek bar without repeating the numbers. */
    visibleCycles: Float = 1.2f,
    /** Full phase-cycle duration - lower travels faster. Default matches "Wavy"'s own tuning. */
    phaseDurationMs: Int = 2200,
    /** False for a purely visual preview (e.g. the style-picker's grid cells) - skips attaching
     * this slider's own tap/drag handling entirely, rather than relying on a wrapping clickable
     * to "win" against it. Compose dispatches gesture recognition child-first, so a real, nested
     * interactive slider inside a clickable card silently swallows any tap that lands on the wave
     * itself before the card's own onClick ever sees it - only taps landing on the card's other,
     * non-slider area (e.g. its label) would have reached the card. Disabling this slider's own
     * gestures outright, rather than fighting over dispatch order, is what actually fixes that. */
    interactive: Boolean = true,
    /** True only for "Squiggly" - a pill/stadium thumb matching Slim/Default's Material3 Slider
     * thumb shape. "Wavy" keeps the original plain circle. */
    pillThumb: Boolean = false,
) {
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val isDragging = dragProgress >= 0f
    val shown = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    val transition = rememberInfiniteTransition(label = "squiggly")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = phaseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_phase",
    )

    val amplitude by animateFloatAsState(
        targetValue = if (playing && !isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "wave_amplitude",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("squiggly_slider"),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(
                    if (interactive) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        if (dragProgress >= 0f) onSeek(dragProgress)
                                        dragProgress = -1f
                                    },
                                    onDragCancel = { dragProgress = -1f },
                                ) { change, _ ->
                                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            val centerY = size.height / 2f
            val activeWidth = size.width * shown
            val strokeWidth = 4.dp.toPx()
            val waveHeight = 7.dp.toPx() * amplitude
            // [visibleCycles] full sine cycles across the full width, regardless of screen size.
            val wavelength = size.width / (visibleCycles * 5.6f)

            // Remaining portion: always a flat line.
            drawLine(
                color = inactiveColor,
                start = Offset(activeWidth, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            // Played portion: sampled sine. Falls back to a straight line at zero amplitude so a
            // paused/scrubbing state doesn't pay for path building it won't show.
            if (activeWidth > 0f) {
                if (waveHeight < 0.5f) {
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeWidth, centerY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                } else {
                    val path = Path().apply {
                        moveTo(0f, centerY)
                        var x = 0f
                        while (x <= activeWidth) {
                            val y = centerY + sin(x / wavelength * 2f * PI.toFloat() - phase) * waveHeight
                            lineTo(x, y)
                            x += 2f // 2px steps: smooth enough to read, cheap enough for 60fps.
                        }
                    }
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            // Thumb: "Squiggly" gets a vertical pill/stadium bar matching Material3's own default
            // Slider thumb shape (what "Slim"/"Default" use); "Wavy" keeps its original plain
            // circle.
            if (pillThumb) {
                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 20.dp.toPx()
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(activeWidth - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f),
                )
            } else {
                drawCircle(
                    color = activeColor,
                    radius = 7.dp.toPx(),
                    center = Offset(activeWidth, centerY),
                )
            }
        }
    }
}
