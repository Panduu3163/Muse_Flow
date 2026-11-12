package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.component.*
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class MicroMotionVisualTest {
    @get:Rule val compose = createComposeRule()
    private fun preview(dark: Boolean, reduced: Boolean, large: Boolean) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalReducedMotion provides reduced,
                LocalDensity provides Density(density.density, if (large) 1.6f else 1f),
                LocalPlaybackActive provides false) {
                MuseFlowTheme(darkTheme = dark) { Surface { Column(Modifier.width(360.dp)) {
                    TrackRow("Midnight Drive", "Artist", null, "3:42", {}, isPlaying = true, onOpenMenu = {})
                    TrackRow("Selected recording", "Another artist", null, "4:10", {}, selected = true, onOpenMenu = {})
                    TrackRow("Downloaded recording", "Artist", null, "2:58", {}, isDownloaded = true, onOpenMenu = {})
                    TrackRow("Downloading recording", "Artist", null, "5:02", {}, downloadProgress = 38, onOpenMenu = {})
                    SwitchPreference("Album artwork colors", "Use the playing recording's palette", true, {})
                    FeedbackMessage("Playback could not start. Try again when connected.", isError = true, onRetry = {})
                } } }
            }
        }
        compose.onNodeWithContentDescription("Downloaded").assertExists()
        compose.onNodeWithText("Retry").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/micro-motion-${if (dark) "dark" else "light"}-${if (large) "large-reduced" else "normal"}.png")
    }
    @Test fun darkCommonComponents() = preview(true, false, false)
    @Test fun lightLargeTextReducedMotion() = preview(false, true, true)
}
