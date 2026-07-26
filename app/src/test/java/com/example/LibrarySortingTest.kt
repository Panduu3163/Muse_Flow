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
    fun `an unparsable duration contributes zero rather than throwing`() {
        val tracks = listOf(trackOf("-:--"), trackOf("3:00"))
        assertEquals("3 min", tracks.totalDurationLabel())
    }

    @Test
    fun `empty list reads as under a minute`() {
        assertEquals("Less than a minute", emptyList<Track>().totalDurationLabel())
    }
}
