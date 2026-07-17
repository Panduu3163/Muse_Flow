package com.example

/** Which backend a search result came from - lets the UI tag results (e.g. a "YouTube Music"
 * badge) and lets playback decide whether a result is directly playable (JioSaavn) or needs to
 * be resolved to a JioSaavn match first (YouTube Music, which this app never streams from
 * directly). */
enum class MusicSource { JIOSAAVN, YOUTUBE_MUSIC, NETEASE }

/** A single track found by a [Provider], from any source (YouTube Music, JioSaavn, ...). */
data class TrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String?,
    val source: String,
    val sourceType: MusicSource,
    /**
     * Set only when the provider can derive a playable URL straight from search results (e.g.
     * JioSaavn's encrypted_media_url), needing no extra network round-trip. Null for providers
     * that require a separate resolve step (e.g. YouTube Music's videoId -> /player call).
     */
    val directStreamUrl: String? = null,
    /** Cover art URL, used as the media notification's large icon when present. */
    val imageUrl: String? = null
)

/** A JioSaavn album search result, enough to render a row and fetch its tracklist. */
data class AlbumResult(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val songCount: Int?
)

/** A JioSaavn artist search result, enough to render a row and fetch their top tracks. */
data class ArtistResult(
    val id: String,
    val name: String,
    val imageUrl: String?
)

/** A JioSaavn playlist search result, enough to render a row and fetch its tracklist. */
data class PlaylistResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val songCount: Int?
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

/** A provider result mapped to a real, playable [Track]: [Track.streamUrl] and [Track.imageUrl]
 * carry the actual CDN/cover-art URLs straight from search, so playing one needs no further
 * resolution step and its artwork is already known everywhere the track flows (search results,
 * Home shelves, mini-player, Now Playing, notification). Shared by every screen that turns search
 * results into playable tracks - Search, and Home's real mood/genre shelves. */
fun TrackResult.toPlayableTrack(gradientIndex: Int): Track = Track(
    title = title,
    artist = artist,
    album = source,
    duration = duration ?: "-:--",
    plays = "",
    gradientIndex = gradientIndex,
    imageUrl = imageUrl,
    streamUrl = directStreamUrl,
    sourceType = sourceType
)
