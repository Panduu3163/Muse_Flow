package com.example

import org.junit.Assert.*
import org.junit.Test

class SearchPlaybackPolicyTest {
    private val results = (1..3).map {
        TrackResult("abcdefghij$it", "Same song", "Same artist", "3:00", "YouTube Music", MusicSource.YOUTUBE_MUSIC)
    }

    @Test fun onlineSearchStartsOnlyTheChosenRecording() {
        val origin = playbackQueueOrigin(MusicSource.YOUTUBE_MUSIC, online = true, fromSearch = true)
        assertEquals(QueueOrigin.SEARCH_RADIO, origin)
        assertEquals(listOf(results[1]), initialPlaybackQueue(results[1], results, origin))
        assertEquals(3, results.size)
    }

    @Test fun onlineCollectionStillRetainsItsSourceOrder() {
        val origin = playbackQueueOrigin(MusicSource.YOUTUBE_MUSIC, online = true, fromSearch = false)
        assertEquals(QueueOrigin.ONLINE, origin)
        assertEquals(results, initialPlaybackQueue(results[1], results, origin))
    }

    @Test fun offlineSearchKeepsDownloadedQueuePolicy() {
        val origin = playbackQueueOrigin(MusicSource.YOUTUBE_MUSIC, online = false, fromSearch = true)
        assertEquals(QueueOrigin.OFFLINE_DOWNLOADS, origin)
        assertEquals(results, initialPlaybackQueue(results[1], results, origin))
    }

    @Test fun onDeviceSearchRemainsDeviceOnlyInEitherConnectionState() {
        for (online in listOf(true, false)) {
            assertEquals(QueueOrigin.ON_DEVICE,
                playbackQueueOrigin(MusicSource.LOCAL_DEVICE, online, fromSearch = true))
        }
    }

    @Test fun searchRadioNeverClonesTheSeedToPretendItHasFiftyRecommendations() {
        assertFalse(canRecycleQueueToFill(QueueOrigin.SEARCH_RADIO.name))
        assertTrue(canRecycleQueueToFill(QueueOrigin.ONLINE.name))
        assertTrue(canRecycleQueueToFill(null)) // Existing saved queues retain their prior policy.
    }

    @Test fun emptyCollectionStillContainsTheChosenSong() {
        assertEquals(listOf(results[1]), initialPlaybackQueue(results[1], emptyList(), QueueOrigin.ONLINE))
    }
}
