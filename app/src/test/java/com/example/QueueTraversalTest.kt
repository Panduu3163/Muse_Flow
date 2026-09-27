package com.example

import org.junit.Assert.*
import org.junit.Test

class QueueTraversalTest {
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
