package com.example

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Fetches word-synced lyrics from Better Lyrics' own hosted API
 * (https://github.com/better-lyrics/unison, served at `lyrics-api.boidu.dev`), adapted from
 * Echo-Music's `BetterLyrics.kt`/`TTMLParser.kt`. The API returns Apple Music-style TTML
 * ("Timed Text Markup Language") documents; [parseTtml] turns those directly into [LyricLine]s
 * with word-level [LyricWord] timing where the TTML has per-`<span>` timestamps.
 *
 * Distinct from [KugouLyricsProvider] - which is what this class used to be, before being split
 * out under its real name - this one talks to Better Lyrics' actual origin instead of Kugou's.
 *
 * Used as the "BetterLyrics" entry in the configurable lyrics provider order (see
 * [LyricsViewModel]).
 */
class BetterLyricsProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val BASE_URL = "https://lyrics-api.boidu.dev"
    }

    suspend fun fetchLyrics(title: String, artist: String, durationSeconds: Int? = null): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                val ttml = fetchTtml(title, artist, durationSeconds) ?: return@withContext LyricsResult.NotFound
                val lines = parseTtml(ttml)
                if (lines.isEmpty()) LyricsResult.NotFound else LyricsResult.Synced(lines)
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    private fun fetchTtml(title: String, artist: String, durationSeconds: Int?): String? {
        val url = buildString {
            append("${Api.BASE_URL}/getLyrics")
            append("?s=${encode(title)}")
            append("&a=${encode(artist)}")
            if (durationSeconds != null && durationSeconds > 0) append("&d=$durationSeconds")
        }
        val request = Request.Builder().url(url).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body).optString("ttml").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** Parses an Apple Music-style TTML document into [LyricLine]s. Each `<p>` element is a
         * line; its `begin` attribute is the line start time, and per-word `<span begin="..."
         * end="...">` children (when present) become [LyricWord]s. Background-vocal spans
         * (`ttm:role="x-bg"`) and translation/romanization spans (`x-translation`/`x-roman`) are
         * skipped - this app has nowhere to show them separately from the main line yet. */
        fun parseTtml(ttml: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            try {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                val builder = factory.newDocumentBuilder()
                val doc = builder.parse(ttml.byteInputStream())

                val pElements = doc.getElementsByTagName("p")
                for (i in 0 until pElements.length) {
                    val p = pElements.item(i) as? Element ?: continue
                    val begin = p.getAttribute("begin").takeIf { it.isNotBlank() } ?: continue
                    val lineStartMs = (parseTimeSeconds(begin) * 1000).toLong()

                    val words = mutableListOf<LyricWord>()
                    val text = StringBuilder()
                    val childNodes = p.childNodes
                    for (j in 0 until childNodes.length) {
                        val node = childNodes.item(j)
                        if (node.nodeType != Node.ELEMENT_NODE) continue
                        val span = node as? Element ?: continue
                        if (span.tagName.lowercase() != "span") continue

                        val role = span.attributeByLocalName("role")
                        if (role == "x-bg" || role == "x-translation" || role == "x-roman") continue

                        val wordBegin = span.getAttribute("begin")
                        val wordEnd = span.getAttribute("end")
                        val wordText = span.textContent?.trim().orEmpty()
                        if (wordText.isEmpty()) continue

                        if (wordBegin.isNotBlank() && wordEnd.isNotBlank()) {
                            words += LyricWord(
                                startMs = (parseTimeSeconds(wordBegin) * 1000).toLong(),
                                endMs = (parseTimeSeconds(wordEnd) * 1000).toLong(),
                                text = wordText
                            )
                        }
                        if (text.isNotEmpty()) text.append(' ')
                        text.append(wordText)
                    }

                    val lineText = text.toString().trim().ifEmpty { p.textContent?.trim().orEmpty() }
                    if (lineText.isBlank()) continue
                    lines += LyricLine(timeMs = lineStartMs, text = lineText, words = words.takeIf { it.isNotEmpty() })
                }
            } catch (e: Exception) {
                return emptyList()
            }
            return lines.sortedBy { it.timeMs }
        }

        /** Reads an attribute regardless of which namespace prefix (if any) the document used -
         * TTML documents vary between `ttm:role`, a default-namespaced `role`, or no prefix at
         * all depending on the source. */
        private fun Element.attributeByLocalName(localName: String): String {
            getAttributeNS("http://www.w3.org/ns/ttml#metadata", localName).takeIf { it.isNotEmpty() }?.let { return it }
            getAttribute("ttm:$localName").takeIf { it.isNotEmpty() }?.let { return it }
            val attrs = attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                val name = attr.nodeName ?: continue
                if (name == localName || name.endsWith(":$localName")) return attr.nodeValue.orEmpty()
            }
            return ""
        }

        /** TTML timestamps are "hh:mm:ss.fff" or "mm:ss.fff" (sometimes just seconds). */
        private fun parseTimeSeconds(timeStr: String): Double = try {
            if (":" in timeStr) {
                val parts = timeStr.split(":")
                when (parts.size) {
                    2 -> parts[0].toDouble() * 60 + parts[1].toDouble()
                    3 -> parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()
                    else -> timeStr.toDoubleOrNull() ?: 0.0
                }
            } else {
                timeStr.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
