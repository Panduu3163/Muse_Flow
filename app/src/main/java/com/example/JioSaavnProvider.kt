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

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Api.USER_AGENT)
            .build()

        val bodyString = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw JioSaavnException("Search failed: HTTP ${response.code}")
            }
            response.body?.string() ?: throw JioSaavnException("Empty search response")
        }

        val root = JSONObject(bodyString)
        val results = root.optJSONArray("results") ?: JSONArray()
        (0 until results.length()).mapNotNull { i -> parseSong(results.optJSONObject(i)) }
    }

    /**
     * JioSaavn's stream URL is already decrypted locally during [search] (it's embedded in the
     * search result, not fetched separately), so this is just a lookup - no network call.
     */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? =
        item.directStreamUrl?.let { StreamResolution(url = it) }

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

        val encryptedMediaUrl = moreInfo?.optString("encrypted_media_url")?.takeIf { it.isNotBlank() }
        val streamUrl = encryptedMediaUrl?.let { decryptMediaUrl(it) }

        return TrackResult(
            id = id,
            title = title,
            artist = artist,
            duration = duration,
            source = name,
            directStreamUrl = streamUrl
        )
    }

    /** Decrypts JioSaavn's DES-ECB-encrypted media URL, then upgrades it to 160kbps quality. */
    private fun decryptMediaUrl(encryptedMediaUrl: String): String? = try {
        val encryptedBytes = Base64.decode(encryptedMediaUrl, Base64.DEFAULT)
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, desKey)
        val decrypted = String(cipher.doFinal(encryptedBytes), Charsets.ISO_8859_1)
        decrypted.replace("_96.mp4", "_160.mp4")
    } catch (e: Exception) {
        null
    }

    private fun decodeHtmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
