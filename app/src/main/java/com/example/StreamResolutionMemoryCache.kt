package com.example

import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class StreamResolutionKey(val videoId: String, val backend: ExtractorBackend, val quality: AudioQualityMode)

/** Short-lived complete resolutions, including the required UA. No signed URLs are persisted. */
internal class StreamResolutionMemoryCache(
    private val maxEntries: Int = 64,
    private val ttlMs: Long = 90_000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val resolution: StreamResolution, val deadline: Long)
    private class Flight(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val entries = LinkedHashMap<StreamResolutionKey, Entry>(16, .75f, true)
    private val flights = mutableMapOf<StreamResolutionKey, Flight>()
    private var revision = 0L

    @Synchronized private fun get(key: StreamResolutionKey): StreamResolution? {
        val entry = entries[key] ?: return null
        if (clock() >= entry.deadline) { entries.remove(key); return null }
        return entry.resolution
    }

    suspend fun resolve(key: StreamResolutionKey, loader: suspend () -> StreamResolution?): StreamResolution? {
        get(key)?.let { return it }
        val flight = synchronized(this) { flights.getOrPut(key) { Flight() }.also { it.users++ } }
        try {
            return flight.mutex.withLock {
                get(key)?.let { return@withLock it }
                val generation = synchronized(this) { revision }
                val resolution = loader() ?: return@withLock null
                val expiresAt = runCatching {
                    URI(resolution.url).rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
                        val parts = pair.split('=', limit = 2)
                        if (parts.size == 2 && parts[0] == "expire")
                            URLDecoder.decode(parts[1], "UTF-8").toLongOrNull()?.times(1_000L) else null
                    }
                }.getOrNull()
                val lifetime = minOf(ttlMs, expiresAt?.let { it - wallClock() - 15_000L } ?: ttlMs)
                synchronized(this) {
                    if (generation == revision && lifetime > 0 && maxEntries > 0) {
                        entries[key] = Entry(resolution, clock() + lifetime)
                        while (entries.size > maxEntries) entries.remove(entries.keys.first())
                    }
                }
                resolution
            }
        } finally {
            synchronized(this) { if (--flight.users == 0) flights.remove(key) }
        }
    }

    @Synchronized fun invalidate(videoId: String) {
        entries.keys.removeAll { it.videoId == videoId }
        // A cancelled/obsolete warmup must not repopulate an entry after an explicit retry.
        revision++
    }
}
