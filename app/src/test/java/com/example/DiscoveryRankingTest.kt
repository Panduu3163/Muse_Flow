package com.example

import org.junit.Assert.*
import org.junit.Test

class DiscoveryRankingTest {
    private fun track(id: String, artist: String = "Artist A") = TrackResult(
        id, "Same title", artist, "3:00", "YouTube Music", MusicSource.YOUTUBE_MUSIC,
    )

    @Test fun excludesExistingIdsAndKeepsDifferentRecordingsWithSameTitle() {
        val result = rankDiscoveryCandidates(listOf(track("seed"), track("studio"), track("live"), track("live")),
            51, excludedIds = setOf("seed"))
        assertEquals(setOf("studio", "live"), result.map { it.id }.toSet())
    }

    @Test fun artistVarietyImprovesWithoutDiscardingRelevantSongs() {
        val input = listOf(track("a1"), track("a2"), track("a3"), track("b", "Artist B"))
        val result = rankDiscoveryCandidates(input, 4)
        assertEquals("a1", result.first().id)
        assertTrue(result.indexOfFirst { it.id == "b" } < 3)
        assertEquals(input.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test fun recentSongsAreSoftlyPenalizedRatherThanPermanentlyHidden() {
        val result = rankDiscoveryCandidates(listOf(track("old"), track("new", "B")), 2, recentIds = setOf("old"))
        assertEquals(listOf("new", "old"), result.map { it.id })
    }

    @Test fun neverFabricatesCandidatesOrMixesOnDeviceFiles() {
        val local = track("local").copy(sourceType = MusicSource.LOCAL_DEVICE)
        assertEquals(listOf("one"), rankDiscoveryCandidates(listOf(local, track("one")), 51).map { it.id })
        assertTrue(rankDiscoveryCandidates(listOf(track("one")), 0).isEmpty())
    }

    @Test fun learnedFeedbackIsBoundedAndMalformedWeightsCannotPoisonRanking() {
        val input = listOf(track("a", "A"), track("b", "B"))
        assertEquals(listOf("a", "b"), rankDiscoveryCandidates(input, 2,
            learnedPreferences = mapOf("a" to Double.NaN, "b" to Double.POSITIVE_INFINITY)).map { it.id })
        assertEquals(input.map { it.id }.toSet(), rankDiscoveryCandidates(input, 2,
            learnedPreferences = mapOf("b" to 1_000_000.0)).map { it.id }.toSet())
    }
}
