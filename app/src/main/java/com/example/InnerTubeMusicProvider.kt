package com.example

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    override suspend fun search(query: String): List<TrackResult> = searchSongsAndVideos(query).items

    suspend fun searchGeneralVideos(query: String): List<TrackResult> =
        com.music.innertube.NewPipeExtractor.searchGeneralVideos(query).map { it.toTrackResult() }

    /** The "everything" search page - see [SearchShelf]'s own doc. [YouTube.searchSummary] parses
     * YouTube Music's actual default search response (no filter applied), which already carries
     * its own real popularity-driven ordering and shelf grouping - not something reconstructed
     * from separate per-type searches. A shelf that ends up with nothing renderable (every item
     * was some fifth YTItem kind this app doesn't have a row for) is dropped rather than shown
     * empty. */
    suspend fun searchSummary(query: String): List<SearchShelf> =
        YouTube.searchSummary(query)
            .getOrThrow()
            .summaries
            .mapNotNull { summary ->
                var items = summary.items.mapNotNull { it.toSearchResultItem() }
                // The Top result card is meant to be the single best song/video match, not a
                // grab-bag - an artist/album/playlist card occasionally rides along in the same
                // shelf (YouTube sometimes attaches one as a secondary suggestion), which isn't
                // what "Top results" should show; those kinds already get their own shelves below.
                if (summary.title.contains("top result", ignoreCase = true)) {
                    items = items.filterIsInstance<SearchResultItem.Song>()
                }
                if (items.isEmpty()) null else SearchShelf(summary.title, items)
            }

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

    /** First page of a songs search, paired with a continuation token - the pair
     * [searchTracksContinuation] needs to keep paging past it. Distinct from [search] (which
     * discards the continuation) because most callers just want a one-shot list; only the Search
     * screen's infinite scroll needs to keep going. */
    suspend fun searchTracksPage(query: String): TrackPage {
        val result = searchSongsAndVideos(query)
        return TrackPage(
            items = result.items,
            continuation = result.continuation,
        )
    }

    /**
     * YouTube Music exposes songs and videos as separate search filters. The resolver already
     * accepts either kind because both carry an ordinary YouTube video id, but the old search only
     * requested FILTER_SONG, making covers, live performances and video-only uploads impossible
     * to discover. Fetch both first pages together, keep song ranking first, and de-duplicate by
     * video id. Both feeds keep their own continuation tokens so later video-only recordings
     * remain discoverable too.
     */
    private suspend fun searchSongsAndVideos(query: String): TrackPage = coroutineScope {
        val songs = async { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG) }
        val videos = async { YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO) }
        val generalVideos = async { runCatching { com.music.innertube.NewPipeExtractor.searchGeneralVideos(query) } }
        val songResult = songs.await()
        val videoResult = videos.await()
        val generalResult = generalVideos.await()
        if (songResult.isFailure && videoResult.isFailure && generalResult.isFailure) songResult.getOrThrow()
        val songPage = songResult.getOrNull()
        val videoPage = videoResult.getOrNull()
        val musicItems = (songPage?.items.orEmpty() + videoPage?.items.orEmpty()).filterIsInstance<SongItem>()
        val generalItems = generalResult.getOrNull().orEmpty()
        TrackPage(
            items = interleaveTrackResults(musicItems.map { it.toTrackResult() },
                generalItems.map { it.toTrackResult() }),
            continuation = combinedContinuation(songPage?.continuation, videoPage?.continuation),
        )
    }

    /** Both feeds are paginated; video-only recordings must not disappear after page one. */
    suspend fun searchTracksContinuation(continuation: String): TrackPage = coroutineScope {
        val tokens = if (continuation.startsWith("museflow:")) {
            org.json.JSONObject(continuation.removePrefix("museflow:"))
        } else org.json.JSONObject().put("songs", continuation)
        val songToken = tokens.optString("songs").takeIf { it.isNotBlank() }
        val videoToken = tokens.optString("videos").takeIf { it.isNotBlank() }
        val songs = async { songToken?.let { YouTube.searchContinuation(it).getOrThrow() } }
        val videos = async { videoToken?.let { YouTube.searchContinuation(it).getOrThrow() } }
        val songPage = songs.await()
        val videoPage = videos.await()
        TrackPage(
            items = (songPage?.items.orEmpty() + videoPage?.items.orEmpty())
                .filterIsInstance<SongItem>().distinctBy { it.id }.map { it.toTrackResult() },
            continuation = combinedContinuation(songPage?.continuation, videoPage?.continuation),
        )
    }

    private fun combinedContinuation(songs: String?, videos: String?): String? =
        if (songs == null && videos == null) null else "museflow:" +
            org.json.JSONObject().apply { songs?.let { put("songs", it) }; videos?.let { put("videos", it) } }

    /** A page of [videoId]'s radio mix, with a continuation token so a "recommended for you" feed
     * can keep extending it - unlike [getRadioTracks], which returns only the first batch. */
    suspend fun getRadioTracksPage(videoId: String, continuation: String? = null): TrackPage {
        val result = YouTube.next(WatchEndpoint(videoId = videoId), continuation = continuation).getOrThrow()
        return TrackPage(
            items = result.items.distinctBy { it.id }.map { it.toTrackResult() },
            continuation = result.continuation,
        )
    }

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

