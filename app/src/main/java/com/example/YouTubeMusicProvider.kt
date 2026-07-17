package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks directly to YouTube Music's private "InnerTube" API - the same undocumented endpoints
 * the official web/mobile clients use, reverse-engineered by the open-source community (see
 * Metrolist: https://github.com/MetrolistGroup/Metrolist). There is no public API for this.
 *
 * Search uses the WEB_REMIX pretend "client" (matches music.youtube.com's own client, gets clean
 * song results). Stream resolution ([getStreamUrl]) also uses WEB_REMIX now, via
 * [YouTubeStreamResolver] - the fully authenticated (real `visitorData` + BotGuard PoToken +
 * signature/n-parameter deciphering) pipeline validated in isolation and now wired in for real.
 * WEB_REMIX requires all of that specifically because it's the client whose formats carry a
 * `signatureCipher` instead of a direct URL - unlike ANDROID_VR/IOS (this provider's previous
 * approach), which sidestepped needing a cipher/PoToken at all but is more prone to being blocked
 * and returns lower/inconsistent quality.
 *
 * This is a from-scratch reimplementation, not copied code, informed by reading Metrolist's
 * (AGPL-3.0) `innertube` module for the request/response shapes and client behavior.
 */
class YouTubeMusicProvider(context: Context) : Provider<TrackResult> {

    private val appContext = context.applicationContext

    override val name = "YouTube Music"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private object Client {
        const val ORIGIN = "https://music.youtube.com"
        const val API_BASE = "$ORIGIN/youtubei/v1/"

        // WEB_REMIX: music.youtube.com's own web client. Used for both search and stream
        // resolution.
        const val WEB_REMIX_NAME = "WEB_REMIX"
        const val WEB_REMIX_VERSION = "1.20260114.03.00"
        const val WEB_REMIX_CLIENT_ID = "67"
        const val WEB_REMIX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        // "Song" search filter chip (opaque protobuf token YouTube Music uses internally).
        const val SEARCH_FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    }

    // Categories seen in an *unfiltered* search's flat result list - the plain-text label each
    // musicResponsiveListItemRenderer carries as the first run of its subtitle. Deliberately not
    // using YouTube Music's per-category filter chips (as [search] does for songs, via
    // [Client.SEARCH_FILTER_SONG]): those are opaque protobuf blobs with no public spec, and
    // getting an album/artist/playlist one byte wrong would silently return nothing. Bucketing an
    // unfiltered search's results by this visible label is slower (one request covers every
    // category, so albums/artists/playlists each redo the same request) but verified against the
    // real API and immune to guessing a chip wrong.
    private object Category {
        const val ALBUM = "Album"
        const val SINGLE = "Single"
        const val ARTIST = "Artist"
        const val PLAYLIST = "Playlist"
    }

    class YouTubeMusicException(message: String) : Exception(message)

    /** Searches YouTube Music for songs matching [query]. Runs on [Dispatchers.IO]. */
    override suspend fun search(query: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("context", buildContext(Client.WEB_REMIX_NAME, Client.WEB_REMIX_VERSION))
            put("query", query)
            put("params", Client.SEARCH_FILTER_SONG)
        }

        val responseJson = postJson(
            path = "search",
            body = requestBody,
            clientId = Client.WEB_REMIX_CLIENT_ID,
            clientVersion = Client.WEB_REMIX_VERSION,
            userAgent = Client.WEB_REMIX_USER_AGENT
        )

        parseSearchResults(responseJson).map {
            TrackResult(
                id = it.videoId,
                title = it.title,
                artist = it.artist,
                duration = it.duration,
                source = name,
                sourceType = MusicSource.YOUTUBE_MUSIC,
                imageUrl = it.thumbnailUrl
            )
        }
    }

    /**
     * Resolves [item] to a directly-playable stream URL via the real, authenticated WEB_REMIX
     * pipeline - see [YouTubeStreamResolver] for the full visitorData/PoToken/cipher/n-transform
     * chain. **Never cache this result** - confirmed by testing, a resolved URL 403s within
     * minutes (the PoToken-bound freshness window). Callers must call this fresh immediately
     * before every actual playback attempt, never reuse an earlier resolution.
     */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? =
        YouTubeStreamResolver.resolve(appContext, item.id)?.let { StreamResolution(url = it) }

    /** Searches YouTube Music for albums (and singles, which browse identically to a one-track
     * album) matching [query]. Runs on [Dispatchers.IO]. */
    suspend fun searchAlbums(query: String): List<AlbumResult> = withContext(Dispatchers.IO) {
        searchAllCategories(query)
            .filter { categoryOf(it) in setOf(Category.ALBUM, Category.SINGLE) }
            .mapNotNull { parseAlbumRenderer(it) }
    }

    /** Searches YouTube Music for artists matching [query]. Runs on [Dispatchers.IO]. */
    suspend fun searchArtists(query: String): List<ArtistResult> = withContext(Dispatchers.IO) {
        searchAllCategories(query)
            .filter { categoryOf(it) == Category.ARTIST }
            .mapNotNull { parseArtistRenderer(it) }
    }

    /** Searches YouTube Music for playlists matching [query]. Runs on [Dispatchers.IO]. */
    suspend fun searchPlaylists(query: String): List<PlaylistResult> = withContext(Dispatchers.IO) {
        searchAllCategories(query)
            .filter { categoryOf(it) == Category.PLAYLIST }
            .mapNotNull { parsePlaylistRenderer(it) }
    }

    /** Fetches an album's full tracklist via a `browse` call on [albumId] (the
     * `MPREb_...`-style browseId from [searchAlbums]'s [AlbumResult.id]). Track rows on an album
     * page don't repeat the (shared) artist per-row, so the header's own artist credit is used as
     * a fallback for any track that comes back without one. */
    suspend fun getAlbumTracks(albumId: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val root = browse(albumId)
        val header = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveHeaderRenderer")
        val albumArtist = header
            ?.optJSONObject("straplineTextOne")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }

        val shelfItems = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.optJSONObject(0)
            ?.optJSONObject("musicShelfRenderer")
            ?.optJSONArray("contents")
            ?: JSONArray()

        parseTrackShelfItems(shelfItems, fallbackArtist = albumArtist)
    }

    /** Fetches an artist's "Top songs" shelf via a `browse` call on [artistId] (the
     * `UC...`-style channel-id browseId from [searchArtists]'s [ArtistResult.id]) - the closest
     * this API offers to a flat discography, same limitation [JioSaavnProvider.getArtistTracks]
     * has. */
    suspend fun getArtistTracks(artistId: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val root = browse(artistId)
        val shelfItems = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.optJSONObject(0)
            ?.optJSONObject("musicShelfRenderer")
            ?.optJSONArray("contents")
            ?: JSONArray()
        parseTrackShelfItems(shelfItems)
    }

    /** Fetches a playlist's full tracklist via a `browse` call on [playlistId] (the
     * `VL...`-style browseId from [searchPlaylists]'s [PlaylistResult.id]). */
    suspend fun getPlaylistTracks(playlistId: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val root = browse(playlistId)
        val shelfItems = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.optJSONObject(0)
            ?.optJSONObject("musicPlaylistShelfRenderer")
            ?.optJSONArray("contents")
            ?: JSONArray()
        parseTrackShelfItems(shelfItems)
    }

    private fun buildContext(clientName: String, clientVersion: String): JSONObject {
        val clientJson = JSONObject().apply {
            put("clientName", clientName)
            put("clientVersion", clientVersion)
            put("gl", "US")
            put("hl", "en")
        }
        return JSONObject().apply {
            put("client", clientJson)
            put("request", JSONObject().apply {
                put("internalExperimentFlags", JSONArray())
                put("useSsl", true)
            })
            put("user", JSONObject().apply {
                put("lockedSafetyMode", false)
            })
        }
    }

    private fun postJson(
        path: String,
        body: JSONObject,
        clientId: String,
        clientVersion: String,
        userAgent: String
    ): JSONObject {
        val request = Request.Builder()
            .url("${Client.API_BASE}$path?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", clientId) // yes, the client *id*, not the name
            .header("X-YouTube-Client-Version", clientVersion)
            .header("X-Origin", Client.ORIGIN)
            .header("Referer", "${Client.ORIGIN}/")
            .header("User-Agent", userAgent)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string()
                ?: throw YouTubeMusicException("Empty response from YouTube Music (HTTP ${response.code})")
            if (!response.isSuccessful) {
                throw YouTubeMusicException("YouTube Music request failed: HTTP ${response.code} - $bodyString")
            }
            return JSONObject(bodyString)
        }
    }

    /**
     * Search responses are a deeply-nested, mostly-optional renderer tree that changes shape
     * often. Traversing it defensively with org.json (rather than strict typed models) is more
     * resilient to the odd missing/renamed field.
     */
    private fun parseSearchResults(root: JSONObject): List<YtSearchResult> {
        val results = mutableListOf<YtSearchResult>()

        val sections = root
            .optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return results

        for (i in 0 until sections.length()) {
            val shelfItems = sections.optJSONObject(i)
                ?.optJSONObject("musicShelfRenderer")
                ?.optJSONArray("contents")
                ?: continue

            for (j in 0 until shelfItems.length()) {
                val renderer = shelfItems.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer")
                    ?: continue
                parseSongRenderer(renderer)?.let { results.add(it) }
            }
        }

        return results
    }

    /** A [renderer]'s flex column [index] text runs (song title, subtitle credits/metadata, ...) -
     * shared by every `musicResponsiveListItemRenderer` this provider parses, whether it came
     * from a song/album/artist/playlist search result or an album/artist/playlist browse page. */
    private fun flexColumnRuns(renderer: JSONObject, index: Int): JSONArray? = renderer
        .optJSONArray("flexColumns")
        ?.optJSONObject(index)
        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        ?.optJSONObject("text")
        ?.optJSONArray("runs")

    private fun flexColumnFirstText(renderer: JSONObject, index: Int): String? =
        flexColumnRuns(renderer, index)?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }

    /** A renderer's own browseId, when it has one (albums/artists/playlists all navigate via one;
     * plain songs don't). */
    private fun browseIdOf(renderer: JSONObject): String? = renderer
        .optJSONObject("navigationEndpoint")
        ?.optJSONObject("browseEndpoint")
        ?.optString("browseId")
        ?.takeIf { it.isNotBlank() }

    /** The visible category label an *unfiltered* search result carries as the first run of its
     * subtitle column - "Song", "Album", "Single", "Artist", "Playlist", "Video", ... - see
     * [Category] and [searchAllCategories]. */
    private fun categoryOf(renderer: JSONObject): String? = flexColumnRuns(renderer, 1)?.optJSONObject(0)?.optString("text")

    private fun extractThumbnailUrl(renderer: JSONObject): String? = renderer
        .optJSONObject("thumbnail")
        ?.optJSONObject("musicThumbnailRenderer")
        ?.optJSONObject("thumbnail")
        ?.optJSONArray("thumbnails")
        ?.let { thumbnails ->
            // Thumbnails are listed smallest-first; the last entry is the highest-res one
            // YouTube offered, and we upgrade its size params further below.
            (0 until thumbnails.length()).mapNotNull { thumbnails.optJSONObject(it)?.optString("url") }
                .lastOrNull { it.isNotBlank() }
        }
        ?.let { upgradeThumbnailUrl(it) }

    /** YouTube's thumbnail URLs (typically googleusercontent.com) encode the requested size in
     * the URL itself (e.g. "=w120-h120-l90-rj"); search results default to a small size meant
     * for a list row, so bump it up for sharper Now Playing/notification art. URLs without that
     * size-param pattern (e.g. a plain i.ytimg.com path) are passed through unchanged. */
    private fun upgradeThumbnailUrl(rawUrl: String): String =
        rawUrl.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")

    /** Parses one song row, from either a filtered song search result or an album/artist/playlist
     * browse page's tracklist shelf - both use the same `musicResponsiveListItemRenderer` shape.
     * [fallbackArtist] covers album tracklists, whose rows omit a per-track artist (it's the
     * album's own, shown once in the page header) - see [getAlbumTracks]. */
    private fun parseSongRenderer(renderer: JSONObject, fallbackArtist: String? = null): YtSearchResult? {
        val titleRuns = flexColumnRuns(renderer, 0) ?: return null
        val title = titleRuns.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() } ?: return null

        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: titleRuns.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitleRuns = flexColumnRuns(renderer, 1)
        var artist: String? = null
        var duration: String? = null
        val durationPattern = Regex("""^\d{1,2}:\d{2}$""")
        if (subtitleRuns != null) {
            for (k in 0 until subtitleRuns.length()) {
                val text = subtitleRuns.optJSONObject(k)?.optString("text")?.trim() ?: continue
                if (text.isBlank() || text == "•") continue
                if (durationPattern.matches(text)) {
                    duration = text
                } else if (artist == null) {
                    artist = text
                }
            }
        }
        // Album/playlist tracklist rows (unlike search results) carry duration in a separate
        // fixed column instead of the subtitle - only consulted if the subtitle didn't have one.
        if (duration == null) {
            duration = renderer.optJSONArray("fixedColumns")
                ?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?.takeIf { durationPattern.matches(it) }
        }

        return YtSearchResult(
            videoId = videoId,
            title = title,
            artist = artist ?: fallbackArtist ?: "Unknown artist",
            duration = duration,
            thumbnailUrl = extractThumbnailUrl(renderer)
        )
    }

    /** Every `musicResponsiveListItemRenderer` in [shelfItems] (an album/artist/playlist browse
     * page's tracklist shelf), parsed and mapped to a real [TrackResult]. */
    private fun parseTrackShelfItems(shelfItems: JSONArray, fallbackArtist: String? = null): List<TrackResult> {
        val results = mutableListOf<TrackResult>()
        for (i in 0 until shelfItems.length()) {
            val renderer = shelfItems.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            val parsed = parseSongRenderer(renderer, fallbackArtist) ?: continue
            results.add(
                TrackResult(
                    id = parsed.videoId,
                    title = parsed.title,
                    artist = parsed.artist,
                    duration = parsed.duration,
                    source = name,
                    sourceType = MusicSource.YOUTUBE_MUSIC,
                    imageUrl = parsed.thumbnailUrl
                )
            )
        }
        return results
    }

    /** Runs an *unfiltered* search (no category filter chip - see [Category]) and flattens every
     * result row into one list, regardless of which category shelf it came from. [searchAlbums],
     * [searchArtists], and [searchPlaylists] each bucket this same flat list by [categoryOf]. */
    private fun searchAllCategories(query: String): List<JSONObject> {
        val requestBody = JSONObject().apply {
            put("context", buildContext(Client.WEB_REMIX_NAME, Client.WEB_REMIX_VERSION))
            put("query", query)
        }
        val responseJson = postJson(
            path = "search",
            body = requestBody,
            clientId = Client.WEB_REMIX_CLIENT_ID,
            clientVersion = Client.WEB_REMIX_VERSION,
            userAgent = Client.WEB_REMIX_USER_AGENT
        )

        val renderers = mutableListOf<JSONObject>()
        val sections = responseJson
            .optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return renderers

        // Unlike a filtered search's musicShelfRenderer-per-category shape, an unfiltered
        // search's sections are itemSectionRenderers each wrapping exactly one row - every
        // category is already flattened into one big list by YouTube Music itself.
        for (i in 0 until sections.length()) {
            val itemSectionContents = sections.optJSONObject(i)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")
                ?: continue
            for (j in 0 until itemSectionContents.length()) {
                itemSectionContents.optJSONObject(j)
                    ?.optJSONObject("musicResponsiveListItemRenderer")
                    ?.let { renderers.add(it) }
            }
        }
        return renderers
    }

    private fun parseAlbumRenderer(renderer: JSONObject): AlbumResult? {
        val browseId = browseIdOf(renderer) ?: return null
        val title = flexColumnFirstText(renderer, 0) ?: return null
        // Subtitle runs look like ["Album", " • ", "Coldplay", " • ", "2000"] - the first
        // non-separator run after the category label itself is the artist.
        val artist = flexColumnRuns(renderer, 1)
            ?.let { runs -> (1 until runs.length()).firstNotNullOfOrNull { runs.optJSONObject(it)?.optString("text")?.takeIf { t -> t.isNotBlank() && t != "•" } } }
            ?: "Unknown artist"
        return AlbumResult(
            id = browseId,
            title = title,
            artist = artist,
            imageUrl = extractThumbnailUrl(renderer),
            songCount = null,
            sourceType = MusicSource.YOUTUBE_MUSIC
        )
    }

    private fun parseArtistRenderer(renderer: JSONObject): ArtistResult? {
        val browseId = browseIdOf(renderer) ?: return null
        val name = flexColumnFirstText(renderer, 0) ?: return null
        return ArtistResult(
            id = browseId,
            name = name,
            imageUrl = extractThumbnailUrl(renderer),
            sourceType = MusicSource.YOUTUBE_MUSIC
        )
    }

    private fun parsePlaylistRenderer(renderer: JSONObject): PlaylistResult? {
        val browseId = browseIdOf(renderer) ?: return null
        val title = flexColumnFirstText(renderer, 0) ?: return null
        // Subtitle runs look like ["Playlist", " • ", "<owner>", " • ", "<N> songs"] for a
        // YouTube Music-curated playlist, or ["Playlist", " • ", "<owner>", " • ", "<N> views"]
        // for a user one - song count isn't always present, so it's left null rather than guessed.
        val subtitleParts = flexColumnRuns(renderer, 1)
            ?.let { runs -> (1 until runs.length()).mapNotNull { runs.optJSONObject(it)?.optString("text")?.trim() } }
            ?.filter { it.isNotBlank() && it != "•" }
            ?: emptyList()
        val owner = subtitleParts.getOrNull(0) ?: ""
        val songCount = subtitleParts.getOrNull(1)
            ?.let { Regex("""(\d+)\s*songs?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return PlaylistResult(
            id = browseId,
            title = title,
            subtitle = owner,
            imageUrl = extractThumbnailUrl(renderer),
            songCount = songCount,
            sourceType = MusicSource.YOUTUBE_MUSIC
        )
    }

    private fun browse(browseId: String): JSONObject {
        val requestBody = JSONObject().apply {
            put("context", buildContext(Client.WEB_REMIX_NAME, Client.WEB_REMIX_VERSION))
            put("browseId", browseId)
        }
        return postJson(
            path = "browse",
            body = requestBody,
            clientId = Client.WEB_REMIX_CLIENT_ID,
            clientVersion = Client.WEB_REMIX_VERSION,
            userAgent = Client.WEB_REMIX_USER_AGENT
        )
    }
}
