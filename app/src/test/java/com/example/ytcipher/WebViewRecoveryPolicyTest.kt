package com.example.ytcipher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewRecoveryPolicyTest {

    private class FakeClock(var nowMs: Long = 0L) {
        val fn: () -> Long = { nowMs }
    }

    @Test
    fun `allows attempts before any failure`() {
        val policy = WebViewRecoveryPolicy(maxConsecutiveFailures = 3, cooldownMs = 1000)
        assertTrue(policy.shouldAttempt())
    }

    @Test
    fun `allows attempts under the failure threshold`() {
        val policy = WebViewRecoveryPolicy(maxConsecutiveFailures = 3, cooldownMs = 1000)
        policy.onFailure()
        policy.onFailure()
        assertTrue(policy.shouldAttempt())
    }

    @Test
    fun `opens a cooldown once the failure threshold is hit`() {
        val clock = FakeClock(nowMs = 0L)
        val policy = WebViewRecoveryPolicy(maxConsecutiveFailures = 3, cooldownMs = 1000, clock = clock.fn)
        repeat(3) { policy.onFailure() }
        assertFalse(policy.shouldAttempt())
    }

    @Test
    fun `allows attempts again once the cooldown elapses`() {
        val clock = FakeClock(nowMs = 0L)
        val policy = WebViewRecoveryPolicy(maxConsecutiveFailures = 3, cooldownMs = 1000, clock = clock.fn)
        repeat(3) { policy.onFailure() }
        assertFalse(policy.shouldAttempt())

        clock.nowMs = 999
        assertFalse(policy.shouldAttempt())

        clock.nowMs = 1000
        assertTrue(policy.shouldAttempt())
    }

    @Test
    fun `a success resets the consecutive failure count`() {
        val policy = WebViewRecoveryPolicy(maxConsecutiveFailures = 3, cooldownMs = 1000)
        policy.onFailure()
        policy.onFailure()
        policy.onSuccess()
        policy.onFailure()
        policy.onFailure()
        // Only 2 consecutive failures since the reset - still under the threshold of 3.
        assertTrue(policy.shouldAttempt())
    }
}
