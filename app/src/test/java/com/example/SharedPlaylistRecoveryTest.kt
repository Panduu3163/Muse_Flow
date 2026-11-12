package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.screens.ImportSharedPlaylistScreen
import com.example.ui.theme.MuseFlowTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class SharedPlaylistRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("New song", "Artist", "", "3:00", "", 0, sourceId = "abcdefghijk", sourceType = MusicSource.YOUTUBE_MUSIC)

    @Test fun editedInputCannotBeReplacedByAStaleNetworkPreview() {
        val release = CompletableDeferred<Unit>()
        compose.setContent { MuseFlowTheme {
            ImportSharedPlaylistScreen({}, fetchPlaylist = {
                withContext(NonCancellable) { release.await() }
                listOf(TrackResult("oldoldold12", "Old fetched song", "Artist", "3:00", "YouTube Music", MusicSource.YOUTUBE_MUSIC))
            }, savePlaylist = { _, _, _ -> })
        } }
        compose.onNodeWithTag("import_playlist_code_field").performTextInput("https://music.youtube.com/playlist?list=PLabcdefghijk")
        compose.onNodeWithTag("import_playlist_decode_button").performScrollTo().performClick()
        compose.onNodeWithTag("import_playlist_code_field").performScrollTo().performTextReplacement(PlaylistShareCodec.encode("New selection", listOf(track)))
        compose.onNodeWithTag("import_playlist_decode_button").performScrollTo().performClick()
        compose.runOnIdle { release.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("New selection").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Old fetched song").assertDoesNotExist()
    }

    @Test fun failedSaveReturnsToTheSamePreviewForExplicitRetry() {
        compose.setContent { MuseFlowTheme {
            ImportSharedPlaylistScreen({}, savePlaylist = { _, _, _ -> error("Disk full") })
        } }
        compose.onNodeWithTag("import_playlist_code_field").performTextInput(PlaylistShareCodec.encode("Mix", listOf(track)))
        compose.onNodeWithTag("import_playlist_decode_button").performScrollTo().performClick()
        compose.onNodeWithTag("import_playlist_confirm").performScrollTo().performClick()
        compose.onNodeWithText("Return to preview").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Mix").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("import_playlist_confirm").assertIsEnabled()
    }
}
