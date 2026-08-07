package com.example

import androidx.compose.runtime.Immutable

/** Which backend a search result came from - lets the UI tag results (e.g. a "YouTube Music"
 * badge) and lets playback decide how to obtain a playable URL. [YOUTUBE_MUSIC] results carry
 * only a videoId and are resolved on demand by [YouTubeStreamResolver]; [LOCAL_DEVICE] results
 * (a MediaStore content:// URI) are directly playable with no resolve step. */
enum class MusicSource { YOUTUBE_MUSIC, LOCAL_DEVICE }

/** A single track found by a [Provider], from any source (YouTube Music, on-device, ...).
 * Marked [Immutable] (all `val`, all stable field types) so Search/Home/Playlist list rows can
 * skip recomposition when other state changes around them. */
@Immutable
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
    val imageUrl: String? = null,
    /** The track's album browseId, when the source exposes one - lets "View album" navigate
     * straight there. Null for local files and any result where the source genuinely has no
     * album (a single, or a video not attached to one). */
    val albumId: String? = null,
    /** The track's (primary) artist browseId, when the source exposes one - lets "View artist"
     * navigate straight there. Null for local files. */
    val artistId: String? = null,
)

/** An album search result, enough to render a row and fetch its tracklist - [id] is a YouTube
 * Music browseId. */
@Immutable
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
@Immutable
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
 * fetched separately) because both pieces come from the exact same underlying API response.
 *
 * [name]/[imageUrl] are the artist's own header details, added so the Artist screen (which
 * navigates here by id alone - e.g. from a Liked track's stored [TrackResult.artistId], with no
 * cached [ArtistResult] in hand) doesn't need a second fetch just to render its own title. Null
 * on a backend/page that doesn't expose them; the screen falls back to a generic label. */
@Immutable
data class ArtistTracklist(
    val tracks: List<TrackResult>,
    /** YouTube's subscriber count (e.g. "1.2M subscribers") and its separate monthly-listener
     * figure (e.g. "54.6M monthly listeners") - kept apart rather than coalesced into one string
     * because the Artist screen shows both as their own capsule, same as Echo Music does. Either
     * can be null independently of the other. */
    val subscriberCountText: String? = null,
    val monthlyListenerCountText: String? = null,
    val name: String? = null,
    val imageUrl: String? = null,
    /** The artist page's own bio/description text, for the Artist screen's "About" section. Null
     * on a backend/page that doesn't expose one. */
    val description: String? = null,
    /** Discography and "fans might also like"-style shelves, from the same page response as
     * [tracks] - the artist page's own sections carry these already; they just weren't being
     * read before. Empty on the legacy backend, which doesn't parse them (deliberately not
     * attempted there - see the plan doc's note on why legacy JSON parsing stays conservative). */
    val albums: List<AlbumResult> = emptyList(),
    val relatedArtists: List<ArtistResult> = emptyList(),
)

/** An album's own header details plus its tracklist - the album-page equivalent of
 * [ArtistTracklist], for the same reason: the Album screen navigates here by id alone. */
@Immutable
data class AlbumDetails(
    val title: String?,
    val artist: String?,
    val imageUrl: String?,
    val tracks: List<TrackResult>,
)

/** One shelf of a generic browse page (a mood/genre page, or anything else reached by browseId +
 * params) - mixed content, same as an artist page's extra shelves, so it's the same four buckets
 * rather than a sealed type per item kind. */
@Immutable
data class BrowseSection(
    val title: String?,
    val tracks: List<TrackResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val playlists: List<PlaylistResult> = emptyList(),
)

/** A generic browse page, reached by a `browseId` (+ optional `params`) rather than a fixed
 * endpoint - what a mood/genre tile from [MoodGenreCategory] actually opens. */
@Immutable
data class BrowsePage(
    val title: String?,
    val sections: List<BrowseSection>,
)

/** One tappable mood/genre tile - [colorArgb] is the tile's own background colour from the
 * source (YouTube Music picks a different one per tile), [browseId]/[params] together are what
 * [BrowsePage] is fetched with. */
@Immutable
data class MoodGenreTile(
    val title: String,
    val browseId: String,
    val params: String?,
    val colorArgb: Long,
)

/** A titled group of [MoodGenreTile]s (e.g. "Moods", "Genres") - the Explore screen's own
 * top-level content. */
@Immutable
data class MoodGenreCategory(
    val title: String,
    val tiles: List<MoodGenreTile>,
)

/** A playlist search result, enough to render a row and fetch its tracklist - [id] is a YouTube
 * Music browseId. */
@Immutable
data class PlaylistResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val songCount: Int?,
    val sourceType: MusicSource = MusicSource.YOUTUBE_MUSIC
)

/**
 * One page of a paginated track list, with a [continuation] token to fetch the next page - null
 * once the source has genuinely run out (as opposed to a page that's merely empty because a
 * dedupe filter removed everything from it).
 */
data class TrackPage(
    val items: List<TrackResult>,
    val continuation: String?,
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

/** Same identity key as [Track.downloadKey] - title/artist based, not [TrackResult.id], since a
 * download made from search and the same track reached again from a shelf/playlist won't share an
 * id when their sources differ, but will always share a title/artist. Lets playback recognize "I
 * already have this on disk" regardless of which screen the track came from. */
fun TrackResult.downloadKey(): String = "${title.trim().lowercase()}::${artist.trim().lowercase()}"

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
    sourceId = id,
    albumId = albumId,
    artistId = artistId,
)
