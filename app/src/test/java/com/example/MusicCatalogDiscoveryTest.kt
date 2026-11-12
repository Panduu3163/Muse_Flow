package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MusicCatalogDiscoveryTest {
    private fun track(id: String) = TrackResult(id, "Same title", "Artist", "3:00", "YouTube Music", MusicSource.YOUTUBE_MUSIC)

    @Test fun combinesMusicCatalogLanesAndKeepsDistinctAudioVideoRecordings() = runTest {
        val result = musicCatalogDiscovery({ listOf(track("audio")) }, { listOf(track("video"), track("audio")) })
        assertEquals(listOf("audio", "video"), result.map { it.id })
    }

    @Test fun failedLaneRetainsActualResultsFromTheOtherMusicLane() = runTest {
        val result = musicCatalogDiscovery({ throw IOException("songs unavailable") }, { listOf(track("video")) })
        assertEquals(listOf("video"), result.map { it.id })
    }

    @Test fun twoOutagesRemainAnErrorInsteadOfAFakeEmptySuccess() = runTest {
        try {
            musicCatalogDiscovery({ throw IOException("songs unavailable") }, { throw IOException("videos unavailable") })
            fail("Both failed requests must surface an error")
        } catch (expected: IOException) { assertEquals("songs unavailable", expected.message) }
    }

    @Test fun cancellationDoesNotPublishFallbackResults() = runTest {
        try {
            musicCatalogDiscovery({ throw CancellationException("obsolete query") }, { listOf(track("video")) })
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) { assertEquals("obsolete query", expected.message) }
    }

    @Test fun genuineEmptyCatalogResultsStayEmpty() = runTest {
        assertTrue(musicCatalogDiscovery({ emptyList() }, { emptyList() }).isEmpty())
    }
}
