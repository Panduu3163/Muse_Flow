package com.example

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint

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
                    songCount = playlist.songCountText.asSongCount(),
                    sourceType = MusicSource.YOUTUBE_MUSIC,
                )
            }
            .orEmpty()

    /** Search suggestions as the user types - an endpoint the legacy provider never had. */
    suspend fun suggestions(query: String): List<String> =
        YouTube.searchSuggestions(query).getOrNull()?.queries.orEmpty()

    /** YouTube Music's own "New releases" shelf (`FEmusic_new_releases`) - an endpoint the legacy
     * provider never had either. As of the current page layout this is a songs/videos carousel, not
     * an albums grid (see [YouTube.newReleaseSongs]'s doc), so this returns playable tracks. */
    suspend fun getNewReleases(): List<TrackResult> =
        YouTube.newReleaseSongs()
            .getOrThrow()
            .distinctBy { it.id }
            .map { it.toTrackResult() }

    /**
     * A generic browse page - what a [MoodGenreTile] (or anything else reached by browseId+params
     * rather than a fixed endpoint) opens. Genuinely mixed content per shelf (a mood page can mix
     * playlists, artists and albums in the same section), so every item type is gathered rather
     * than one assumed per section the way [getArtistTracklist]'s primary songs shelf can assume
     * songs.
     */
    suspend fun browse(browseId: String, params: String?): BrowsePage {
        val result = YouTube.browse(browseId, params).getOrThrow()
        return BrowsePage(
            title = result.title,
            sections = result.items.map { item ->
                BrowseSection(
                    title = item.title,
                    tracks = item.items.filterIsInstance<SongItem>().map { it.toTrackResult() },
                    albums = item.items.filterIsInstance<AlbumItem>().map {
                        AlbumResult(
                            id = it.browseId,
                            title = it.title,
                            artist = it.artists?.joinToString(", ") { artist -> artist.name }.orEmpty(),
                            imageUrl = it.thumbnail,
                            songCount = null,
                            sourceType = MusicSource.YOUTUBE_MUSIC,
                        )
                    },
                    artists = item.items.filterIsInstance<ArtistItem>().map {
                        ArtistResult(
                            id = it.id,
                            name = it.title,
                            imageUrl = it.thumbnail,
                            sourceType = MusicSource.YOUTUBE_MUSIC,
                        )
                    },
                    playlists = item.items.filterIsInstance<PlaylistItem>().map {
                        PlaylistResult(
                            id = it.id,
                            title = it.title,
                            subtitle = it.author?.name.orEmpty(),
                            imageUrl = it.thumbnail,
                            songCount = it.songCountText.asSongCount(),
                            sourceType = MusicSource.YOUTUBE_MUSIC,
                        )
                    },
                )
            },
        )
    }

    /** The Explore screen's own top-level content - categories of tappable mood/genre tiles, each
     * opening a [browse] page via its own browseId+params. */
    suspend fun getMoodAndGenres(): List<MoodGenreCategory> =
        YouTube.moodAndGenres()
            .getOrThrow()
            .map { category ->
                MoodGenreCategory(
                    title = category.title,
                    tiles = category.items.map { item ->
                        MoodGenreTile(
                            title = item.title,
                            browseId = item.endpoint.browseId,
                            params = item.endpoint.params,
                            colorArgb = item.stripeColor,
                        )
                    },
                )
            }

    /** Tracks of an album, from the `MPREb_...` browseId carried by [AlbumResult.id]. */
    suspend fun getAlbumTracks(albumId: String): List<TrackResult> =
        YouTube.album(albumId)
            .getOrThrow()
            .songs
            .map { it.toTrackResult() }

    /**
     * An artist's songs, from the `UC...` browseId carried by [ArtistResult.id].
     *
     * An artist page is a set of mixed shelves (songs, albums, singles, related artists), so the
     * songs are gathered by filtering every shelf rather than reading one fixed shelf - which
     * shelf holds them varies by artist. Distinct by id because a track can appear in both the
     * "Songs" and "Top songs" shelves of the same page.
     */
    suspend fun getArtistTracklist(artistId: String): ArtistTracklist {
        val page = YouTube.artist(artistId).getOrThrow()
        val allItems = page.sections.flatMap { it.items }
        return ArtistTracklist(
            tracks = allItems
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .map { it.toTrackResult() },
            subscriberCountText = page.subscriberCountText,
            monthlyListenerCountText = page.monthlyListenerCount,
            name = page.artist.title,
            imageUrl = page.artist.thumbnail,
            description = page.description,
            // Discography and "fans might also like" shelves, gathered across every section the
            // page returned rather than one fixed shelf - same reasoning as the songs above:
            // which shelf holds them varies by artist, and one page can carry more than one
            // (Albums, Singles, EPs are all separate InnerTube shelves, merged here into one list
            // rather than kept apart - a first version doesn't need MuseFlow's own Album/Single
            // distinction, only the destination each entry navigates to).
            albums = allItems.filterIsInstance<AlbumItem>().distinctBy { it.browseId }.map {
                AlbumResult(
                    id = it.browseId,
                    title = it.title,
                    artist = it.artists?.joinToString(", ") { artist -> artist.name }.orEmpty(),
                    imageUrl = it.thumbnail,
                    songCount = null,
                    sourceType = MusicSource.YOUTUBE_MUSIC,
                )
            },
            relatedArtists = allItems.filterIsInstance<ArtistItem>()
                .filter { it.id != artistId }
                .distinctBy { it.id }
                .map {
                    ArtistResult(
                        id = it.id,
                        name = it.title,
                        imageUrl = it.thumbnail,
                        sourceType = MusicSource.YOUTUBE_MUSIC,
                    )
                },
        )
    }

    /** An album's header (title/artist/cover) plus its tracklist, from the `MPREb_...` browseId
     * carried by [AlbumResult.id] - the counterpart to [getArtistTracklist] for the Album screen,
     * which navigates by id alone and needs its own title to render before the tracklist arrives. */
    suspend fun getAlbumDetails(albumId: String): AlbumDetails {
        val page = YouTube.album(albumId).getOrThrow()
        return AlbumDetails(
            title = page.album.title,
            artist = page.album.artists?.joinToString(", ") { it.name },
            imageUrl = page.album.thumbnail,
            tracks = page.songs.map { it.toTrackResult() },
        )
    }

    /**
     * Tracks of a playlist, from the id carried by [PlaylistResult.id].
     *
     * YouTube returns a playlist one page at a time, so the pages are followed: without this a
     * "27 songs" playlist loaded 18 and Play queued 18, silently dropping the rest. The cap keeps
     * a 500-track playlist from turning one tap into a dozen sequential requests - past that many
     * tracks, what the sheet shows stops being something anyone reads through anyway.
     */
    suspend fun getPlaylistTracks(playlistId: String): List<TrackResult> {
        val page = YouTube.playlist(playlistId).getOrThrow()
        val songs = page.songs.toMutableList()

        var continuation = page.songsContinuation
        while (continuation != null && songs.size < PLAYLIST_TRACK_CAP) {
            // A failed continuation ends paging rather than failing the whole load - the tracks
            // already gathered are still worth showing.
            val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            if (next.songs.isEmpty()) break
            songs += next.songs
            continuation = next.continuation
        }

        return songs.map { it.toTrackResult() }
    }

    /**
     * A radio queue seeded from [videoId] - what YouTube Music's own "Start radio" produces.
     *
     * The `next` endpoint is the one the web player calls for radio, and it returns the seed track
     * at the head of the mix. That seed is kept rather than stripped, so the caller can play the
     * result from index 0 and hear the song it started from - which is what "start radio on this
     * track" means to anyone using it.
     */
    /**
     * The real YouTube Music charts feed (`FEmusic_charts`), flattened to one ordered song list -
     * trending first, then top songs, matching the section order the page itself returns them in.
     *
     * The raw page also carries album/single shelves (`ChartsPage.ChartType.NEW_RELEASES`,
     * `GENRE`), which aren't songs at all - [SongItem] is filtered specifically rather than mapped
     * generically, so those don't silently produce broken rows with no playable id.
     */
    suspend fun getChartsTracks(): List<TrackResult> =
        YouTube.getChartsPage()
            .getOrThrow()
            .sections
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
            .map { it.toTrackResult() }

    suspend fun getRadioTracks(videoId: String): List<TrackResult> =
        YouTube.next(WatchEndpoint(videoId = videoId))
            .getOrThrow()
            .items
            .distinctBy { it.id }
            .map { it.toTrackResult() }

    /** YouTube Music's own lyrics tab - plain text only (no line/word timing, unlike LRCLib/
     * BetterLyrics), so it's a last-resort fallback rather than tried first. Requires an extra
     * `next()` call to reach the tab's `lyricsEndpoint` before `lyrics()` can fetch it - the
     * endpoint isn't derivable from the videoId alone. */
    suspend fun getLyricsText(videoId: String): String? {
        val lyricsEndpoint = YouTube.next(WatchEndpoint(videoId = videoId))
            .getOrNull()
            ?.lyricsEndpoint
            ?: return null
        return YouTube.lyrics(lyricsEndpoint).getOrNull()
    }

    private companion object {
        const val PLAYLIST_TRACK_CAP = 300
    }
}

/**
 * A playlist's track count, when the source actually gives one.
 *
 * `songCountText` is just the last run of a search result's subtitle line, which is a track count
 * for some playlists and a running time for others. Stripping non-digits from it turned
 * "2 hours, 7 minutes" into "27" and displayed it as a song count - so an 18-track playlist
 * advertised 27. Only a leading number followed by a song/track word is a count; anything else is
 * something other than one and is better shown as nothing than as a wrong number.
 */
internal fun String?.asSongCount(): Int? {
    val text = this ?: return null
    val match = Regex("""^\s*([\d,]+)\s*(song|track)""", RegexOption.IGNORE_CASE).find(text)
    return match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
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
    albumId = album?.id,
    artistId = artists.firstOrNull()?.id,
)
