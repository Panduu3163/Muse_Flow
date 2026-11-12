package com.example

import org.junit.Assert.*
import org.junit.Test

class ListeningSessionTest {
    private fun track(id: String = "audio000001") = Track("Song", "Artist", "Album", "3:00", "", 0,
        sourceId = id, sourceType = MusicSource.YOUTUBE_MUSIC)

    @Test fun countsOnlyPlayingTimeAndKeepsOneSessionAcrossPauses() {
        var clock = 0L
        val saved = mutableListOf<ListeningSessionEntity>()
        val tracker = ListeningSessionTracker({ clock }, { 123L }, { 0 }) { row, _ -> saved += row }
        tracker.start(track(), 180_000, true)
        clock = 10_000
        tracker.playingChanged(false, 180_000)
        clock = 90_000 // pause/buffering must not count
        tracker.playingChanged(true, 180_000)
        clock = 100_000
        tracker.finish(ListeningOutcome.SKIPPED)
        assertEquals(listOf(10_000L, 20_000L), saved.map { it.listenedMs })
        assertEquals(1, saved.map { it.id }.distinct().size)
        assertEquals("SKIPPED", saved.last().outcome)
        assertEquals("yt:audio000001", saved.last().trackKey)
    }

    @Test fun seekAndQueueEditsNeverInventListenedDurationOrDislikes() {
        var clock = 0L
        val saved = mutableListOf<ListeningSessionEntity>()
        val tracker = ListeningSessionTracker({ clock }, { 100 }, { 0 }) { row, _ -> saved += row }
        tracker.start(track(), 180_000, true)
        clock = 5_000
        tracker.playingChanged(false, 180_000)
        clock = 500_000
        tracker.finish(ListeningOutcome.SKIPPED) // moving a paused queue is neutral
        assertEquals(5_000L, saved.last().listenedMs)
        assertEquals("INTERRUPTED", saved.last().outcome)
        assertEquals(0.0, learnedRecordingPreferences(saved.takeLast(1), 100)["audio000001"]!!, 0.0)
    }

    @Test fun retriesCanStartFreshAndRepeatedFinishNeverDuplicatesASession() {
        var clock = 0L
        var generation = 0L
        val saved = mutableListOf<Pair<ListeningSessionEntity, Long>>()
        val tracker = ListeningSessionTracker({ clock }, { 100 }, { generation }) { row, epoch -> saved += row to epoch }
        tracker.start(track(), 180_000, true)
        clock = 5_000
        tracker.finish(ListeningOutcome.ERROR)
        tracker.finish(ListeningOutcome.ERROR)
        generation++
        tracker.ensureStarted(track(), 180_000)
        tracker.playingChanged(true, 180_000)
        clock = 40_000
        tracker.finish(ListeningOutcome.ENDED)
        assertEquals(2, saved.size)
        assertNotEquals(saved[0].first.id, saved[1].first.id)
        assertEquals(listOf(0L, 1L), saved.map { it.second })
        assertEquals(35_000L, saved.last().first.listenedMs)
    }

    @Test fun onlyDeliberateEarlySkipsTrainNegativePreferenceAndOldDataExpires() {
        fun session(outcome: String, listened: Long = 5_000, at: Long = 100) =
            ListeningSessionEntity(outcome, "yt:audio000001", "audio000001", "A", at, listened, 180_000, outcome)
        for (neutral in listOf("ERROR", "INTERRUPTED", "OPEN", "ENDED")) {
            assertEquals(0.0, learnedRecordingPreferences(listOf(session(neutral)), 100).values.single(), 0.0)
        }
        assertTrue(learnedRecordingPreferences(listOf(session("SKIPPED")), 100).values.single() < 0)
        assertTrue(learnedRecordingPreferences(listOf(session("ENDED", 150_000)), 100).values.single() > 0)
        assertEquals(0.0, learnedRecordingPreferences(listOf(session("SKIPPED", 1_000)), 100).values.single(), 0.0)
        assertTrue(learnedRecordingPreferences(listOf(session("SKIPPED", at = -40L * 86_400_000)), 100).isEmpty())
    }

    @Test fun recentFavouritesUseHeardTimeInsteadOfHistoricalStartCounts() {
        val a = PlaybackHistoryEntity("yt:a", "A", "Artist", "", "3:00", 0, null, null, 100, playCount = 100)
        val b = a.copy(key = "yt:b", title = "B", playCount = 1)
        val rows = listOf(ListeningSessionEntity("b", b.key, "b", "Artist", 100, 120_000, 180_000, "ENDED"),
            ListeningSessionEntity("a", a.key, "a", "Artist", 100, 5_000, 180_000, "SKIPPED"))
        assertEquals(listOf("B"), recentFavourites(listOf(a, b), rows, 100).map { it.title })
    }

    @Test fun artistLearningCanGeneralizeWithoutOverpoweringSourceRelevance() {
        val rows = (0 until 8).map { ListeningSessionEntity("$it", "yt:old", "old", " Preferred Artist ", 100,
            90_000, 180_000, "ENDED") }
        val artists = learnedArtistPreferences(rows, 100)
        assertTrue(artists.getValue("preferred artist") in 0.0..0.15)
        assertTrue(artists.getValue("preferred artist") > 0)
        assertTrue(learnedArtistPreferences(rows, 40L * 86_400_000).isEmpty())
    }
}
