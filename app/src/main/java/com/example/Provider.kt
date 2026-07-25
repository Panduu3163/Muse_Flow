package com.example

/** Which backend a search result came from - lets the UI tag results (e.g. a "YouTube Music"
 * badge) and lets playback decide how to obtain a playable URL. [YOUTUBE_MUSIC] results carry
 * only a videoId and are resolved on demand by [YouTubeStreamResolver]; [LOCAL_DEVICE] results
 * (a MediaStore content:// URI) are directly playable with no resolve step. */
enum class MusicSource { YOUTUBE_MUSIC, LOCAL_DEVICE }

/** A single track found by a [Provider], from any source (YouTube Music, on-device, ...). */
data class TrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String?,
    val source: String,
    val sourceType: MusicSource,
    /**
     * Set only when the provider can derive a playable URL straight from search results (e.g. a
     * [MusicSource.LOCAL_DEVICE] content:// URI), needing no extra network round-trip. Null for
     * providers that require a separate resolve step (YouTube Music's videoId -> /player call).
     */
    val directStreamUrl: String? = null,
    /** Cover art URL, used as the media notification's large icon when present. */
    val imageUrl: String? = null
)

/** An album search result, enough to render a row and fetch its tracklist - [id] is a YouTube
 * Music browseId. */
data class AlbumResult(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val songCount: Int?,
    val sourceType: MusicSource = MusicSource.YOUTUBE_MUSIC
)

/** An artist search result, enough to render a row and fetch their top tracks - [id] is a YouTube
 * Music channel browseId. [listenerCount] is a source-formatted monthly-listener-style string
 * (e.g. "54.6M monthly audience") when the search response happened to include one for free. */
data class ArtistResult(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val sourceType: MusicSource = MusicSource.YOUTUBE_MUSIC,
    val listenerCount: String? = null
)

/** An artist's real top-tracks list plus, when the source exposes it, a listener-count string in
 * whatever format that source presents it (already formatted/abbreviated - e.g. "54.6M monthly
 * audience") - null if unavailable for this particular artist. Bundled together (rather than
 * fetched separately) because both pieces come from the exact same underlying API response. */
data class ArtistTracklist(
    val tracks: List<TrackResult>,
    val listenerCount: String? = null
)

/** A playlist search result, enough to render a row and fetch its tracklist - [id] is a YouTube
 * Music browseId. */
data class PlaylistResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val songCount: Int?,
    val sourceType: MusicSource = MusicSource.YOUTUBE_MUSIC
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
 * can treat online and on-device sources interchangeably.
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
    sourceType = sourceType,
    sourceId = id
)
