package com.example.ui.utils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared damped press feedback with native click, long press and cancellation.
 * Actions run immediately; reduced motion leaves the content at its normal scale.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = feedbackClickable(scaleDown = scaleDown, enabled = enabled,
    rippleEnabled = false, onLongClick = onLongClick, onClick = onClick)

/**
 * A slow, readable auto-scroll for a long single-line title (Now Playing's track/artist, the
 * mini-player's title) - [basicMarquee]'s own default velocity (30dp/s) reads as too fast to
 * actually read the name as it goes by; this halves it. No-ops when the text already fits, same
 * as [basicMarquee] itself, so it's safe to apply unconditionally to a title that's sometimes
 * short and sometimes long.
 */
@OptIn(ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
fun Modifier.slowMarquee(): Modifier = if (com.example.ui.theme.LocalReducedMotion.current) {
    this
} else {
    this.basicMarquee(iterations = Int.MAX_VALUE, velocity = 15.dp)
}
