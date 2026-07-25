package com.example

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Resolves a catalog [Track] (title/artist only, no stream URL of its own) to a real, playable
 * stream by searching YouTube Music for it on demand and running the matched videoId through
 * [YouTubeStreamResolver]. [MusicData]'s tracks are just metadata - the actual CDN URL only
 * exists once we've looked the track up - so this runs lazily, right before playback needs it,
 * rather than up front for the whole catalog.
 *
 * Results are cached by track identity via a [ConcurrentHashMap] of in-flight/completed
 * [Deferred]s, so concurrent resolutions of the same track (e.g. rapid skip-back-and-forth)
 * dedupe onto a single network call instead of racing.
 */
class TrackStreamResolver(
    private val scope: CoroutineScope,
    private val appContext: Context
) {
    private val cache = ConcurrentHashMap<Track, Deferred<TrackResult?>>()

    suspend fun resolve(track: Track): TrackResult? =
        cache.computeIfAbsent(track) {
            scope.async(start = CoroutineStart.LAZY) {
                runCatching {
                    val match = YouTubeMusicProvider(appContext)
                        .search("${track.title} ${track.artist}")
                        .firstOrNull()
                    val streamUrl = match?.let { StreamResolverRouter.resolve(appContext, it.id)?.url }
                    if (match != null && streamUrl != null) {
                        match.copy(directStreamUrl = streamUrl)
                    } else {
                        null
                    }
                }.getOrNull()
            }
        }.await()
}
