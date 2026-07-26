package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvedUrlCacheTest {

    private class FakeClock(var nowMs: Long = 0L) {
        val fn: () -> Long = { nowMs }
    }

    @Test
    fun `miss on an unknown key`() {
        val cache = ResolvedUrlCache()
        assertNull(cache.get("unknown"))
    }

    @Test
    fun `a cached url is served back before expiry`() {
        val clock = FakeClock(nowMs = 0L)
        val cache = ResolvedUrlCache(expirySafetyMarginMs = 0L, clock = clock.fn)
        cache.put("v1", "https://example.com/stream1", expiresInSeconds = 100)

        clock.nowMs = 99_000
        assertEquals("https://example.com/stream1", cache.get("v1"))
    }

    @Test
    fun `an entry is evicted once it expires`() {
        val clock = FakeClock(nowMs = 0L)
        val cache = ResolvedUrlCache(expirySafetyMarginMs = 0L, clock = clock.fn)
        cache.put("v1", "https://example.com/stream1", expiresInSeconds = 100)

        clock.nowMs = 100_000
        assertNull(cache.get("v1"))
        // The lazy-eviction-on-get should have actually removed it, not just hidden it.
        assertEquals(0, cache.size())
    }

    @Test
    fun `the safety margin shortens the effective ttl`() {
        val clock = FakeClock(nowMs = 0L)
        val cache = ResolvedUrlCache(expirySafetyMarginMs = 15_000L, clock = clock.fn)
        cache.put("v1", "https://example.com/stream1", expiresInSeconds = 100)

        // Server said 100s, but the 15s safety margin means it should already be gone by 85s.
        clock.nowMs = 85_000
        assertNull(cache.get("v1"))
    }

    @Test
    fun `a null expiresInSeconds falls back to the fallback ttl`() {
        val clock = FakeClock(nowMs = 0L)
        val cache = ResolvedUrlCache(fallbackTtlMs = 60_000L, expirySafetyMarginMs = 0L, clock = clock.fn)
        cache.put("v1", "https://example.com/stream1", expiresInSeconds = null)

        clock.nowMs = 59_000
        assertEquals("https://example.com/stream1", cache.get("v1"))
        clock.nowMs = 60_000
        assertNull(cache.get("v1"))
    }

    @Test
    fun `a ttl that the safety margin would push non-positive is not cached at all`() {
        val cache = ResolvedUrlCache(expirySafetyMarginMs = 15_000L)
        // 10s reported TTL - 15s margin = negative, coerced to 0 - "not worth caching".
        cache.put("v1", "https://example.com/stream1", expiresInSeconds = 10)
        assertNull(cache.get("v1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `invalidate drops an entry even if it hasn't expired yet`() {
        val cache = ResolvedUrlCache()
        cache.put("v1", "https://example.com/stream1", expiresInSeconds = 1000)
        cache.invalidate("v1")
        assertNull(cache.get("v1"))
    }

    @Test
    fun `the eldest entry is evicted once maxEntries is exceeded`() {
        val cache = ResolvedUrlCache(maxEntries = 2, expirySafetyMarginMs = 0L)
        cache.put("v1", "url1", expiresInSeconds = 1000)
        cache.put("v2", "url2", expiresInSeconds = 1000)
        cache.put("v3", "url3", expiresInSeconds = 1000)

        assertNull(cache.get("v1"))
        assertEquals("url2", cache.get("v2"))
        assertEquals("url3", cache.get("v3"))
        assertEquals(2, cache.size())
    }

    @Test
    fun `re-getting an entry counts as access for the lru order`() {
        val cache = ResolvedUrlCache(maxEntries = 2, expirySafetyMarginMs = 0L)
        cache.put("v1", "url1", expiresInSeconds = 1000)
        cache.put("v2", "url2", expiresInSeconds = 1000)
        // Touch v1 so it's now the most-recently-used, leaving v2 as the eldest.
        cache.get("v1")
        cache.put("v3", "url3", expiresInSeconds = 1000)

        assertEquals("url1", cache.get("v1"))
        assertNull(cache.get("v2"))
        assertEquals("url3", cache.get("v3"))
    }
}
