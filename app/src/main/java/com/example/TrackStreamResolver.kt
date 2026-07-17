package com.example

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Resolves a catalog [Track] (title/artist only, no stream URL of its own) to a real, playable
 * JioSaavn result by searching for it on demand. [MusicData]'s tracks are just metadata - the
 * actual CDN URL only exists once we've looked the track up - so this runs lazily, right before
 * playback needs it, rather than up front for the whole catalog.
 *
 * Results are cached by track identity via a [ConcurrentHashMap] of in-flight/completed
 * [Deferred]s, so concurrent resolutions of the same track (e.g. rapid skip-back-and-forth)
 * dedupe onto a single network call instead of racing.
 */
class TrackStreamResolver(
    private val scope: CoroutineScope,
    private val provider: Provider<TrackResult> = JioSaavnProvider()
) {
    private val cache = ConcurrentHashMap<Track, Deferred<TrackResult?>>()

    suspend fun resolve(track: Track): TrackResult? =
        cache.computeIfAbsent(track) {
            scope.async(start = CoroutineStart.LAZY) {
                runCatching {
                    provider.search("${track.title} ${track.artist}")
                        .firstOrNull { it.directStreamUrl != null }
                }.getOrNull()
            }
        }.await()
}
