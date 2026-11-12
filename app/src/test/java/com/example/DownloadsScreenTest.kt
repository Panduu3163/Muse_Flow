package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.screens.DownloadsContent
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
class DownloadsScreenTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetTextScale() { RuntimeEnvironment.setFontScale(1f) }
    private fun request(state: DownloadRequestState) = DownloadRequestEntity("yt:abcdefghijk", "একটি দীর্ঘ গানের নাম — Live recording", "Artist with a long name", "", "3:00", 0,
        null, "abcdefghijk", "YOUTUBE_MUSIC", null, null, state.name, 1, "attempt", error = "Connection lost. Retry when you are online.", updatedAt = 1)

    @Test fun retryAndExplicitRemovalWorkAtTwoHundredPercentText() {
        RuntimeEnvironment.setFontScale(2f)
        val request = request(DownloadRequestState.INTERRUPTED)
        var retries = 0; var removals = 0
        compose.setContent { MuseFlowTheme(darkTheme = true) { Surface {
            DownloadsContent(DownloadsUiState(false, listOf(request)), emptyMap(), emptySet(), null, {}, { retries++ }, {}, { removals++ }, {})
        } } }
        compose.onNodeWithText("Retry download").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, removals) }
        compose.onNodeWithText("Remove download").performScrollTo().performClick()
        compose.onNodeWithTag("download_remove_confirm").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-download-remove-large-text.png")
        compose.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(0, removals) }
        compose.onNodeWithText("Remove download").performClick()
        compose.onNodeWithTag("download_remove_confirm").performClick()
        compose.runOnIdle { assertEquals(1, removals) }
    }

    @Test fun savedButUncommittedAudioOffersRecoveryInsteadOfClaimingAvailableOffline() {
        val request = request(DownloadRequestState.VERIFYING).copy(error = "Audio is saved; retry to finish local recovery.")
        var recovered = 0
        compose.setContent { MuseFlowTheme(darkTheme = false) { Surface {
            DownloadsContent(DownloadsUiState(false, listOf(request)), emptyMap(), emptySet(), null, {}, { recovered++ }, {}, {}, {})
        } } }
        compose.onNodeWithText("Available offline").assertDoesNotExist()
        compose.onNodeWithText("Finish saving").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, recovered) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-download-local-recovery.png")
    }
}
