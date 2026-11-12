package com.example

import org.junit.Assert.*
import org.junit.Test

class LyricsRecordingMatchTest {
    private fun match(candidateTitle: String, duration: Int? = 180, candidateArtist: String = "Demo artist") =
        lyricsRecordingMatchScore("Example song", "Demo artist", 180, candidateTitle, candidateArtist, duration)

    @Test fun rejectsKnownDifferentVersionsEvenWhenDurationMatches() {
        assertNull(match("Example song (Live)"))
        assertNull(match("Example song - Remix"))
        assertNull(match("Example song (Acoustic)"))
        assertNull(match("Example song (Slowed)"))
    }

    @Test fun rejectsWrongArtistAndKnownDurationMismatch() {
        assertNull(match("Example song", candidateArtist = "Someone else"))
        assertNull(match("Example song", duration = 240))
        assertNotNull(match("Example song", duration = null))
        assertNotNull(match("Example song (Official Audio)"))
    }

    @Test fun doesNotConfuseLiveWithAliveOrDiscardAllVersionText() {
        assertNotNull(lyricsRecordingMatchScore("Alive", "Demo", 180, "Alive", "Demo", 180))
        assertNotNull(lyricsRecordingMatchScore("Example song (Live)", "Demo", 180, "Example song (Live)", "Demo", 180))
        assertNull(lyricsRecordingMatchScore("Example song (Live)", "Demo", 180, "Example song", "Demo", 180))
    }

    @Test fun oneTimedLineDoesNotClaimWholeDocumentWordSync() {
        val lines = listOf(LyricLine(0, "A", listOf(LyricWord(0, 400, "A"))), LyricLine(500, "Several untimed words here"))
        assertEquals(3, lyricResultRank(LyricsResult.Synced(lines)))
        assertEquals(4, lyricResultRank(LyricsResult.Synced(lines.take(1))))
    }

    @Test fun unknownDurationDoesNotRejectWhileKnownMismatchDoes() {
        assertTrue(lyricsDurationMatches(null, 300))
        assertTrue(lyricsDurationMatches(180, 185))
        assertFalse(lyricsDurationMatches(180, 250))
    }
}
