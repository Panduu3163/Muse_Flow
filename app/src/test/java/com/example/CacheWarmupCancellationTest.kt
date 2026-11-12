package com.example

import org.junit.Assert.*
import org.junit.Test

class CacheWarmupCancellationTest {
    @Test fun changingTracksCancelsTheActualBlockingWriter() {
        val state = CacheWarmupCancellation(); var cancelled = 0
        val old = state.replace(); assertTrue(state.register(old) { cancelled++ })
        state.replace(); assertEquals(1, cancelled)
    }
    @Test fun lateOldWriterCannotReplaceTheNewWriter() {
        val state = CacheWarmupCancellation(); var oldCancelled = 0; var newCancelled = 0
        val old = state.replace(); val current = state.replace()
        state.register(current) { newCancelled++ }
        assertFalse(state.register(old) { oldCancelled++ })
        state.replace()
        assertEquals(1, oldCancelled); assertEquals(1, newCancelled)
    }
    @Test fun oldCompletionCannotDetachNewCancellationHandle() {
        val state = CacheWarmupCancellation(); var cancelled = 0
        val old = state.replace(); val current = state.replace()
        state.register(current) { cancelled++ }
        state.finished(old); state.replace()
        assertEquals(1, cancelled)
    }
}
