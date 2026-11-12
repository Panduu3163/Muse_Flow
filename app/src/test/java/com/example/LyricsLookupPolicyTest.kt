package com.example

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLookupPolicyTest {
    @Test fun wordTimingBeatsAnEarlierLineTimedSource() = runTest {
        val winner = fetchBestLyrics(listOf(
            LyricsSourceRequest("slow line") {
                delay(10_000)
                LyricsResult.Synced(listOf(LyricLine(0, "line")))
            },
            LyricsSourceRequest("word source") {
                delay(10)
                LyricsResult.Synced(listOf(LyricLine(0, "word",
                    listOf(LyricWord(0, 400, "word")))))
            },
        ))
        assertEquals("word source", winner.source)
    }

    @Test fun equalWordTimingRespectsSourceOrder() = runTest {
        val winner = fetchBestLyrics(listOf(
            LyricsSourceRequest("first") {
                delay(100)
                LyricsResult.Synced(listOf(LyricLine(0, "first", listOf(LyricWord(0, 200, "first")))))
            },
            LyricsSourceRequest("second") {
                delay(1)
                LyricsResult.Synced(listOf(LyricLine(0, "second", listOf(LyricWord(0, 200, "second")))))
            },
        ))
        assertEquals("first", winner.source)
    }

    @Test fun sameQualityRespectsConfiguredOrderDespiteArrivalOrder() = runTest {
        val winner = fetchBestLyrics(listOf(
            LyricsSourceRequest("first") { delay(100); LyricsResult.Synced(listOf(LyricLine(0, "A"))) },
            LyricsSourceRequest("second") { delay(1); LyricsResult.Synced(listOf(LyricLine(0, "B"))) },
        ))
        assertEquals("first", winner.source)
    }

    @Test fun plainLyricsBeatAnInstrumentalClaimFromAnotherSource() = runTest {
        val winner = fetchBestLyrics(listOf(
            LyricsSourceRequest("instrumental") { LyricsResult.Instrumental },
            LyricsSourceRequest("plain") { LyricsResult.PlainOnly("actual lyrics") },
        ))
        assertEquals("plain", winner.source)
    }

    @Test fun allFailedSourcesShowRetryableError() = runTest {
        val winner = fetchBestLyrics(listOf(
            LyricsSourceRequest("one") { LyricsResult.Error("offline") },
            LyricsSourceRequest("two") { throw IllegalStateException("failed") },
        ))
        assertTrue(winner.result is LyricsResult.Error)
    }

    @Test fun missesMixedWithOutagesAreNotReportedAsConfirmedMissingLyrics() = runTest {
        val winner = fetchBestLyrics(listOf(
            LyricsSourceRequest("miss") { LyricsResult.NotFound },
            LyricsSourceRequest("restricted") { LyricsResult.Error("authorization required") },
        ))
        assertTrue(winner.result is LyricsResult.Error)
    }

    @Test fun missesMixedWithTimeoutsStayRetryableAndGenuineMissesRemainNotFound() = runTest {
        val timedOut = fetchBestLyrics(listOf(
            LyricsSourceRequest("miss") { LyricsResult.NotFound },
            LyricsSourceRequest("slow") { delay(20_000); LyricsResult.NotFound },
        ), timeoutMs = 100)
        assertTrue(timedOut.result is LyricsResult.Error)
        assertTrue(fetchBestLyrics(listOf(LyricsSourceRequest("miss") { LyricsResult.NotFound })).result is LyricsResult.NotFound)
    }
}
