package com.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryWorkPolicyTest {
    @Test fun fadesRemainAccurateWithLongSleepsOnlyBetweenFadeWindows() {
        assertEquals(0f, playbackFadeStep(0, 180_000, 5000, 1f).volume, .0001f)
        assertEquals(.5f, playbackFadeStep(2500, 180_000, 5000, 1f).volume, .0001f)
        assertEquals(1f, playbackFadeStep(90_000, 180_000, 5000, 1f).volume, .0001f)
        assertEquals(.5f, playbackFadeStep(177_500, 180_000, 5000, 1f).volume, .0001f)
        assertEquals(50L, playbackFadeStep(2500, 180_000, 5000, 1f).nextUpdateMs)
        assertEquals(5000L, playbackFadeStep(165_000, 180_000, 5000, 2f).nextUpdateMs)
        assertEquals(1f, playbackFadeStep(0, -1, 5000, 1f).volume, .0001f)
    }

    @Test fun downloadedAndDeviceAudioDoNotRequestANetworkWakeLock() {
        assertFalse(playbackNeedsNetworkWake("file"))
        assertFalse(playbackNeedsNetworkWake("content"))
        assertFalse(playbackNeedsNetworkWake("asset"))
        assertTrue(playbackNeedsNetworkWake("https"))
    }

    @Test fun idleEngineCleanupWaitsForAllRequestsAndCancelsWhenReused() = runTest {
        var released = 0
        val holder = IdleResource<String>(backgroundScope, 300_000) { resource ->
            if (resource.isUnused()) { released++; resource.value = null }
        }
        holder.value = "cached engine"
        holder.acquire(); holder.acquire(); holder.release()
        advanceTimeBy(400_000); runCurrent()
        assertEquals(0, released)
        holder.release(); runCurrent()
        advanceTimeBy(299_999)
        holder.acquire()
        advanceTimeBy(300_000); runCurrent()
        assertEquals(0, released)
        holder.release(); runCurrent()
        advanceTimeBy(300_000); runCurrent()
        assertEquals(1, released)
        assertNull(holder.value)
    }

    @Test fun timerExpiresWithoutPollingWhenNoUiObservesIt() = runTest {
        var clockReads = 0
        var expired = 0
        val timer = SleepTimerEngine(backgroundScope, { clockReads++; testScheduler.currentTime }) { it() }
        timer.start(60_000) { expired++ }
        runCurrent()
        val initialReads = clockReads
        advanceTimeBy(59_999); runCurrent()
        assertEquals(initialReads, clockReads)
        assertEquals(0, expired)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, expired)
        assertNull(timer.remainingMs.value)
    }

    @Test fun timerUiCatchesUpAfterHiddenCountdownAndOldTimersNeverFire() = runTest {
        var oldExpired = 0
        var newExpired = 0
        val timer = SleepTimerEngine(backgroundScope, { testScheduler.currentTime }) { it() }
        timer.start(10_000) { oldExpired++ }
        runCurrent()
        advanceTimeBy(3000)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { timer.remainingMs.collect {} }
        runCurrent()
        assertEquals(7000L, timer.remainingMs.value)
        observer.cancel(); runCurrent()
        timer.start(1000) { newExpired++ }; runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(0, oldExpired)
        assertEquals(1, newExpired)
        timer.start(1000) { newExpired++ }; timer.cancel()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, newExpired)
    }
    @Test fun expiredDeadlineIsRecheckedAfterDeviceSleepBeforePlaybackContinues() = runTest {
        var elapsed = 0L
        var expired = 0
        val timer = SleepTimerEngine(backgroundScope, { elapsed }) { it() }
        timer.start(60_000) { expired++ }; runCurrent()
        // Android elapsedRealtime advances in deep sleep while coroutine uptime delays can wait.
        elapsed = 120_000
        timer.recheck(); runCurrent()
        assertEquals(1, expired)
        assertNull(timer.remainingMs.value)
    }

}
