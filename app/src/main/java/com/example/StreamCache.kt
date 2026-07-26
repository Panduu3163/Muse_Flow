package com.example

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache

/**
 * Disk cache for streamed audio bytes, so replaying or seeking within a track that's already
 * played doesn't re-request them over the network - and, via [prefetch], seeking *ahead* of
 * playback can be instant too, not just behind it.
 *
 * Deliberately separate from the user-facing Download feature (`DownloadRepository`, which stores
 * full files under `filesDir/downloads` permanently): this lives under `cacheDir`, is
 * OS-reclaimable under storage pressure, is capped by [MAX_CACHE_BYTES], and the user never sees
 * or manages it - it exists purely so playback feels instant, the same role Coil's image disk
 * cache plays for artwork (see `MuseFlowApplication.kt`).
 *
 * Keyed by the stable resolve-placeholder URI ([youTubeResolvePlaceholderUri]'s videoId), not the
 * actual resolved CDN URL - that URL carries a short-lived signed expiry token that changes on
 * every resolve, which would make the cache key different every time and never hit. [CacheDataSource]
 * sees the placeholder URI first (it wraps the resolving upstream), so this falls out for free
 * from the existing lazy-resolve design rather than needing a custom `CacheKeyFactory`.
 */
@UnstableApi
object StreamCache {
    private const val MAX_CACHE_BYTES = 500L * 1024 * 1024

    @Volatile private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            context.cacheDir.resolve("stream_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { instance = it }
    }
}
