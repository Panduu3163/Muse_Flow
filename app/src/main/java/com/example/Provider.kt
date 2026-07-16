package com.example

/** A single track found by a [Provider], from any source (YouTube Music, JioSaavn, ...). */
data class TrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String?,
    val source: String,
    /**
     * Set only when the provider can derive a playable URL straight from search results (e.g.
     * JioSaavn's encrypted_media_url), needing no extra network round-trip. Null for providers
     * that require a separate resolve step (e.g. YouTube Music's videoId -> /player call).
     */
    val directStreamUrl: String? = null
)

/**
 * A resolved, playable audio stream. If [userAgent] is set, it must be sent as the request's
 * User-Agent header when fetching [url] - some CDNs (YouTube's) tie the URL to the User-Agent
 * that resolved it and reject a mismatched one.
 */
data class StreamResolution(
    val url: String,
    val userAgent: String? = null
)

/**
 * Common contract for a music search + stream-resolution backend, so callers (like the player)
 * can try multiple sources interchangeably - e.g. YouTube Music as primary, JioSaavn as a
 * fallback when YouTube's result isn't playable.
 */
interface Provider<T> {
    val name: String
    suspend fun search(query: String): List<T>
    suspend fun getStreamUrl(item: T): StreamResolution?
}
