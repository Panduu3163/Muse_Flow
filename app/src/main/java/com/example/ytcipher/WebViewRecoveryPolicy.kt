package com.example.ytcipher

/**
 * Pure, clock-injectable backoff policy for a WebView-backed pipeline (cipher deobfuscation,
 * PoToken generation) that can start failing repeatedly - e.g. the WebView's render process gets
 * reclaimed under memory pressure. After [maxConsecutiveFailures] failures in a row, opens a
 * [cooldownMs] window during which [shouldAttempt] returns false, so the caller can fail fast
 * instead of paying the full WebView-creation cost again immediately, only to fail again.
 * [onSuccess] resets the failure count. Mirrors zemer-cipher's `RendererRecoveryPolicy`
 * (GPL-3.0, https://github.com/ZemerTeam/zemer-cipher) shape.
 *
 * Each WebView-backed pipeline should hold its own instance - a cipher failure and a PoToken
 * failure are independent problems with independent cooldowns, so one failing repeatedly
 * shouldn't also block attempts at the other.
 */
class WebViewRecoveryPolicy(
    private val maxConsecutiveFailures: Int = 3,
    private val cooldownMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var consecutiveFailures = 0
    private var cooldownUntilMs = 0L

    @Synchronized
    fun shouldAttempt(): Boolean = clock() >= cooldownUntilMs

    @Synchronized
    fun onSuccess() {
        consecutiveFailures = 0
        cooldownUntilMs = 0L
    }

    @Synchronized
    fun onFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= maxConsecutiveFailures) {
            cooldownUntilMs = clock() + cooldownMs
            consecutiveFailures = 0
        }
    }
}
