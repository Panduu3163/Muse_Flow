package com.example

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w850dp-h360dp-land-xxhdpi")
class NowPlayingWindowTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetScale() { RuntimeEnvironment.setFontScale(1f) }
    private fun show(onPlay: () -> Unit) {
        compose.setContent { MuseFlowTheme(darkTheme = true) {
            NowPlayingScreen(
                state = NowPlayingState(title = "A long recording title", artist = "Singer", hasMedia = true, durationMs = 174000),
                onTogglePlayPause = onPlay, onNext = {}, onPrevious = {}, onSeek = {},
                onToggleShuffle = {}, onCycleRepeat = {}, onPlayQueueItem = {}, onMoveQueueItem = { _, _ -> },
                onRemoveQueueItem = {}, onUndoRemoveQueueItem = {}, onClearUpcomingQueue = {},
                onAddRadio = {}, onSaveQueueAsPlaylist = { true }, onCollapse = {},
                lyricsContent = { modifier -> LazyColumn(modifier.testTag("test_lyrics")) {
                    items((1..100).toList()) { Text("Line $it") }
                } },
            )
        } }
    }
    private fun checkTransport(image: String) {
        var played = 0
        show { played++ }
        compose.onNodeWithTag("now_playing_play_pause").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, played) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$image.png")
        compose.onNodeWithContentDescription("Show queue").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("player_queue_screen").assertIsDisplayed()
    }
    @Test fun landscapeKeepsTransportAndQueueReachable() { checkTransport("62-landscape-player") }
    @Test @Config(qualifiers = "w360dp-h800dp-port-xxhdpi")
    fun largeTextKeepsTransportAndQueueReachable() {
        RuntimeEnvironment.setFontScale(2f)
        checkTransport("62-large-text-player")
    }
    @Test fun landscapeLyricsRemainBoundedAndTransportReachable() {
        show {}
        compose.onNodeWithContentDescription("Show lyrics").performScrollTo().performClick()
        compose.onNodeWithTag("test_lyrics").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("now_playing_play_pause").performScrollTo().assertIsDisplayed()
    }
}
