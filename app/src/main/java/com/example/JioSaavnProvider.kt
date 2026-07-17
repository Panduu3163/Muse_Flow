package com.example

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Talks to JioSaavn's public (undocumented but widely used) search API. Unlike YouTube Music,
 * there's no PoToken/signature deciphering involved: search results embed a DES-encrypted media
 * URL directly, and decrypting it (a fixed key JioSaavn has used for years, per numerous
 * open-source JioSaavn clients - this implementation cross-checked against
 * https://github.com/sumitkolhe/jiosaavn-api's `link.helper.ts`) yields a directly playable CDN
 * URL. No separate "resolve stream" network call is needed, unlike YouTube.
 */
class JioSaavnProvider : Provider<TrackResult> {

    override val name = "JioSaavn"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val BASE_URL = "https://www.jiosaavn.com/api.php"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    // Fixed DES key JioSaavn has used to obfuscate its media URLs for years; ECB mode needs no IV.
    private val desKey = SecretKeySpec("38346591".toByteArray(Charsets.ISO_8859_1), "DES")

    class JioSaavnException(message: String) : Exception(message)

    override suspend fun search(query: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${Api.BASE_URL}?__call=search.getResults&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&q=$encodedQuery&p=1&n=20"
        val results = fetchJson(url).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i -> parseSong(results.optJSONObject(i)) }
    }

    /** Searches JioSaavn's album catalog, via the same private search API [search] uses. */
    suspend fun searchAlbums(query: String): List<AlbumResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${Api.BASE_URL}?__call=search.getAlbumResults&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&q=$encodedQuery&p=1&n=20"
        val results = fetchJson(url).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i -> parseAlbum(results.optJSONObject(i)) }
    }

    /** Searches JioSaavn's artist catalog, via the same private search API [search] uses. */
    suspend fun searchArtists(query: String): List<ArtistResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${Api.BASE_URL}?__call=search.getArtistResults&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&q=$encodedQuery&p=1&n=20"
        val results = fetchJson(url).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i -> parseArtist(results.optJSONObject(i)) }
    }

    /** Searches JioSaavn's playlist catalog, via the same private search API [search] uses. */
    suspend fun searchPlaylists(query: String): List<PlaylistResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${Api.BASE_URL}?__call=search.getPlaylistResults&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&q=$encodedQuery&p=1&n=20"
        val results = fetchJson(url).optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i -> parsePlaylist(results.optJSONObject(i)) }
    }

    /** Fetches an album's full tracklist, for [AlbumDetailScreen]. */
    suspend fun getAlbumTracks(albumId: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val url = "${Api.BASE_URL}?__call=content.getAlbumDetails&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&albumid=$albumId"
        val list = fetchJson(url).optJSONArray("list") ?: JSONArray()
        (0 until list.length()).mapNotNull { i -> parseSong(list.optJSONObject(i)) }
    }

    /** Fetches a playlist's full tracklist, for [PlaylistDetailScreen]. */
    suspend fun getPlaylistTracks(playlistId: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val url = "${Api.BASE_URL}?__call=playlist.getDetails&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&listid=$playlistId"
        val list = fetchJson(url).optJSONArray("list") ?: JSONArray()
        (0 until list.length()).mapNotNull { i -> parseSong(list.optJSONObject(i)) }
    }

    /** Fetches an artist's top tracks, for [ArtistDetailScreen]. There's no "full discography as
     * a flat tracklist" endpoint - top tracks is the closest JioSaavn's private API offers. */
    suspend fun getArtistTracks(artistId: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val url = "${Api.BASE_URL}?__call=artist.getArtistPageDetails&_format=json&_marker=0" +
            "&api_version=4&ctx=web6dot0&artistId=$artistId&n_song=50&n_album=0&page=1&category=&sort_order="
        val list = fetchJson(url).optJSONArray("topSongs") ?: JSONArray()
        (0 until list.length()).mapNotNull { i -> parseSong(list.optJSONObject(i)) }
    }

    /**
     * JioSaavn's stream URL is already decrypted locally during [search] (it's embedded in the
     * search result, not fetched separately), so this is just a lookup - no network call.
     */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? =
        item.directStreamUrl?.let { StreamResolution(url = it) }

    private fun fetchJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Api.USER_AGENT)
            .build()

        val bodyString = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw JioSaavnException("Request failed: HTTP ${response.code}")
            }
            response.body?.string() ?: throw JioSaavnException("Empty response")
        }
        return JSONObject(bodyString)
    }

    private fun parseSong(song: JSONObject?): TrackResult? {
        song ?: return null
        val id = song.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = decodeHtmlEntities(song.optString("title")).takeIf { it.isNotBlank() } ?: return null
        val moreInfo = song.optJSONObject("more_info")

        val primaryArtists = moreInfo
            ?.optJSONObject("artistMap")
            ?.optJSONArray("primary_artists")
        val artistNames = primaryArtists?.let { artists ->
            (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
        }?.filter { it.isNotBlank() }

        val artist = artistNames?.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: decodeHtmlEntities(song.optString("subtitle")).takeIf { it.isNotBlank() }
            ?: "Unknown artist"

        val durationSeconds = moreInfo?.optString("duration")?.toIntOrNull()
        val duration = durationSeconds?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }

        // JioSaavn only serves 320kbps to tracks that support it (this flag), and the URL you get
        // back from decryption always defaults to 96kbps regardless - so picking the true highest
        // quality means checking this rather than always upgrading to a fixed bitrate.
        val has320kbps = moreInfo?.optString("320kbps").equals("true", ignoreCase = true)
        val encryptedMediaUrl = moreInfo?.optString("encrypted_media_url")?.takeIf { it.isNotBlank() }
        val streamUrl = encryptedMediaUrl?.let { decryptMediaUrl(it, has320kbps) }

        // JioSaavn's search results embed a low-res thumbnail URL (e.g. "...50x50.jpg");
        // every quality JioSaavn actually serves lives at the same path, so upgrading the size
        // segment gets a much sharper image for the notification/Now Playing art with no extra call.
        val imageUrl = upgradeImageUrl(song.optString("image"))

        return TrackResult(
            id = id,
            title = title,
            artist = artist,
            duration = duration,
            source = name,
            sourceType = MusicSource.JIOSAAVN,
            directStreamUrl = streamUrl,
            imageUrl = imageUrl
        )
    }

    /** Upgrades a JioSaavn thumbnail URL (e.g. "...50x50.jpg") to the much sharper 500x500
     * version served at the same path - shared by every result type's image field. */
    private fun upgradeImageUrl(rawImageUrl: String): String? =
        rawImageUrl.takeIf { it.isNotBlank() }
            ?.let { decodeHtmlEntities(it) }
            ?.replace(Regex("50x50|150x150"), "500x500")

    private fun parseAlbum(json: JSONObject?): AlbumResult? {
        json ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = decodeHtmlEntities(json.optString("title")).takeIf { it.isNotBlank() } ?: return null
        val moreInfo = json.optJSONObject("more_info")
        val artist = moreInfo?.optString("music")?.takeIf { it.isNotBlank() }?.let { decodeHtmlEntities(it) }
            ?: decodeHtmlEntities(json.optString("subtitle")).takeIf { it.isNotBlank() }
            ?: "Unknown artist"
        val songCount = moreInfo?.optString("song_count")?.toIntOrNull()
        return AlbumResult(
            id = id,
            title = title,
            artist = artist,
            imageUrl = upgradeImageUrl(json.optString("image")),
            songCount = songCount
        )
    }

    private fun parseArtist(json: JSONObject?): ArtistResult? {
        json ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = decodeHtmlEntities(json.optString("name")).takeIf { it.isNotBlank() } ?: return null
        // JioSaavn falls back to a generic "artist-default-*.png" placeholder when it has no
        // real photo - filtering those out lets the UI fall back to its own gradient+initial
        // avatar instead of showing JioSaavn's placeholder image.
        val rawImage = json.optString("image").takeIf { it.isNotBlank() && !it.contains("default") }
        return ArtistResult(id = id, name = name, imageUrl = rawImage?.let { upgradeImageUrl(it) })
    }

    private fun parsePlaylist(json: JSONObject?): PlaylistResult? {
        json ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = decodeHtmlEntities(json.optString("title")).takeIf { it.isNotBlank() } ?: return null
        val songCount = json.optJSONObject("more_info")?.optString("song_count")?.toIntOrNull()
        val subtitle = songCount?.let { "$it songs" }
            ?: decodeHtmlEntities(json.optString("subtitle")).takeIf { it.isNotBlank() }
            ?: ""
        return PlaylistResult(
            id = id,
            title = title,
            subtitle = subtitle,
            imageUrl = upgradeImageUrl(json.optString("image")),
            songCount = songCount
        )
    }

    /**
     * Decrypts JioSaavn's DES-ECB-encrypted media URL, then upgrades it to the highest quality
     * actually available for this track: 320kbps when [has320kbps] is set, otherwise 160kbps
     * (which - unlike 320 - is available for effectively every track, premium or not). The
     * decrypted URL always defaults to a "_96.mp4" suffix; [bitrateSuffixRegex] replaces whatever
     * bitrate is actually embedded there, rather than assuming it's always exactly "_96".
     */
    private fun decryptMediaUrl(encryptedMediaUrl: String, has320kbps: Boolean): String? = try {
        val encryptedBytes = Base64.decode(encryptedMediaUrl, Base64.DEFAULT)
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, desKey)
        val decrypted = String(cipher.doFinal(encryptedBytes), Charsets.ISO_8859_1)
        val targetBitrate = if (has320kbps) "320" else "160"
        decrypted.replace(bitrateSuffixRegex, "_$targetBitrate.mp4")
    } catch (e: Exception) {
        null
    }

    private val bitrateSuffixRegex = Regex("_\\d+\\.mp4$")

    private fun decodeHtmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
