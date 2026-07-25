package com.example

/**
 * Pure, clock-injectable TTL cache for resolved stream URLs - extracted out of
 * [YouTubeStreamResolver] so its expiry/eviction math is directly unit-testable without dragging
 * in Context/OkHttp/WebView. Access-order [LinkedHashMap] capped at [maxEntries], same
 * LRU-via-removeEldestEntry shape as Metrolist's `songUrlCache` (GPL-3.0).
 */
class ResolvedUrlCache(
    private val maxEntries: Int = 200,
    // Expire a little before the server-declared TTL, not exactly at it, so a slow-to-open HTTP
    // connection right at the boundary doesn't race a genuine 403.
    private val expirySafetyMarginMs: Long = 15_000L,
    // Used only if a caller ever passes a null expiresInSeconds outright.
    private val fallbackTtlMs: Long = 4 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class CachedResolution(val url: String, val expiresAtMs: Long)

    private val cache = object : LinkedHashMap<String, CachedResolution>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResolution>?) =
            size > maxEntries
    }

    /** Returns the cached URL for [key], or null if there is none or it has expired (an expired
     * entry is evicted as a side effect of this call). */
    @Synchronized
    fun get(key: String): String? {
        val cached = cache[key] ?: return null
        return if (clock() >= cached.expiresAtMs) {
            cache.remove(key)
            null
        } else {
            cached.url
        }
    }

    /** Caches [url] for [key] until [expiresInSeconds] from now (minus the safety margin), or
     * [fallbackTtlMs] if null. A non-positive resulting TTL is treated as "not worth caching" and
     * silently skipped rather than caching something already-expired. */
    @Synchronized
    fun put(key: String, url: String, expiresInSeconds: Long?) {
        val ttlMs = ((expiresInSeconds?.times(1000L) ?: fallbackTtlMs) - expirySafetyMarginMs)
            .coerceAtLeast(0L)
        if (ttlMs <= 0L) return
        cache[key] = CachedResolution(url, clock() + ttlMs)
    }

    /** Drops any cached entry for [key], regardless of whether it's still live. */
    @Synchronized
    fun invalidate(key: String) {
        cache.remove(key)
    }

    /** Current entry count, including any not-yet-lazily-evicted expired entries. */
    @Synchronized
    fun size(): Int = cache.size
}
