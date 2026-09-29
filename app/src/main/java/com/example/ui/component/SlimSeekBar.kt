package com.example.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

/** Thin visual track with a full 48dp touch target and accessible seek action. */
@Composable
fun SlimSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragProgress >= 0f) dragProgress else progress.coerceIn(0f, 1f)
    Canvas(
        modifier.fillMaxWidth().height(48.dp).testTag("slim_seek_bar")
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                if (interactive) setProgress { target -> onSeek(target.coerceIn(0f, 1f)); true }
            }
            .then(if (!interactive) Modifier else Modifier
                .pointerInput(Unit) {
                    detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
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
                })
    ) {
        val y = size.height / 2f
        drawLine(inactiveColor, Offset(0f, y), Offset(size.width, y),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(activeColor, Offset(0f, y), Offset(size.width * shown, y),
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        if (dragProgress >= 0f) {
            drawCircle(activeColor, radius = 5.dp.toPx(), center = Offset(size.width * shown, y))
        }
    }
}
