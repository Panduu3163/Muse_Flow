package com.example.ytcipher

/**
 * Pure, clock-injectable gate for "is it time to attempt another background refresh?" - extracted
 * out of [YtCipherConfigStore] so the cooldown/TTL decision is directly unit-testable. A refresh is
 * allowed at most once per [successTtlMs] since the last *successful* refresh, or, after a failed
 * attempt, once per [failedCooldownMs] - two separate timers so one failure mode (e.g. the remote
 * host being briefly down) can't starve the other's retry budget.
 */
class RefreshCooldownGate(
    private val successTtlMs: Long,
    private val failedCooldownMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    // Nullable rather than a 0L "never happened" sentinel compared via `now - sentinel < ttl` -
    // that comparison is only safe because a real wall-clock epoch is always huge relative to any
    // TTL in practice; it silently breaks for a clock that starts near zero (e.g. a test's fake
    // clock), so track "has this ever happened" explicitly instead of leaning on clock magnitude.
    @Volatile private var lastAttemptMs: Long? = null
    @Volatile private var lastSuccessMs: Long? = null

    /** If a refresh attempt is currently allowed, records it as started (so a concurrent/immediate
     * next call won't also be allowed) and returns true. Otherwise returns false with no side
     * effect. */
    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock()
        lastSuccessMs?.let { if (now - it < successTtlMs) return false }
        lastAttemptMs?.let { if (now - it < failedCooldownMs) return false }
        lastAttemptMs = now
        return true
    }

    /** Marks the most recently acquired attempt as having succeeded, resetting the success TTL
     * window from now. */
    @Synchronized
    fun onSuccess() {
        lastSuccessMs = clock()
    }
}
