package com.example

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem

/**
 * Adapts the ported `:innertube` client to MuseFlow's [Provider] contract, so the rest of the app
 * (search, downloads, artist pages) is unaware of which extractor is actually behind it.
 *
 * Stream resolution deliberately still goes through MuseFlow's own [YouTubeStreamResolver]: the
 * cipher/PoToken pipeline there is this app's own work and is what actually turns a videoId into a
 * playable URL. InnerTube supplies discovery (search/browse metadata); MuseFlow supplies playback.
 */
class InnerTubeMusicProvider(private val context: Context) : Provider<TrackResult> {
    override val name: String = "YouTube Music (InnerTube)"

    // getOrThrow, not getOrNull: swallowing the failure here would make "the network is down"
    // look identical to "YouTube genuinely has no results", which is precisely the ambiguity the
    // strict extractor routing exists to avoid. The ViewModel turns the throw into a real error.
    override suspend fun search(query: String): List<TrackResult> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow()
            .items
            .filterIsInstance<SongItem>()
            .map { it.toTrackResult() }

    /** Resolved entirely within the ported module - see [InnerTubeStreamResolver]. */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? =
        InnerTubeStreamResolver.resolve(item.id)

    suspend fun searchAlbums(query: String): List<AlbumResult> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM)
            .getOrThrow()
            .items
            .filterIsInstance<AlbumItem>()
            .map { album ->
                AlbumResult(
                    id = album.browseId,
                    title = album.title,
                    artist = album.artists?.joinToString(", ") { it.name }.orEmpty(),
                    imageUrl = album.thumbnail,
                    songCount = null,
                    sourceType = MusicSource.YOUTUBE_MUSIC,
                )
            }
            .orEmpty()

    suspend fun searchArtists(query: String): List<ArtistResult> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST)
            .getOrThrow()
            .items
            .filterIsInstance<ArtistItem>()
            .map { artist ->
                ArtistResult(
                    id = artist.id,
                    name = artist.title,
                    imageUrl = artist.thumbnail,
                    sourceType = MusicSource.YOUTUBE_MUSIC,
                )
            }
            .orEmpty()

    suspend fun searchPlaylists(query: String): List<PlaylistResult> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
            .getOrThrow()
            .items
            .filterIsInstance<PlaylistItem>()
            .map { playlist ->
                PlaylistResult(
                    id = playlist.id,
                    title = playlist.title,
                    subtitle = playlist.author?.name.orEmpty(),
                    imageUrl = playlist.thumbnail,
                    songCount = playlist.songCountText?.filter { it.isDigit() }?.toIntOrNull(),
                    sourceType = MusicSource.YOUTUBE_MUSIC,
                )
            }
            .orEmpty()

    /** Search suggestions as the user types - an endpoint the legacy provider never had. */
    suspend fun suggestions(query: String): List<String> =
        YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty()
}

/** InnerTube reports duration in whole seconds; MuseFlow's UI wants "m:ss". */
private fun SongItem.toTrackResult(): TrackResult = TrackResult(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    duration = duration?.let { total -> "${total / 60}:${(total % 60).toString().padStart(2, '0')}" },
    source = album?.name ?: "YouTube Music",
    sourceType = MusicSource.YOUTUBE_MUSIC,
    // Null on purpose: a YouTube result is only playable after the cipher/PoToken resolve step.
    directStreamUrl = null,
    imageUrl = thumbnail,
)
