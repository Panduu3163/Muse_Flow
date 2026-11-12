package com.example

import org.junit.Assert.*
import org.junit.Test

class QueueTraversalTest {
    @Test fun upNextFollowsShuffleOrderInsteadOfTimelineOrder() {
        val shuffled = mapOf(2 to 0, 0 to 3, 3 to 1, 1 to -1)
        assertEquals(listOf(0, 3, 1), upcomingQueueIndices(2, 4) { shuffled[it] ?: -1 })
    }
    @Test fun upNextIncludesOneRepeatAllWrapThenStopsBeforeCurrentSong() {
        assertEquals(listOf(1, 2), upcomingQueueIndices(0, 3) { (it + 1) % 3 })
        assertEquals(listOf(0, 1), upcomingQueueIndices(2, 3) { (it + 1) % 3 })
    }
    @Test fun skipsUnavailableItemsInShuffleOrder() {
        val order = mapOf(0 to 3, 3 to 1, 1 to 2, 2 to -1)
        assertEquals(1, nextRecoverableIndex(0, { order[it] ?: -1 }, { it == 1 || it == 2 }))
    }
    @Test fun wrapsRepeatAllToAnEarlierDownload() {
        assertEquals(0, nextRecoverableIndex(2, { (it + 1) % 3 }, { it == 0 }))
    }
    @Test fun stopsWhenEveryItemIsUnavailable() {
        var visits = 0
        assertNull(nextRecoverableIndex(0, { (it + 1) % 4 }, { visits++; false }))
        assertEquals(3, visits)
    }
    @Test fun neverRetriesTheFailedItemInRepeatOne() {
        assertNull(nextRecoverableIndex(0, { 0 }, { true }))
    }
    @Test fun stopsAtEndWhenRepeatIsOff() {
        assertNull(nextRecoverableIndex(2, { -1 }, { true }))
    }
}
