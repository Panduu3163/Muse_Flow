package com.example

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** A single synced lyric line, in playback order, ready to be time-matched against position. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult
    data class PlainOnly(val text: String) : LyricsResult
    data object Instrumental : LyricsResult
    data object NotFound : LyricsResult
    data class Error(val message: String) : LyricsResult
}

/**
 * Fetches lyrics from LRCLib (https://lrclib.net) - a free, public, no-API-key lyrics database
 * keyed by track/artist (optionally narrowed by duration). LRCLib returns line-by-line synced
 * timing ("syncedLyrics", standard LRC format) when available, which is what makes a real
 * karaoke-style highlight-as-you-play view possible; [parseLrc] turns that into [LyricLine]s.
 */
class LrcLibProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val BASE_URL = "https://lrclib.net/api"
        // LRCLib asks integrating clients to identify themselves via User-Agent.
        const val USER_AGENT = "MuseFlow/1.0 (Android; +https://lrclib.net)"
    }

    /**
     * [durationSeconds], when known, narrows LRCLib's `/get` exact-match endpoint (it matches
     * within a couple of seconds of tolerance). If that 404s - wrong duration hint, or just no
     * exact match - falls back to `/search`, taking the first result with real timing data.
     */
    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int? = null): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                getExact(title, artist, durationSeconds)
                    ?: searchFallback(title, artist)
                    ?: LyricsResult.NotFound
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    private fun getExact(title: String, artist: String, durationSeconds: Int?): LyricsResult? {
        val url = buildString {
            append("${Api.BASE_URL}/get")
            append("?track_name=${encode(title)}")
            append("&artist_name=${encode(artist)}")
            if (durationSeconds != null && durationSeconds > 0) append("&duration=$durationSeconds")
        }
        val response = execute(url) ?: return null
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            return parseTrackObject(JSONObject(body))
        }
    }

    private fun searchFallback(title: String, artist: String): LyricsResult? {
        val url = "${Api.BASE_URL}/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        val response = execute(url) ?: return null
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            val candidates = JSONArray(body)
            val parsed = (0 until candidates.length()).mapNotNull { i ->
                candidates.optJSONObject(i)?.let(::parseTrackObject)
            }
            return parsed.firstOrNull { it is LyricsResult.Synced }
                ?: parsed.firstOrNull { it is LyricsResult.Instrumental }
                ?: parsed.firstOrNull { it is LyricsResult.PlainOnly }
        }
    }

    private fun parseTrackObject(track: JSONObject): LyricsResult {
        if (track.optBoolean("instrumental")) return LyricsResult.Instrumental
        track.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.Synced(parseLrc(it))
        }
        track.optString("plainLyrics").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.PlainOnly(it)
        }
        return LyricsResult.NotFound
    }

    private fun execute(url: String): Response? {
        val request = Request.Builder().url(url).header("User-Agent", Api.USER_AGENT).build()
        return try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        private val timeTagRegex = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /**
         * Parses standard LRC timing tags - "[mm:ss.xx]lyric text", possibly several stacked on
         * one line for a repeated lyric - into a flat, time-sorted list. Metadata tags like
         * `[ar:]`/`[ti:]`/`[length:]` don't match the timing pattern and are skipped as-is.
         */
        fun parseLrc(lrc: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            for (rawLine in lrc.lineSequence()) {
                val matches = timeTagRegex.findAll(rawLine).toList()
                if (matches.isEmpty()) continue
                val text = rawLine.substring(matches.last().range.last + 1).trim()
                for (match in matches) {
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val fraction = match.groupValues[3]
                    val millis = when (fraction.length) {
                        0 -> 0L
                        1 -> fraction.toLong() * 100
                        2 -> fraction.toLong() * 10
                        else -> fraction.toLong()
                    }
                    lines += LyricLine((minutes * 60 + seconds) * 1000 + millis, text)
                }
            }
            return lines.sortedBy { it.timeMs }
        }

        /** Index of the line that should be highlighted at [positionMs], or -1 before the first
         * line's timestamp. [lines] must be sorted ascending by [LyricLine.timeMs] (as returned
         * by [parseLrc]). */
        fun currentLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
            lines.indexOfLast { it.timeMs <= positionMs }
    }
}
