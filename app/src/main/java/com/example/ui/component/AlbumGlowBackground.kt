package com.example.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.AlbumPalette
import com.example.ui.theme.LocalReducedMotion
import kotlin.math.sin

/** Artwork-coloured light rendered as three cheap radial gradients, clipped by the parent. */
@Composable
fun AlbumGlowBackground(
    palette: AlbumPalette,
    base: Color,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
) {
    val reducedMotion = LocalReducedMotion.current || LocalInspectionMode.current
    val dominant by animateColorAsState(palette.dominant, tween(if (reducedMotion) 0 else 900), label = "glow_dominant")
    val vibrant by animateColorAsState(palette.vibrant, tween(if (reducedMotion) 0 else 900), label = "glow_vibrant")
    val muted by animateColorAsState(palette.muted, tween(if (reducedMotion) 0 else 900), label = "glow_muted")
    val phase = if (reducedMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "album_glow")
        val animated by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (immersive) 13_000 else 24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "album_glow_phase",
        )
        animated
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val radius = if (immersive) maxOf(width * .82f, height * .52f)
            else maxOf(width * .68f, height * 2.3f)
        val colors = arrayOf(dominant, vibrant, muted)
        colors.forEachIndexed { index, color ->
            val angle = (phase + index / 3f) * (2f * Math.PI).toFloat()
            val center = Offset(
                width * (.5f + (if (immersive) .52f else .38f) * sin(angle)),
                height * (.5f + (if (immersive) .43f else .32f) * sin(angle + 1.2f)),
            )
            drawRect(Brush.radialGradient(
                colors = listOf(color.copy(alpha = if (immersive) .52f else .25f),
                    color.copy(alpha = if (immersive) .20f else .09f), Color.Transparent),
                center = center, radius = radius,
            ))
        }
    }
}
