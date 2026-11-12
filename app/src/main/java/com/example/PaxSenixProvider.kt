package com.example

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches word-synced lyrics from PaxSenix's Apple Music lyrics proxy
 * (https://lyrics.paxsenix.org), adapted from Echo-Music's `paxsenixlyrics/Paxsenix.kt`: search
 * Apple Music's catalog for the track, then fetch its TTML/enhanced-LRC/plain lyrics by id.
 *
 * Used as the "PaxSenix" entry in the configurable lyrics provider order (see [LyricsViewModel]).
 */
class PaxSenixProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val BASE_URL = "https://lyrics.paxsenix.org"
        const val USER_AGENT = "MuseFlow/1.0"
    }

    private data class SearchResult(
        val id: String,
        val displayName: String,
        val displayArtist: String,
        val durationMs: Int?,
    )

    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int? = null): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                val results = search("$title $artist").ifEmpty { search(title) }
                if (results.isEmpty()) return@withContext LyricsResult.NotFound

                val best = results
                    .map { it to score(it, title, artist, durationSeconds) }
                    .sortedByDescending { it.second }
                    .firstOrNull { it.second > 0 }
                    ?.first ?: return@withContext LyricsResult.NotFound

                fetchLyricsForTrack(best.id) ?: LyricsResult.NotFound
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    private suspend fun search(query: String): List<SearchResult> {
        val url = "${Api.BASE_URL}/apple-music/search?q=${encode(query)}"
        val array = JSONArray(httpClient.fetchLyricsBody(url) ?: return emptyList())
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SearchResult(id, item.optString("trackName").ifBlank { item.optString("songName") },
                item.optString("artistName"), item.optInt("duration", -1).takeIf { it >= 0 })
        }
    }

    private fun score(result: SearchResult, title: String, artist: String, durationSeconds: Int?): Double =
        lyricsRecordingMatchScore(title, artist, durationSeconds, result.displayName, result.displayArtist,
            result.durationMs?.div(1000)) ?: -1.0

    private suspend fun fetchLyricsForTrack(id: String): LyricsResult? {
        val url = "${Api.BASE_URL}/apple-music/lyrics?id=${encode(id)}"
        val json = httpClient.fetchLyricsJson(url) ?: return null

        json.optString("ttmlContent").takeIf { it.isNotBlank() }?.let { ttml ->
            val lines = BetterLyricsProvider.parseTtml(ttml)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }

        val contentArray = json.optJSONArray("content")
        if (contentArray != null && contentArray.length() > 0) {
            val lines = parseContentArray(contentArray)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }

        json.optString("elrcMultiPerson").takeIf { it.isNotBlank() }?.let { elrc ->
            val lines = parseEnhancedLrc(elrc)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        json.optString("elrc").takeIf { it.isNotBlank() }?.let { elrc ->
            val lines = parseEnhancedLrc(elrc)
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        json.optString("plain").takeIf { it.isNotBlank() }?.let {
            return LyricsResult.PlainOnly(it)
        }
        return null
    }

    /** The `content` array is a list of already-structured lines, each with millisecond
     * `timestamp`/`endtime` and a `text` array of `{text, timestamp, endtime}` words - directly
     * convertible without going through a text-based lyric format. */
    private fun parseContentArray(contentArray: JSONArray): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (i in 0 until contentArray.length()) {
            val line = contentArray.optJSONObject(i) ?: continue
            val lineTimeMs = line.optLong("timestamp", -1L)
            if (lineTimeMs < 0) continue
            val wordArray = line.optJSONArray("text")
            val words = mutableListOf<LyricWord>()
            val text = StringBuilder()
            if (wordArray != null) {
                for (j in 0 until wordArray.length()) {
                    val w = wordArray.optJSONObject(j) ?: continue
                    val wText = w.optString("text").takeIf { it.isNotEmpty() } ?: continue
                    val wStart = w.optLong("timestamp", lineTimeMs)
                    val wEnd = w.optLong("endtime", wStart)
                    words += LyricWord(startMs = wStart, endMs = wEnd, text = wText)
                    text.append(wText)
                }
            }
            val lineText = text.toString().trim()
            if (lineText.isBlank()) continue
            lines += LyricLine(timeMs = lineTimeMs, text = lineText, words = words.takeIf { it.isNotEmpty() })
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        // Enhanced-LRC / "rich sync" format: a normal "[mm:ss.xx]" line timestamp, immediately
        // followed by per-word "<mm:ss.xx>word" tags in place of - or in addition to - plain text.
        private val lineTagRegex = Regex("""^\[(\d{2}):(\d{2})[.:](\d{2,3})]""")
        private val wordTagRegex = Regex("""<(\d{2}):(\d{2})[.:](\d{2,3})>([^<]*)""")

        /** Parses the enhanced-LRC text some PaxSenix responses use for `elrc`/`elrcMultiPerson`
         * into [LyricLine]s with word-level timing. Shared with [SimpMusicProvider], whose
         * `richSyncLyrics` field uses the same tag convention. */
        fun parseEnhancedLrc(text: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            for (rawLine in text.lineSequence()) {
                val trimmed = rawLine.trim('\r', '\n')
                val lineMatch = lineTagRegex.find(trimmed) ?: continue
                val lineStartMs = timeMs(lineMatch.groupValues[1], lineMatch.groupValues[2], lineMatch.groupValues[3])
                val rest = trimmed.substring(lineMatch.range.last + 1)

                val wordMatches = wordTagRegex.findAll(rest).toList()
                if (wordMatches.isEmpty()) {
                    val plain = rest.trim()
                    if (plain.isNotBlank()) lines += LyricLine(timeMs = lineStartMs, text = plain)
                    continue
                }

                val words = mutableListOf<LyricWord>()
                for ((index, match) in wordMatches.withIndex()) {
                    val wStart = timeMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    val wEnd = if (index < wordMatches.size - 1) {
                        timeMs(
                            wordMatches[index + 1].groupValues[1],
                            wordMatches[index + 1].groupValues[2],
                            wordMatches[index + 1].groupValues[3]
                        )
                    } else wStart
                    val wText = match.groupValues[4]
                    if (wText.isNotBlank()) words += LyricWord(startMs = wStart, endMs = wEnd, text = wText)
                }
                val lineText = words.joinToString("") { it.text }.trim()
                if (lineText.isBlank()) continue
                lines += LyricLine(timeMs = lineStartMs, text = lineText, words = words.takeIf { it.isNotEmpty() })
            }
            return lines.sortedBy { it.timeMs }
        }

        private fun timeMs(minutes: String, seconds: String, fraction: String): Long {
            val fracMs = when (fraction.length) {
                2 -> fraction.toLong() * 10
                else -> fraction.toLong()
            }
            return minutes.toLong() * 60_000 + seconds.toLong() * 1000 + fracMs
        }
    }
}
