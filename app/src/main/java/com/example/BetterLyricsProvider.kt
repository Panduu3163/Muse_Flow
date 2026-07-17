package com.example

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches word-synced lyrics from Kugou Music's public search+lyrics API - no API key needed.
 * Named `BetterLyricsProvider` because it adapts the *approach* pioneered by
 * [better-lyrics](https://github.com/better-lyrics/better-lyrics): word/syllable-level synced
 * lyrics parsed into a line+word timing model (see [LyricLine]/[LyricWord]) for karaoke-style
 * highlighting - the same idea as that extension's TTML handling in its `ttmlUtils.ts`, just
 * applied to Kugou's KRC format instead of TTML.
 *
 * It is deliberately **not** backed by Better Lyrics' own hosted API. That one
 * (https://github.com/better-lyrics/unison, `unison.boidu.dev/lyrics`) is crowdsourced-only for
 * anonymous reads - confirmed by testing a dozen popular tracks (Shape of You, Anti-Hero, As It
 * Was, Levitating, YOASOBI's Idol, ...), every one came back "Lyrics not found." Its live
 * multi-provider lookups - which per that project's own backend source (`better-lyrics/api`) DO
 * include Kugou - require an API key we don't have; anonymous callers only ever see whatever's
 * already been crowd-submitted. Kugou's own public API - one of the actual sources
 * better-lyrics' backend scrapes - is queried directly here instead, going to the origin rather
 * than through a gate we can't open.
 *
 * Used as the second entry in the lyrics chain (see [LyricsViewModel]), tried only when LRCLib
 * has no match.
 */
class BetterLyricsProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val SEARCH_SONG_URL = "https://mobileservice.kugou.com/api/v3/search/song"
        const val LYRICS_SEARCH_URL = "https://lyrics.kugou.com/search"
        const val LYRICS_DOWNLOAD_URL = "https://lyrics.kugou.com/download"
        const val USER_AGENT = "Mozilla/5.0"

        // Fixed XOR key Kugou has used for years to obfuscate KRC lyric downloads - long
        // documented across open-source Kugou API client implementations; verified against a
        // live response before this was written.
        val KRC_XOR_KEY = byteArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
        )
    }

    private data class LyricsCandidate(val id: String, val accessKey: String)

    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int? = null): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                val hash = findSongHash(title, artist) ?: return@withContext LyricsResult.NotFound
                val candidate = findLyricsCandidate(title, artist, hash) ?: return@withContext LyricsResult.NotFound
                val krc = downloadKrc(candidate.id, candidate.accessKey) ?: return@withContext LyricsResult.NotFound
                val lines = parseKrc(krc)
                if (lines.isEmpty()) LyricsResult.NotFound else LyricsResult.Synced(lines)
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    /** Kugou keys its lyrics lookup off a track "hash" (its own content fingerprint), not a plain
     * track id - found via its song search endpoint first. */
    private fun findSongHash(title: String, artist: String): String? {
        val keyword = encode("$title $artist")
        val url = "${Api.SEARCH_SONG_URL}?keyword=$keyword&page=1&pagesize=5"
        val json = fetchJson(url) ?: return null
        val info = json.optJSONObject("data")?.optJSONArray("info") ?: return null
        return (0 until info.length())
            .firstNotNullOfOrNull { info.optJSONObject(it)?.optString("hash")?.takeIf { h -> h.isNotBlank() } }
    }

    private fun findLyricsCandidate(title: String, artist: String, hash: String): LyricsCandidate? {
        val keyword = encode("$title-$artist")
        val url = "${Api.LYRICS_SEARCH_URL}?ver=1&man=yes&client=pc&keyword=$keyword&hash=$hash"
        val json = fetchJson(url) ?: return null
        val first = json.optJSONArray("candidates")?.optJSONObject(0) ?: return null
        val id = first.optString("id").takeIf { it.isNotBlank() } ?: return null
        val accessKey = first.optString("accesskey").takeIf { it.isNotBlank() } ?: return null
        return LyricsCandidate(id, accessKey)
    }

    private fun downloadKrc(id: String, accessKey: String): String? {
        val url = "${Api.LYRICS_DOWNLOAD_URL}?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=krc&charset=utf8"
        val json = fetchJson(url) ?: return null
        val contentBase64 = json.optString("content").takeIf { it.isNotBlank() } ?: return null
        return decryptKrc(contentBase64)
    }

    /** Kugou's KRC download format: base64 -> skip the 4-byte "krc1" magic header -> XOR with a
     * fixed repeating key -> zlib-inflate -> UTF-8 text. */
    private fun decryptKrc(contentBase64: String): String? = try {
        val raw = Base64.decode(contentBase64, Base64.DEFAULT)
        val body = raw.copyOfRange(4, raw.size)
        val key = Api.KRC_XOR_KEY
        val decrypted = ByteArray(body.size) { i -> (body[i].toInt() xor key[i % key.size].toInt()).toByte() }

        val inflater = Inflater()
        inflater.setInput(decrypted)
        val output = ByteArrayOutputStream(decrypted.size * 4)
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            output.write(buffer, 0, count)
        }
        inflater.end()
        output.toString("UTF-8")
    } catch (e: Exception) {
        null
    }

    private fun fetchJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).header("User-Agent", Api.USER_AGENT).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        // A metadata/timing line looks like "[9551,2435]<0,236,0>The <236,487,0>club ...";
        // plain metadata lines ("[ar:...]", "[id:...]") don't match (no comma-separated digits).
        private val krcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val krcWordRegex = Regex("""<(\d+),(\d+),\d+>([^<]*)""")

        /** Parses Kugou's KRC text into [LyricLine]s, each carrying word-level timing via
         * [LyricLine.words] - the per-word `<offsetMs,durationMs,N>word` tags, offsets relative
         * to that line's own start time. */
        fun parseKrc(krc: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            for (rawLine in krc.lineSequence()) {
                val trimmed = rawLine.trim('\r', '\n')
                val lineMatch = krcLineRegex.matchEntire(trimmed) ?: continue
                val lineStartMs = lineMatch.groupValues[1].toLongOrNull() ?: continue
                val rest = lineMatch.groupValues[3]

                val words = mutableListOf<LyricWord>()
                val text = StringBuilder()
                for (wordMatch in krcWordRegex.findAll(rest)) {
                    val offsetMs = wordMatch.groupValues[1].toLong()
                    val durationMs = wordMatch.groupValues[2].toLong()
                    val wordText = wordMatch.groupValues[3]
                    if (wordText.isEmpty()) continue
                    words += LyricWord(
                        startMs = lineStartMs + offsetMs,
                        endMs = lineStartMs + offsetMs + durationMs,
                        text = wordText
                    )
                    text.append(wordText)
                }
                val lineText = text.toString().trim()
                if (lineText.isBlank()) continue
                lines += LyricLine(timeMs = lineStartMs, text = lineText, words = words.takeIf { it.isNotEmpty() })
            }
            return lines.sortedBy { it.timeMs }
        }
    }
}
