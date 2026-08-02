package com.example

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches lyrics from the YouLyPlus / LyricsPlus (KPoe) API - community-hosted mirrors of the
 * open-source `ibratabian17/lyricsplus` backend, the same servers the YouLyPlus browser extension
 * queries. Adapted from Echo-Music's `youlyplus/YouLyPlus.kt`.
 *
 * Each mirror is tried in turn (rather than raced in parallel, to keep this provider's shape a
 * plain suspend function like MuseFlow's others) until one returns usable lyrics; the servers are
 * community-run and any one of them can be down at a given time.
 *
 * Used as the "YouLyPlus" entry in the configurable lyrics provider order (see
 * [LyricsViewModel]).
 */
class YouLyPlusProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private object Api {
        // Mirrors of the YouLyPlus extension's KPOE_SERVERS constant.
        val SERVERS = listOf(
            "https://lyricsplus.prjktla.my.id",
            "https://lyricsplus.atomix.one",
            "https://lyricsplus.binimum.org",
            "https://lyricsplus.prjktla.workers.dev",
            "https://lyricsplus-seven.vercel.app",
            "https://lyrics-plus-backend.vercel.app",
        )
    }

    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int? = null): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                for (server in Api.SERVERS) {
                    val result = fetchFromServer(server, title, artist, durationSeconds)
                    if (result != null) return@withContext result
                }
                LyricsResult.NotFound
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    private fun fetchFromServer(server: String, title: String, artist: String, durationSeconds: Int?): LyricsResult? {
        val base = if (server.endsWith("/")) server else "$server/"
        val url = buildString {
            append("${base}v2/lyrics/get")
            append("?title=${encode(title)}")
            append("&artist=${encode(artist)}")
            if (durationSeconds != null && durationSeconds > 0) append("&duration=$durationSeconds")
        }
        val request = Request.Builder().url(url).build()
        val json = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.let(::JSONObject) ?: return null
            }
        } catch (e: Exception) {
            return null
        }

        // KPoe style: an array of timed lines, each optionally with word-level "syllabus".
        json.optJSONArray("lyrics")?.let { lyricsArray ->
            val lines = parseKpoeLines(lyricsArray)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        // LRCLib-compatible style, when a mirror proxies that shape instead.
        json.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Synced(LrcLibProvider.parseLrc(it))
        }
        json.optString("plainLyrics").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.PlainOnly(it)
        }
        return null
    }

    private fun parseKpoeLines(lyricsArray: org.json.JSONArray): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (i in 0 until lyricsArray.length()) {
            val item = lyricsArray.optJSONObject(i) ?: continue
            val lineTimeMs = item.optLong("time", -1L)
            if (lineTimeMs < 0) continue

            val syllabus = item.optJSONArray("syllabus")
            val words = mutableListOf<LyricWord>()
            val text = StringBuilder()
            if (syllabus != null && syllabus.length() > 0) {
                for (j in 0 until syllabus.length()) {
                    val syl = syllabus.optJSONObject(j) ?: continue
                    val sylText = syl.optString("text").takeIf { it.isNotEmpty() } ?: continue
                    val sylTime = syl.optLong("time", lineTimeMs)
                    val sylDuration = syl.optLong("duration", 0L)
                    words += LyricWord(startMs = sylTime, endMs = sylTime + sylDuration, text = sylText)
                    if (text.isNotEmpty() && !text.endsWith(" ") && !sylText.startsWith(" ")) text.append(' ')
                    text.append(sylText)
                }
            } else {
                text.append(item.optString("text"))
            }

            val lineText = text.toString().trim()
            if (lineText.isBlank()) continue
            lines += LyricLine(timeMs = lineTimeMs, text = lineText, words = words.takeIf { it.isNotEmpty() })
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
