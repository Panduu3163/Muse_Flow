package com.example

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SettingsWriteFailureTest {
    @Test fun diskFailureReportsRetryFeedbackAndKeepsTheCallerAlive() = runTest {
        val messages = mutableListOf<String>()
        launchSettingsWrite(messages::add) { throw IOException("Disk full") }.join()
        assertEquals(listOf("Couldn't save settings. Check free storage and retry."), messages)
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun lifecycleCancellationIsNotMisreportedAsStorageFailure() = runTest {
        val entered = CompletableDeferred<Unit>(); val messages = mutableListOf<String>()
        val job = launchSettingsWrite(messages::add) { entered.complete(Unit); awaitCancellation() }
        entered.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled); assertTrue(messages.isEmpty())
    }
}
