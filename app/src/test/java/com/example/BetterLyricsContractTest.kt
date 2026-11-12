package com.example

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BetterLyricsContractTest {
    @Test fun requestPreservesUnicodeAndRecordingIdentity() {
        val url = BetterLyricsProvider.requestUrl("তুমি & I", "A + B", 183, "audio000001").toHttpUrl()
        assertEquals("api.betterlyrics.org", url.host)
        assertEquals("তুমি & I", url.queryParameter("s"))
        assertEquals("A + B", url.queryParameter("a"))
        assertEquals("183", url.queryParameter("d"))
        assertEquals("audio000001", url.queryParameter("videoId"))
        val noId = BetterLyricsProvider.requestUrl("T", "A", -1, "title|artist").toHttpUrl()
        assertNull(noId.queryParameter("videoId"))
        assertNull(noId.queryParameter("d"))
    }

    @Test fun cachedResponseDoesNotRequireOptionalMatchScore() {
        val response = JSONObject().put("ttml", "<tt><body><p begin='1s' end='2s'>Hi</p></body></tt>")
        assertTrue(BetterLyricsProvider.decodeResponse(response) is LyricsResult.Synced)
        assertTrue(BetterLyricsProvider.decodeResponse(response.put("score", 0.97)) is LyricsResult.Synced)
        assertTrue(BetterLyricsProvider.decodeResponse(JSONObject()) is LyricsResult.Error)
        assertTrue(BetterLyricsProvider.decodeResponse(JSONObject().put("ttml", "not xml")) is LyricsResult.Error)
    }

    @Test fun linkedTranslationsAndRomanizationKeepExactSyllableWhitespace() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:i="http://itunes.apple.com/lyric-ttml-extensions">
          <head><metadata><translations><translation xml:lang="en"><text for="L1">Night drive</text></translation></translations>
          <transliterations><transliteration xml:lang="ja-Latn"><text for="L1"><span>ma</span><span>yonaka</span> no</text></transliteration></transliterations></metadata></head>
          <body><div><p i:key="L1" begin="1s" end="3s"><span begin="1s" end="1.5s">Mid</span><span begin="1.5s" end="2s">night </span><span begin="2s" end="3s">drive</span></p></div></body></tt>"""
        val line = BetterLyricsProvider.parseTtml(xml).single()
        assertEquals("Midnight drive", line.text)
        assertEquals("Midnight drive", line.words!!.joinToString("") { it.text })
        assertEquals("Night drive", line.translation)
        assertEquals("mayonaka no", line.romanization)
    }
}
