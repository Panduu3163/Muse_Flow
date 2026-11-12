package com.example

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RestoredShuffleOrderTest {
    @Test fun permutationSurvivesRestart() {
        assertArrayEquals(intArrayOf(2, 0, 3, 1), restoredShuffleOrder(listOf(2, 0, 3, 1), 4))
    }
    @Test fun cappedQueuePreservesRelativeShuffledTraversal() {
        assertArrayEquals(intArrayOf(2, 0, 1), restoredShuffleOrder(listOf(4, 2, 0, 3, 1), 5, 2, 3))
    }
    @Test fun malformedOldOrdersCannotDuplicateOrLoseSongs() {
        listOf(emptyList(), listOf(1, 1, 2), listOf(-1, 1, 2), listOf(0, 1, 3)).forEach {
            assertArrayEquals(intArrayOf(0, 1, 2), restoredShuffleOrder(it, 3))
        }
    }
}
