package com.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bounded headless-engine cache. Owners serialize actual resource access/closing with their mutex.
 * A lease includes initialization and requests, so idle cleanup never interrupts active work. */
internal class IdleResource<T>(
    private val scope: CoroutineScope,
    private val idleMs: Long = 5 * 60_000L,
    private val onIdle: suspend (IdleResource<T>) -> Unit,
) {
    @Volatile var value: T? = null
    private var leases = 0
    private var idleJob: Job? = null

    @Synchronized fun acquire() {
        leases++
        idleJob?.cancel()
        idleJob = null
    }
    @Synchronized fun release() {
        check(leases > 0)
        leases--
        if (leases == 0) idleJob = scope.launch { delay(idleMs); onIdle(this@IdleResource) }
    }
    @Synchronized fun isUnused(): Boolean = leases == 0
}
