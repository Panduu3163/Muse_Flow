package com.example.ytcipher.potoken

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoTokenSessionNeedsRecreateTest {

    private fun needsRecreate(
        forceRecreate: Boolean = false,
        hasWebView: Boolean = true,
        isExpired: Boolean = false,
        isDead: Boolean = false,
        currentSessionId: String? = "session-a",
        requestedSessionId: String = "session-a"
    ) = poTokenSessionNeedsRecreate(forceRecreate, hasWebView, isExpired, isDead, currentSessionId, requestedSessionId)

    @Test
    fun `a live webview for the same session is reused`() {
        assertFalse(needsRecreate())
    }

    @Test
    fun `no webview yet requires creating one`() {
        assertTrue(needsRecreate(hasWebView = false))
    }

    @Test
    fun `an expired webview is recreated even for the same session`() {
        assertTrue(needsRecreate(isExpired = true))
    }

    @Test
    fun `a dead webview is recreated even for the same session`() {
        assertTrue(needsRecreate(isDead = true))
    }

    @Test
    fun `a different requested session id forces a fresh session, not reuse`() {
        assertTrue(needsRecreate(currentSessionId = "session-a", requestedSessionId = "session-b"))
    }

    @Test
    fun `a null current session id (never minted yet) forces creation`() {
        assertTrue(needsRecreate(currentSessionId = null))
    }

    @Test
    fun `forceRecreate always wins even on an otherwise-healthy live session`() {
        assertTrue(needsRecreate(forceRecreate = true))
    }
}
