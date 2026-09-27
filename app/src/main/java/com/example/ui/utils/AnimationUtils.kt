package com.example.ui.utils

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A click modifier that scales the component down while pressed, for a tactile "bounce" - the
 * same [spring] parameters (`dampingRatio = 0.6f, stiffness = 500f`) Echo-Music uses for its own
 * transport-button press states, generalized into a reusable modifier since MuseFlow applies this
 * broadly (track rows, cards, the mini-player) rather than per-button.
 *
 * Driven by [collectIsPressedAsState] on the same [MutableInteractionSource] passed to
 * [combinedClickable] - not a second, independent `pointerInput` gesture recognizer racing it for
 * the same touch stream (an earlier version of this modifier did that; it worked but meant two
 * separate gesture detectors were both watching every touch, redundant with what
 * [combinedClickable] already tracks internally).
 *
 * @param scaleDown The scale factor applied while pressed (e.g., 0.95f = 95% of original size).
 * @param enabled Controls the enabled state. When false, onClick is not dispatched and bounce is disabled.
 * @param onLongClick The callback to be invoked when the component is long clicked.
 * @param onClick The callback to be invoked when the component is clicked.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.6f),
        label = "bounceScale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null, // Disable default ripple so bounce handles visual feedback
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * A slow, readable auto-scroll for a long single-line title (Now Playing's track/artist, the
 * mini-player's title) - [basicMarquee]'s own default velocity (30dp/s) reads as too fast to
 * actually read the name as it goes by; this halves it. No-ops when the text already fits, same
 * as [basicMarquee] itself, so it's safe to apply unconditionally to a title that's sometimes
 * short and sometimes long.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.slowMarquee(): Modifier = this.basicMarquee(
    iterations = Int.MAX_VALUE,
    velocity = 15.dp,
)
