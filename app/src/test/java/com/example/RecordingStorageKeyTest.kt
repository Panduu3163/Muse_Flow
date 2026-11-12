package com.example

import org.junit.Assert.*
import org.junit.Test

class RecordingStorageKeyTest {
    private fun result(id: String) = TrackResult(id, "Same title", "Same artist", "3:00", "YouTube", MusicSource.YOUTUBE_MUSIC)

    @Test fun savedStatusBelongsToTheRecordingNotTheNames() {
        assertNotEquals(result("audio000001").downloadKey(), result("video000001").downloadKey())
        assertEquals(result("audio000001").downloadKey(), result("audio000001").copy(title = "Renamed", artist = "New credit").downloadKey())
        assertEquals(result("audio000001").downloadKey(), result("audio000001").toPlayableTrack(0).downloadKey())
    }
    @Test fun namespacesDoNotCollideAndUnknownLegacyIdentityStillMatches() {
        assertNotEquals(recordingStorageKey("T", "A", "id", "LOCAL_DEVICE"), recordingStorageKey("T", "A", "id", "YOUTUBE_MUSIC"))
        assertEquals("t::a", recordingStorageKey("T", "A", null, null))
        assertEquals("t::a", recordingStorageKey("T", "A", "null", null))
        assertEquals("t::a", recordingStorageKey("T", "A", "T|A", "YOUTUBE_MUSIC"))
    }
}
