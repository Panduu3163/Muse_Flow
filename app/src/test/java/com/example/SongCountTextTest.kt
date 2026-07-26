package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `songCountText` is the last run of a playlist search result's subtitle, which YouTube fills with
 * a track count for some playlists and a running time for others.
 *
 * The regression these cover: the old parse stripped every non-digit and concatenated what was
 * left, so a playlist whose subtitle read "2 hours, 7 minutes" advertised "27 songs" - and its
 * sheet then correctly showed the 18 tracks it actually has, which read as a loading bug rather
 * than a wrong label.
 */
class SongCountTextTest {

    @Test
    fun `reads a plain count`() {
        assertEquals(18, "18 songs".asSongCount())
        assertEquals(1, "1 song".asSongCount())
        assertEquals(50, "50 tracks".asSongCount())
    }

    @Test
    fun `reads a grouped count`() {
        assertEquals(1200, "1,200 songs".asSongCount())
    }

    @Test
    fun `ignores case and spacing`() {
        assertEquals(31, "31 Songs".asSongCount())
        assertEquals(24, "24songs".asSongCount())
    }

    @Test
    fun `rejects a running time`() {
        assertNull("2 hours, 7 minutes".asSongCount())
        assertNull("1 hour 27 minutes".asSongCount())
        assertNull("45 minutes".asSongCount())
    }

    @Test
    fun `rejects text with no leading number`() {
        assertNull("Album".asSongCount())
        assertNull("Radiohead".asSongCount())
        assertNull("".asSongCount())
        assertNull(null.asSongCount())
    }
}
