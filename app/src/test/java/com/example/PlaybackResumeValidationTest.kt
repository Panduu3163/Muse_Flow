package com.example

import androidx.media3.common.MediaItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class PlaybackResumeValidationTest {
    private fun snapshot(id: String, uri: String) = PlaybackResumeSnapshot(
        listOf(MediaItem.Builder().setMediaId(id).setUri(uri).build()), 0, 13_000)

    @Test fun readableLocalFileKeepsPositionAndSource() = runTest {
        val saved = snapshot("recording", "file:///music/local.opus")
        assertEquals(saved, validatedResumeSnapshot(saved) { true })
    }
    @Test fun missingDownloadReResolvesOnlyTheSameOnlineRecording() = runTest {
        val result = validatedResumeSnapshot(snapshot("dQw4w9WgXcQ", "file:///missing.opus")) { false }
        assertEquals(youTubeResolvePlaceholderUri("dQw4w9WgXcQ"), result.items.single().localConfiguration!!.uri)
        assertEquals(13_000, result.positionMs)
    }
    @Test fun revokedLocalContentGrantRequiresReselection() = runTest {
        val result = runCatching { validatedResumeSnapshot(snapshot("content://media/audio/7", "content://media/audio/7")) { false } }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("choose it again"))
    }
    @Test fun remotePlaceholderDoesNotProbeLocalStorage() = runTest {
        var probes = 0
        val saved = snapshot("dQw4w9WgXcQ", youTubeResolvePlaceholderUri("dQw4w9WgXcQ").toString())
        assertEquals(saved, validatedResumeSnapshot(saved) { probes++; false })
        assertEquals(0, probes)
    }
}
