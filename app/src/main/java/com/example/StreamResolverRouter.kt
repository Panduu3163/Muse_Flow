package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first

private const val TAG = "StreamResolverRouter"

/**
 * The single place playback and downloads ask for a playable URL, so the [ExtractorBackend] chosen
 * in Settings governs the whole pipeline rather than just search.
 *
 * - [ExtractorBackend.INNERTUBE] resolves purely through [InnerTubeStreamResolver] (the ported
 *   module plus NewPipeExtractor). MuseFlow's own cipher/PoToken pipeline is entirely out of the
 *   path.
 * - [ExtractorBackend.LEGACY] resolves through MuseFlow's own [YouTubeStreamResolver].
 *
 * **Strict by design: there is no cross-backend fallback.** If the selected extractor can't resolve
 * a track, that's a playback failure rather than a silent hand-off to the other one. A fallback
 * would mask exactly the signal this switch exists to expose - whether the selected backend is
 * actually carrying playback on its own. Switching backends is a single tap in Settings, so a
 * genuinely broken extractor is cheap to escape from without hiding the breakage first.
 */
object StreamResolverRouter {

    /**
     * Resolves to a URL **plus the User-Agent it must be fetched with** - YouTube's CDN answers
     * 403 when a stream URL is requested with a different User-Agent than the client that
     * resolved it, so the two can't be separated.
     */
    suspend fun resolve(context: Context, videoId: String): StreamResolution? {
        val backend = activeBackend(context)
        val url = when (backend) {
            ExtractorBackend.INNERTUBE -> InnerTubeStreamResolver.resolve(videoId)
            // The legacy pipeline's URLs are resolved with the default User-Agent already.
            ExtractorBackend.LEGACY ->
                YouTubeStreamResolver.resolve(context, videoId)?.let { StreamResolution(url = it) }
        }

        if (url == null) {
            Log.w(
                TAG,
                "${backend.label} could not resolve $videoId - strict mode, not falling back. " +
                    "Switch extractor in Settings if this persists."
            )
        }
        return url
    }

    /** Clears any cached resolution for [videoId] so a failed playback re-resolves from scratch.
     * Only the legacy resolver caches; the InnerTube path resolves fresh each time. */
    fun invalidate(videoId: String) = YouTubeStreamResolver.invalidate(videoId)

    suspend fun activeBackend(context: Context): ExtractorBackend =
        runCatching { ExtractorPreference.observe(context).first() }
            .getOrDefault(ExtractorPreference.default)
}
