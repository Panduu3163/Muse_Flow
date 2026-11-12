package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineQueueRecoveryTest {
    @Test
    fun `offline recovery skips network entries and selects the next local file`() {
        val schemes = listOf("file", "museflow-resolve", "museflow-resolve", "file")

        assertEquals(
            3,
            nextPlayableQueueIndex(0, schemes.size, online = false, schemeAt = schemes::get),
        )
    }

    @Test
    fun `offline recovery stops when no later local item exists`() {
        val schemes = listOf("file", "museflow-resolve", "https")

        assertNull(nextPlayableQueueIndex(0, schemes.size, online = false, schemeAt = schemes::get))
    }

    @Test
    fun `online recovery keeps normal sequential order`() {
        val schemes = listOf("file", "museflow-resolve", "file")

        assertEquals(
            1,
            nextPlayableQueueIndex(0, schemes.size, online = true, schemeAt = schemes::get),
        )
    }
}
