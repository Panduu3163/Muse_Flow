package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.component.ListPreference
import com.example.ui.component.TrackDetailsDialog
import com.example.ui.component.SleepTimerDialog
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class UtilityAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun longSettingsPickerCanReachAndSelectLastOptionAtLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        var selected = -1
        compose.setContent { MuseFlowTheme(darkTheme = true) { Surface {
            ListPreference("Long choice", selected = 0, options = (0..40).toList(),
                label = { "Choice $it with a long readable description" }, onSelect = { selected = it })
        } } }
        compose.onNodeWithText("Long choice").performClick()
        compose.onNodeWithTag("list_pref_option_choice_0_with_a_long_readable_description").assertIsSelected()
        compose.onNodeWithText("Choice 40 with a long readable description").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-settings-picker-large-text.png")
        compose.onNodeWithText("Choice 40 with a long readable description").performClick()
        compose.runOnIdle { assertEquals(40, selected) }
    }

    @Test fun longTrackDetailsReachIdentityAndCloseAtLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        var closed = false
        val track = TrackResult("content://media/external/audio/media/123456789", "একটি দীর্ঘ গানের নাম ".repeat(8),
            "An artist with a long name ".repeat(5), "3:45", "On device", MusicSource.LOCAL_DEVICE)
        compose.setContent { MuseFlowTheme(darkTheme = false) {
            TrackDetailsDialog(track, true, false) { closed = true }
        } }
        compose.onNodeWithText(track.id).performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-track-details-large-text.png")
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun activeSleepTimerKeepsCustomDurationAndStopReachableAtLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        var stopped = false
        compose.setContent { MuseFlowTheme(darkTheme = true) {
            SleepTimerDialog(600_000, {}, { stopped = true }, {})
        } }
        compose.onNodeWithText("Set a custom duration").performScrollTo().performClick()
        compose.onNodeWithText("Use 15-minute slider").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-sleep-timer-large-text.png")
        compose.onNodeWithText("Stop timer").performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }
}