internal fun interleaveTrackResults(music: List<TrackResult>, videos: List<TrackResult>): List<TrackResult> =
    buildList {
        for (index in 0 until maxOf(music.size, videos.size)) {
            music.getOrNull(index)?.let(::add)
            videos.getOrNull(index)?.let(::add)
        }
    }.distinctBy { it.id }

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

/** One [com.music.innertube.models.YTItem] as whichever concrete [SearchResultItem] its runtime
 * type maps to - the sealed-class equivalent of [searchAlbums]/[searchArtists]/[searchPlaylists]'s
 * own per-type mapping, reused here instead of duplicated since a shelf mixes kinds. */
private fun com.music.innertube.models.YTItem.toSearchResultItem(): SearchResultItem? = when (this) {
    is SongItem -> SearchResultItem.Song(toTrackResult(), isVideo = isVideoSong)
    is AlbumItem -> SearchResultItem.AlbumRow(
        AlbumResult(
            id = browseId,
            title = title,
            artist = artists?.joinToString(", ") { it.name }.orEmpty(),
            imageUrl = thumbnail,
            songCount = null,
            sourceType = MusicSource.YOUTUBE_MUSIC,
        )
    )
    is ArtistItem -> SearchResultItem.ArtistRow(
        ArtistResult(
            id = id,
            name = title,
            imageUrl = thumbnail,
            sourceType = MusicSource.YOUTUBE_MUSIC,
        )
    )
    is PlaylistItem -> SearchResultItem.PlaylistRow(
        PlaylistResult(
            id = id,
            title = title,
            // Creator name + whatever trailing metadata this playlist actually carries - often a
            // song count, but search results for a well-known/community playlist frequently carry
            // a view/listen count string instead; both are just author.name's neighbour text, so
            // it's shown as-is rather than assumed to always be a song count (that assumption is
            // what songCountText.asSongCount() below is *for* - it returns null rather than a wrong
            // number when the text isn't actually a count of songs).
            subtitle = listOfNotNull(author?.name, songCountText).joinToString(" · "),
            imageUrl = thumbnail,
            songCount = songCountText.asSongCount(),
            sourceType = MusicSource.YOUTUBE_MUSIC,
        )
    )
}

/** InnerTube reports duration in whole seconds; MuseFlow's UI wants "m:ss". */
private fun SongItem.toTrackResult(): TrackResult = TrackResult(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    duration = duration?.let { total -> "${total / 60}:${(total % 60).toString().padStart(2, '0')}" },
    source = album?.name ?: if (musicVideoType == "GENERAL_YOUTUBE_VIDEO") "YouTube video" else "YouTube Music",
    sourceType = MusicSource.YOUTUBE_MUSIC,
    // Null on purpose: a YouTube result is only playable after the cipher/PoToken resolve step.
    directStreamUrl = null,
    imageUrl = thumbnail,
    albumId = album?.id,
    artistId = artists.firstOrNull()?.id,
    artistCredits = artists.map { ArtistCredit(it.name, it.id) },
)
