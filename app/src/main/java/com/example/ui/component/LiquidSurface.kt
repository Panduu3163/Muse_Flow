package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Translucent layers retain readable contrast on every supported Android version.
 *
 * @param tint When set, replaces the neutral surface-container fill/border with a soft wash of
 * this colour instead - used to give otherwise-identical cards (e.g. Library's Liked/Downloads/Top
 * 50/On device tiles) their own colour identity without losing the shared glass look.
 */
@Composable
fun Modifier.liquidSurface(radius: Dp = 24.dp, tint: Color? = null): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    val (top, bottom, border) = if (tint != null) {
        Triple(tint.copy(alpha = .30f), tint.copy(alpha = .14f), tint.copy(alpha = .45f))
    } else {
        Triple(
            colors.surfaceContainerHigh.copy(alpha = .86f),
            colors.surfaceContainerLow.copy(alpha = .94f),
            colors.outlineVariant.copy(alpha = .55f),
        )
    }
    return clip(shape).background(Brush.linearGradient(listOf(top, bottom))).border(1.dp, border, shape)
}
