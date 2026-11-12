package com.example

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import org.json.JSONObject

/** Native TTML integration using Better Lyrics' documented public endpoint. */
class BetterLyricsProvider {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int? = null, videoId: String? = null): LyricsResult {
        return try {
            val url = requestUrl(title, artist, durationSeconds, videoId)
            val json = httpClient.fetchLyricsJson(url) ?: return LyricsResult.NotFound
            decodeResponse(json)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LyricsResult.Error(error.message ?: "Better Lyrics is unavailable")
        }
    }

    companion object {
        internal fun requestUrl(title: String, artist: String, durationSeconds: Int?, videoId: String?): String = buildString {
            fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
            append("https://api.betterlyrics.org/getLyrics?s=${encode(title)}&a=${encode(artist)}")
            if (durationSeconds != null && durationSeconds > 0) append("&d=$durationSeconds")
            if (videoId?.looksLikeYouTubeVideoId() == true) append("&videoId=${encode(videoId)}")
        }

        internal fun decodeResponse(json: JSONObject): LyricsResult {
            val ttml = json.optString("ttml").takeIf { it.isNotBlank() }
                ?: return LyricsResult.Error("Better Lyrics returned no TTML document")
            val lines = parseTtml(ttml)
            return if (lines.isEmpty()) LyricsResult.Error("Better Lyrics returned an unreadable timing document")
                else LyricsResult.Synced(lines)
        }

        fun parseTtml(ttml: String): List<LyricLine> = parseTtmlDocument(ttml)
    }
}
