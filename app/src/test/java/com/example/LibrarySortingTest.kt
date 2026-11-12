package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

/** [totalDurationLabel] pins the m:ss-sum-to-label formatting used by the playlist detail
 * screen's metadata line. */
class LibrarySortingTest {

    private fun trackOf(duration: String) = Track(
        title = "t",
        artist = "a",
        album = "",
        duration = duration,
        plays = "",
        gradientIndex = 0,
    )

    @Test
    fun `sums minutes and seconds across tracks under an hour`() {
        val tracks = listOf(trackOf("3:30"), trackOf("4:45"))
        assertEquals("8 min", tracks.totalDurationLabel())
    }

    @Test
    fun `rolls over into hours past 60 minutes`() {
        val tracks = listOf(trackOf("45:00"), trackOf("30:00"))
        assertEquals("1 hr 15 min", tracks.totalDurationLabel())
    }

    @Test
    fun `omits the minutes clause on an exact hour`() {
        val tracks = listOf(trackOf("60:00"))
        assertEquals("1 hr", tracks.totalDurationLabel())
    }

    @Test
    fun `reads as under a minute for a handful of very short tracks`() {
        val tracks = listOf(trackOf("0:20"), trackOf("0:15"))
        assertEquals("Less than a minute", tracks.totalDurationLabel())
    }

    @Test
    fun `a partial duration is explicitly a lower bound`() {
        val tracks = listOf(trackOf("-:--"), trackOf("3:00"))
        assertEquals("At least 3 min", tracks.totalDurationLabel())
    }

    @Test
    fun `empty list has zero duration`() {
        assertEquals("0 min", emptyList<Track>().totalDurationLabel())
    }

    @Test
    fun `unknown metadata never claims a short song`() {
        assertEquals("Duration unavailable", durationSummaryLabel(listOf(null, "-:--", "live", "")))
    }

    @Test
    fun `supports hour minute second metadata`() {
        assertEquals("1 hr 5 min", durationSummaryLabel(listOf("1:02:30", "3:00")))
    }

    @Test
    fun `invalid and overflowing durations remain unknown`() {
        for (duration in listOf("-1:00", "2:60", "1:60:00", "1", "1:2:3:4", "9223372036854775807:00")) {
            assertEquals(duration, "Duration unavailable", durationSummaryLabel(listOf(duration)))
        }
    }

    @Test
    fun `partial subminute duration uses a lower bound in seconds`() {
        assertEquals("At least 20 sec", durationSummaryLabel(listOf("0:20", null)))
    }

    @Test
    fun `overflowing sum cannot wrap into a false duration`() {
        assertEquals("Duration unavailable", durationSummaryLabel(listOf("153722867280912930:00", "153722867280912930:00")))
    }
}
