package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class Release17RegressionTest {
    @Test fun batchContinuesAfterOneFailureAndReportsActualCompletion() = runTest {
        val processed = mutableListOf<Int>()
        val result = runTrackBatch(listOf(1, 2, 3)) { if (it == 2) error("Unavailable") else processed += it }
        assertEquals(listOf(1, 3), processed)
        assertEquals(BatchActionResult(2, 1), result)
        assertTrue(result.message("Updated").contains("2 of 3"))
    }

    @Test fun batchDoesNotSwallowCancellation() = runTest {
        var cancelled = false
        try { runTrackBatch(listOf(1, 2)) { throw CancellationException("Leave screen") } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun scrollingMemoryIsBoundedAndRejectsNegativePositions() {
        repeat(150) { ScrollPositionMemory.save("test:$it", it, 1) }
        assertNull(ScrollPositionMemory.restore("test:0"))
        assertEquals(149 to 1, ScrollPositionMemory.restore("test:149"))
        ScrollPositionMemory.save("negative", -1, -10)
        assertEquals(0 to 0, ScrollPositionMemory.restore("negative"))
    }
}
