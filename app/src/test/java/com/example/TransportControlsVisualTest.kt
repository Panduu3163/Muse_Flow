package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.screens.TransportControls
import com.example.ui.theme.LocalReducedMotion
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class TransportControlsVisualTest {
    @get:Rule val compose = createComposeRule()
    private fun verify(style: PlayerTransportStyle) {
        var clicks = 0
        compose.setContent { CompositionLocalProvider(LocalReducedMotion provides true) {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF1535A0), onPrimary = Color.White,
                surface = Color.Black, onSurface = Color.White)) {
                Surface { TransportControls(NowPlayingState(isPlaying = true, hasNext = true, hasPrevious = true),
                    Color(0xFF1535A0), style, { clicks++ }, {}, {}) }
            }
        } }
        val button = compose.onNodeWithTag("now_playing_play_pause")
        val bitmap = button.captureToImage(); val pixels = bitmap.toPixelMap()
        val ink = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = pixels[x,y]
            if (p.alpha > .9f && p.red > .94f && p.green > .94f && p.blue > .94f) ink += x to y
        }
        assertTrue("Pause glyph must render", ink.isNotEmpty())
        val left = ink.minOf { it.first }; val right = ink.maxOf { it.first }
        val top = ink.minOf { it.second }; val bottom = ink.maxOf { it.second }
        assertTrue("Glyph horizontally centered", abs((left + right + 1) / 2f - bitmap.width / 2f) <= 2f)
        assertTrue("Glyph vertically centered", abs((top + bottom + 1) / 2f - bitmap.height / 2f) <= 2f)
        assertTrue("Glyph must use the requested size, not default 24dp", bottom-top+1 > bitmap.height * .29f)
        button.performTouchInput { click() }; compose.runOnIdle { assertEquals(1, clicks) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transport-${style.name.lowercase()}-centered.png")
    }
    @Test fun staticGlyphIsCenteredAndProperlySized() = verify(PlayerTransportStyle.Static)
    @Test fun wheelGlyphIsCenteredAndProperlySized() = verify(PlayerTransportStyle.Wheel)
    @Test fun pillGlyphIsCenteredAndProperlySized() = verify(PlayerTransportStyle.Pill)
    @Test fun animatedWheelKeepsTheSameGlyphFrame() {
        val playing = mutableStateOf(false)
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF1535A0),
            onPrimary = Color.White, surface = Color.Black, onSurface = Color.White)) {
            Surface { TransportControls(NowPlayingState(isPlaying = playing.value, hasNext = true, hasPrevious = true),
                Color(0xFF1535A0), PlayerTransportStyle.Wheel, {}, {}, {}) }
        } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { playing.value = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/transport-wheel-animated.png")
    }
}
