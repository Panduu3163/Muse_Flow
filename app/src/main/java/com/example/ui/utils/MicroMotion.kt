package com.example.ui.utils

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MuseFlowMotion

/** Draw-only feedback. The caller's input/semantics node stays outside this layer. */
@Composable
fun Modifier.pressFeedback(source: MutableInteractionSource, enabled: Boolean = true, scaleDown: Float = .985f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        if (pressed && enabled && !reduced) scaleDown.coerceIn(.9f, 1f) else 1f,
        if (reduced) snap() else spring(dampingRatio = .9f, stiffness = 700f), label = "press_feedback",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** One native gesture recognizer, including long press, keyboard and cancellation. */
fun Modifier.feedbackClickable(
    scaleDown: Float = .985f,
    enabled: Boolean = true,
    role: Role? = null,
    rippleEnabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this.combinedClickable(enabled = enabled, role = role, interactionSource = source,
        indication = if (rippleEnabled) LocalIndication.current else null,
        onLongClick = onLongClick, onClick = onClick)
        .pressFeedback(source, enabled, scaleDown)
}

/** Transform content only; native button target, ripple, focus and disabled semantics remain. */
@Composable
fun MicroIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    IconButton(onClick, modifier, enabled, colors, interactionSource = source) {
        Box(Modifier.pressFeedback(source, enabled, .94f), contentAlignment = Alignment.Center) { content() }
    }
}

/** Keep only the current accessible label while the old glyph fades away. */
@Composable
fun MotionIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val reduced = LocalReducedMotion.current
    val color by animateColorAsState(tint, tween(if (reduced) 0 else MuseFlowMotion.quickMillis), label = "icon_tint")
    Crossfade(imageVector, modifier.size(24.dp).clearAndSetSemantics {
        contentDescription?.let { this.contentDescription = it }
    }, tween(if (reduced) 0 else MuseFlowMotion.quickMillis), label = "state_icon") {
        Icon(it, null, modifier = Modifier.fillMaxSize(), tint = color)
    }
}

/** Local value/count transition; semantic value updates immediately, not on animation end. */
@Composable
fun MotionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val reduced = LocalReducedMotion.current
    Crossfade(text, modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        tween(if (reduced) 0 else MuseFlowMotion.quickMillis), label = "value_label") {
        Text(it, color = color, style = style, maxLines = maxLines, overflow = overflow)
    }
}

/** Settle the new content tree only. Never retain an outgoing list, its focus or callbacks. */
fun Modifier.contentMotion(presentationKey: Any?): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(presentationKey) }
    LaunchedEffect(presentationKey, reduced) {
        val changed = previous != presentationKey
        previous = presentationKey
        if (!changed || reduced) progress.snapTo(1f) else {
            progress.snapTo(.92f)
            progress.animateTo(1f, tween(MuseFlowMotion.quickMillis))
        }
    }
    this.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value).coerceAtLeast(0f) * 64.dp.toPx()
    }
}
