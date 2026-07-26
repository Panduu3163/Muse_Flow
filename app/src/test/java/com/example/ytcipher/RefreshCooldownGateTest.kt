package com.example.ytcipher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshCooldownGateTest {

    private class FakeClock(var nowMs: Long = 0L) {
        val fn: () -> Long = { nowMs }
    }

    @Test
    fun `allows the first attempt`() {
        val gate = RefreshCooldownGate(successTtlMs = 1000, failedCooldownMs = 100)
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `a second immediate attempt is blocked by the failed-attempt cooldown`() {
        val clock = FakeClock(nowMs = 0L)
        val gate = RefreshCooldownGate(successTtlMs = 1000, failedCooldownMs = 100, clock = clock.fn)
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }

    @Test
    fun `an attempt is allowed again once the failed cooldown elapses`() {
        val clock = FakeClock(nowMs = 0L)
        val gate = RefreshCooldownGate(successTtlMs = 1000, failedCooldownMs = 100, clock = clock.fn)
        assertTrue(gate.tryAcquire())

        clock.nowMs = 99
        assertFalse(gate.tryAcquire())

        clock.nowMs = 100
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `a success blocks further attempts until the success ttl elapses, even past the failed cooldown`() {
        val clock = FakeClock(nowMs = 0L)
        val gate = RefreshCooldownGate(successTtlMs = 1000, failedCooldownMs = 100, clock = clock.fn)
        assertTrue(gate.tryAcquire())
        gate.onSuccess()

        // Well past the short failed-cooldown, but still inside the longer success TTL.
        clock.nowMs = 500
        assertFalse(gate.tryAcquire())

        clock.nowMs = 1000
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `two independent gates do not share state`() {
        val gateA = RefreshCooldownGate(successTtlMs = 1000, failedCooldownMs = 100)
        val gateB = RefreshCooldownGate(successTtlMs = 1000, failedCooldownMs = 100)
        assertTrue(gateA.tryAcquire())
        // gateA being exhausted must not affect gateB.
        assertTrue(gateB.tryAcquire())
    }
}
