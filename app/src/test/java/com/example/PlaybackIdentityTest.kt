package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.ui.screens.asTrackResult

class PlaybackIdentityTest {
    private fun result(id: String) = TrackResult(
        id, "Same song", "Same artist", "3:00", "YouTube Music", MusicSource.YOUTUBE_MUSIC,
    )

    @Test fun differentRecordingsWithSameNamesDoNotSharePlayingIdentity() {
        val first = result("abcdefghij1")
        val second = result("abcdefghij2")
        assertNotEquals(first.downloadKey(), second.downloadKey()) // Saved status also belongs to the exact recording.
        assertNotEquals(first.playbackId(), second.playbackId())
        val state = NowPlayingState(isPlaying = false, queue = listOf(
            QueueItem(0, first.id, first.title, first.artist, null, isCurrent = true),
            QueueItem(1, second.id, second.title, second.artist, null, isCurrent = false),
        ))
        assertEquals(first.playbackId(), state.currentPlaybackId)
        assertNotEquals(second.playbackId(), state.currentPlaybackId)
    }

    @Test fun savedTrackAndSearchResultIdentifyTheSameRecording() {
        val source = result("abcdefghij1")
        val stored = source.toPlayableTrack(0).copy(streamUrl = "/downloads/saved.audio")
        assertEquals(source.playbackId(), stored.playbackId())
        assertEquals(stored.playbackId(), stored.asTrackResult().id)
    }

    @Test fun providerIdCaseIsPreserved() {
        assertNotEquals(result("Abcdefghij1").playbackId(), result("abcdefghij1").playbackId())
    }

    @Test fun deviceFilesWithSameTagsHaveDifferentIdentity() {
        val first = result("content://media/external/audio/media/1").copy(sourceType = MusicSource.LOCAL_DEVICE)
        val second = first.copy(id = "content://media/external/audio/media/2")
        assertNotEquals(first.playbackId(), second.playbackId())
    }

    @Test fun noCurrentQueueItemHasNoPlayingIdentity() {
        assertNull(NowPlayingState(title = "Same song", artist = "Same artist").currentPlaybackId)
    }

    @Test fun cachedAlternateVersionCannotShadowTheChosenRecording() {
        assertFalse(canReuseDownloadForPlayback("abcdefghij2", null, "abcdefghij1", "/saved.audio"))
        assertTrue(canReuseDownloadForPlayback("abcdefghij1", null, "abcdefghij1", "/saved.audio"))
    }

    @Test fun unknownLegacyDownloadIsNotAssumedToMatchARealSearchId() {
        assertFalse(canReuseDownloadForPlayback("abcdefghij1", null, null, "/saved.audio"))
    }

    @Test fun explicitlySelectedLegacyDownloadStillPlaysItsOwnFile() {
        assertTrue(canReuseDownloadForPlayback("Same song|Same artist", "/saved.audio", null, "/saved.audio"))
        assertTrue(canReuseDownloadForPlayback("Same song|Same artist", "file:///saved.audio", null, "/saved.audio"))
        assertFalse(canReuseDownloadForPlayback("Same song|Same artist", "/other.audio", null, "/saved.audio"))
    }
}
