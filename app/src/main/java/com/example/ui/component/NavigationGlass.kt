package com.example.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** One bounded source-backed blur pass; no bitmap screenshots, polling, or idle animation. */
@Composable
internal fun Modifier.navigationGlass(hazeState: HazeState?): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val surface = colors.surfaceContainer
    val style = remember(surface) { HazeBlurStyle {
        blurRadius(24.dp)
        backgroundColor(surface)
        colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = .70f))))
        noiseFactor(.012f)
        fallbackColorEffect(HazeColorEffect.tint(surface.copy(alpha = .96f)))
    } }
    val backdrop = if (hazeState != null && Build.VERSION.SDK_INT >= 31) {
        Modifier.hazeBlur(HazeInput.Sources(hazeState, retention = HazeSourceRetention.ClearWhenUnavailable), style)
    } else Modifier.background(surface.copy(alpha = .96f))
    return shadow(10.dp, shape, clip = false).clip(shape).then(backdrop)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .065f), Color.Transparent)))
        .border(.75.dp, Brush.verticalGradient(listOf(colors.onSurface.copy(alpha = .16f), colors.outlineVariant.copy(alpha = .18f))), shape)
}
