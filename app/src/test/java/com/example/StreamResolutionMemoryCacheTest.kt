package com.example

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamResolutionMemoryCacheTest {
    private fun key(id: String = "recording", backend: ExtractorBackend = ExtractorBackend.INNERTUBE,
        quality: AudioQualityMode = AudioQualityMode.BestAvailable) = StreamResolutionKey(id, backend, quality)
    private val result = StreamResolution("https://example.invalid/audio", "required-user-agent")

    @Test fun repeatedRequestsReuseTheCompleteResolutionIncludingUserAgent() = runTest {
        val cache = StreamResolutionMemoryCache(); var calls = 0
        repeat(2) { assertEquals(result, cache.resolve(key()) { calls++; result }) }
        assertEquals(1, calls)
    }
    @Test fun recordingBackendAndQualityRemainIndependent() = runTest {
        val cache = StreamResolutionMemoryCache(); var calls = 0
        for (k in listOf(key(), key("other"), key(backend = ExtractorBackend.LEGACY), key(quality = AudioQualityMode.DataSaver)))
            cache.resolve(k) { calls++; result }
        assertEquals(4, calls)
    }
    @Test fun monotonicTtlExpires() = runTest {
        var now = 0L; var calls = 0
        val cache = StreamResolutionMemoryCache(ttlMs = 100, clock = { now })
        cache.resolve(key()) { calls++; result }; now = 100
        cache.resolve(key()) { calls++; result }
        assertEquals(2, calls)
    }
    @Test fun signedUrlExpiryShortensTheCacheLifetime() = runTest {
        var now = 0L; var calls = 0
        val cache = StreamResolutionMemoryCache(clock = { now }, wallClock = { 0 })
        val signed = result.copy(url = "https://example.invalid/audio?expire=16")
        cache.resolve(key()) { calls++; signed }; now = 1000
        cache.resolve(key()) { calls++; signed }
        assertEquals(2, calls)
    }
    @Test fun expiredSignedUrlsAreNotCached() = runTest {
        var calls = 0; val cache = StreamResolutionMemoryCache(wallClock = { 0 })
        repeat(2) { cache.resolve(key()) { calls++; result.copy(url = "https://example.invalid/a?expire=14") } }
        assertEquals(2, calls)
    }
    @Test fun nullAndFailedResolutionsDoNotBecomeCachedSuccess() = runTest {
        val cache = StreamResolutionMemoryCache(); var calls = 0
        assertNull(cache.resolve(key()) { calls++; null })
        try { cache.resolve(key()) { calls++; throw IllegalStateException("unavailable") }; fail() }
        catch (expected: IllegalStateException) { assertEquals("unavailable", expected.message) }
        assertEquals(result, cache.resolve(key()) { calls++; result }); assertEquals(3, calls)
    }
    @Test fun simultaneousPlaybackAndWarmupShareOneResolution() = runTest {
        val cache = StreamResolutionMemoryCache(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val requests = List(5) { async { cache.resolve(key()) { calls++; gate.await(); result } } }
        runCurrent(); assertEquals(1, calls); gate.complete(Unit)
        assertTrue(requests.awaitAll().all { it == result }); assertEquals(1, calls)
    }
    @Test fun cancelledWarmupReleasesForegroundWaiter() = runTest {
        val cache = StreamResolutionMemoryCache()
        val warmup = async { cache.resolve(key()) { awaitCancellation() } }
        runCurrent()
        val foreground = async { cache.resolve(key()) { result } }
        runCurrent(); warmup.cancelAndJoin()
        assertEquals(result, foreground.await())
    }
    @Test fun retryInvalidationPreventsAnOlderWarmupFromRepopulatingCache() = runTest {
        val cache = StreamResolutionMemoryCache(); val gate = CompletableDeferred<Unit>(); var calls = 0
        val old = async { cache.resolve(key()) { calls++; gate.await(); result } }
        runCurrent(); cache.invalidate("recording"); gate.complete(Unit); old.await()
        cache.resolve(key()) { calls++; result }
        assertEquals(2, calls)
    }
    @Test fun cacheKeepsItsSizeBoundAndEvictsLeastRecentlyUsed() = runTest {
        val cache = StreamResolutionMemoryCache(maxEntries = 2); var calls = 0
        for (id in listOf("a", "b", "a", "c", "a", "b")) cache.resolve(key(id)) { calls++; result }
        assertEquals(4, calls)
    }
    @Test fun retryDiscardsAllVariantsForThatRecordingOnly() = runTest {
        val cache = StreamResolutionMemoryCache(); var calls = 0
        val variants = listOf(key(), key(backend = ExtractorBackend.LEGACY), key(quality = AudioQualityMode.DataSaver))
        for (k in variants + key("other")) cache.resolve(k) { calls++; result }
        cache.invalidate("recording")
        for (k in variants + key("other")) cache.resolve(k) { calls++; result }
        assertEquals(7, calls)
    }
}
