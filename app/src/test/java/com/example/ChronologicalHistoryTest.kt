package com.example

import org.junit.Assert.*
import org.junit.Test

class ChronologicalHistoryTest {
    @Test fun eachRecordedPlayAppearsAsAnEntryAndLegacyRowIsKept() {
        val current = PlaybackHistoryEntity("current", "A", "Artist", "", "3:00", 0, null, null, 2000, playCount = 3)
        val old = current.copy(key = "old", title = "Old", playedAt = 500, playCount = 4)
        val days = chronologicalHistory(listOf(current, old), listOf(
            PlaybackEventEntity(trackKey = "current", playedAt = 2000),
            PlaybackEventEntity(trackKey = "current", playedAt = 1000),
        ))
        val entries = days.flatMap { it.entries }
        assertEquals(3, entries.size)
        assertEquals(1, entries[0].playCount)
        assertEquals(1, entries[1].playCount)
        assertEquals(4, entries[2].playCount)
    }
}
