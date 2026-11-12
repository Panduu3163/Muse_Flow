package com.example

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class TtmlDocumentParserTest {
    @Test fun namespacedNestedWordsKeepTimingAndOptionalMetadata() {
        val xml = """<t:tt xmlns:t="http://www.w3.org/ns/ttml" xmlns:m="http://www.w3.org/ns/ttml#metadata" xml:lang="en">
          <t:body><t:div><t:p begin="1s" end="3s" m:agent="v1" xml:id="line1">
            <t:span><t:span begin="1000ms" end="1500ms">Bright </t:span><t:span begin="1.5s" end="2s">morning</t:span></t:span>
            <t:span m:role="x-translation">Translation example</t:span>
            <t:span m:role="x-roman">Reading example</t:span><t:span m:role="x-bg">Echo</t:span>
          </t:p></t:div></t:body></t:tt>"""
        val line = BetterLyricsProvider.parseTtml(xml).single()
        assertEquals("Bright morning", line.text)
        assertEquals(2, line.words!!.size)
        assertEquals(1_500, line.words!!.first().endMs)
        assertEquals(3_000L, line.endMs)
        assertEquals("line1", line.sourceKey)
        assertEquals("en", line.language)
        assertEquals("v1", line.agent)
        assertEquals("Translation example", line.translation)
        assertEquals("Reading example", line.romanization)
        assertEquals("Echo", line.backgroundText)
    }

    @Test fun validatesOffsetAndClockTimes() {
        assertEquals(1250L, parseTtmlTimeMs("1.25s"))
        assertEquals(1250L, parseTtmlTimeMs("1250ms"))
        assertEquals(3_723_500L, parseTtmlTimeMs("01:02:03.500"))
        assertEquals(90_000L, parseTtmlTimeMs("1.5m"))
        assertNull(parseTtmlTimeMs("garbage"))
        assertNull(parseTtmlTimeMs("00:61:00"))
        assertNull(parseTtmlTimeMs("-1s"))
        assertNull(parseTtmlTimeMs("24f"))
    }

    @Test fun invalidLineTimestampIsNotTurnedIntoZero() {
        val lines = BetterLyricsProvider.parseTtml("<tt><body><p begin='oops'>Bad</p><p begin='2s' dur='1s'>Valid</p></body></tt>")
        assertEquals(1, lines.size)
        assertEquals(2000, lines.single().timeMs)
        assertEquals(3000L, lines.single().endMs)
    }

    @Test fun externalEntitiesAreRejected() {
        assertTrue(BetterLyricsProvider.parseTtml("<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///not-allowed'>]><tt><p begin='1s'>&x;</p></tt>").isEmpty())
    }

    @Test fun richMetadataSurvivesCacheWithoutChangingOldEntries() {
        val line = LyricLine(1000, "Example", endMs = 2000, sourceKey = "key", language = "en", translation = "Other", backgroundText = "Echo")
        val lookup = LyricsLookup(LyricsResult.Synced(listOf(line)), "BetterLyrics")
        assertEquals(lookup, decodeLyricsLookup(encodeLyricsLookup(lookup)!!))
        val old = org.json.JSONObject("""{"kind":"synced","lines":[{"time":0,"text":"Old cache"}]}""")
        assertEquals(listOf(LyricLine(0, "Old cache")), (decodeLyricsLookup(old)!!.result as LyricsResult.Synced).lines)
    }
}
