package com.example

/** Cancel the blocking cache writer itself, not only the coroutine waiting for its IO to end. */
internal class CacheWarmupCancellation {
    private var generation = 0L
    private var cancelCurrent: (() -> Unit)? = null
    @Synchronized fun replace(): Long {
        cancelCurrent?.invoke()
        cancelCurrent = null
        return ++generation
    }
    @Synchronized fun register(token: Long, cancel: () -> Unit): Boolean {
        if (token != generation) { cancel(); return false }
        cancelCurrent = cancel
        return true
    }
    @Synchronized fun finished(token: Long) {
        if (token == generation) cancelCurrent = null
    }
}
