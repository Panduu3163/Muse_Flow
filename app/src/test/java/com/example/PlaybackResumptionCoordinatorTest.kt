package com.example

import androidx.media3.common.MediaItem
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackResumptionCoordinatorTest {
    private fun queue(id: String) = PlaybackResumeSnapshot(listOf(MediaItem.Builder().setMediaId(id).build()), 0, 12_000)

    @Test fun startupAndConcurrentUserRequestsShareOnePausedRestore() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0; var current: PlaybackResumeSnapshot? = null
        val coordinator = PlaybackResumptionCoordinator(backgroundScope, { calls++; release.await(); current = queue("saved") }, { 0 }, { current })
        coordinator.start(); coordinator.start()
        val first = async { coordinator.snapshot() }; val second = async { coordinator.snapshot() }
        runCurrent()
        assertEquals(1, calls); assertFalse(first.isCompleted); assertFalse(second.isCompleted)
        release.complete(Unit)
        assertEquals("saved", first.await()!!.items.single().mediaId)
        assertEquals(first.await(), second.await()); assertEquals(1, calls)
    }

    @Test fun aNewSelectionMakesTheWaitingResumptionRequestStale() = runTest {
        val release = CompletableDeferred<Unit>(); var generation = 0L
        var current: PlaybackResumeSnapshot? = null
        val coordinator = PlaybackResumptionCoordinator(backgroundScope, {
            val captured = generation; release.await()
            if (captured == generation) current = queue("old")
        }, { generation }, { current })
        val pending = async { runCatching { coordinator.snapshot() } }
        runCurrent(); generation++; current = queue("new"); release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is IllegalStateException)
        assertEquals("new", current.items.single().mediaId)
    }

    @Test fun cancellingOneRequestDoesNotCancelSharedPausedPreparation() = runTest {
        val release = CompletableDeferred<Unit>(); var current: PlaybackResumeSnapshot? = null
        val coordinator = PlaybackResumptionCoordinator(backgroundScope, { release.await(); current = queue("saved") }, { 0 }, { current })
        coordinator.start()
        val cancelled = async { coordinator.snapshot() }; runCurrent(); cancelled.cancelAndJoin()
        release.complete(Unit)
        assertEquals("saved", coordinator.snapshot()!!.items.single().mediaId)
    }

    @Test fun disabledOrEmptyRememberedQueueDoesNotInventAnItem() = runTest {
        val coordinator = PlaybackResumptionCoordinator(backgroundScope, {}, { 0 }, { null })
        coordinator.start()
        assertNull(coordinator.snapshot())
    }

    @Test fun failedPreparationSurfacesAnErrorInsteadOfReturningOldQueueData() = runTest {
        val coordinator = PlaybackResumptionCoordinator(backgroundScope, { throw java.io.IOException("unavailable storage") }, { 0 }, { queue("must not be returned") })
        try { coordinator.snapshot(); fail("Must report failed preparation") } catch (_: java.io.IOException) { }
    }

    @Test fun stoppingTheSessionInvalidatesEvenAnAlreadyPreparedSnapshot() = runTest {
        val coordinator = PlaybackResumptionCoordinator(backgroundScope, {}, { 0 }, { queue("saved") })
        assertNotNull(coordinator.snapshot())
        coordinator.invalidate()
        try { coordinator.snapshot(); fail("Stopped session cannot resume") } catch (_: IllegalStateException) { }
    }
}
