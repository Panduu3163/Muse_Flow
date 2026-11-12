package com.example

import com.example.ui.component.displayLyricWords
import com.example.ui.component.lyricWordSeparator
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTimingTest {
    @Test fun sourceWordTimingWinsOverEstimatedLineTiming() {
        val timed = listOf(
            LyricWord(1_100, 1_380, "Hello "),
            LyricWord(1_520, 1_900, "world"),
        )
        val line = LyricLine(1_000, "Hello world", timed)
        assertEquals(timed, displayLyricWords(line, LyricLine(2_000, "Next")))
        assertEquals("", lyricWordSeparator(line, timed))
    }

    @Test fun estimatedPacingKeepsOriginalSpacingAndFinalBoundary() {
        val line = LyricLine(1_000, "  Stay,   with me  ")
        val words = displayLyricWords(line, LyricLine(3_000, "Next"))
        assertEquals(line.text, words.joinToString(lyricWordSeparator(line, words)) { it.text })
        assertEquals(1_000, words.first().startMs)
        assertEquals(3_000, words.last().endMs)
    }

    @Test fun noSpaceScriptRemainsOneSegment() {
        val line = LyricLine(400, "愛してる")
        val words = displayLyricWords(line, LyricLine(1_100, "次"))
        assertEquals(1, words.size)
        assertEquals("愛してる", words.single().text)
        assertEquals(1_100, words.single().endMs)
    }

    @Test fun zeroLengthFinalSyllableEndsAtNextLine() {
        val result = normalizeSyncedLyrics(LyricsResult.Synced(listOf(
            LyricLine(1_000, "Hello world", listOf(
                LyricWord(1_000, 1_400, "Hello "),
                LyricWord(1_400, 1_400, "world"),
            )),
            LyricLine(2_100, "Next"),
        )))
        assertEquals(2_100, result.lines.first().words!!.last().endMs)
    }

    @Test fun mismatchedWordTextFallsBackToFullLine() {
        val result = normalizeSyncedLyrics(LyricsResult.Synced(listOf(
            LyricLine(0, "Real lyric", listOf(LyricWord(0, 500, "Other"))),
        )))
        assertEquals(null, result.lines.single().words)
    }

    @Test fun enhancedLrcKeepsExplicitSpacesAndUnspacedScript() {
        val lines = PaxSenixProvider.parseEnhancedLrc(
            "[00:01.00]<00:01.00>Hello <00:01.40>world\n" +
                "[00:02.00]<00:02.00>你<00:02.30>好"
        )
        assertEquals("Hello world", lines[0].text)
        assertEquals("你好", lines[1].text)
    }

    @Test fun ttmlKeepsSyllablesTogetherWhenNoSpaceIsPresent() {
        val lines = BetterLyricsProvider.parseTtml(
            "<tt><body><div><p begin=\"00:00:01.000\">" +
                "<span begin=\"00:00:01.000\" end=\"00:00:01.200\">你</span>" +
                "<span begin=\"00:00:01.200\" end=\"00:00:01.500\">好</span>" +
                "</p></div></body></tt>"
        )
        assertEquals("你好", lines.single().text)
        assertEquals("你好", lines.single().words!!.joinToString("") { it.text })
    }
}
