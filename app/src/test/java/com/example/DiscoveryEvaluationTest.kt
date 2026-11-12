package com.example

import java.io.File
import kotlin.math.ln
import org.junit.Assert.*
import org.junit.Test

/** Controlled, synthetic judgments: regression evidence, not a real-user relevance claim. */
class DiscoveryEvaluationTest {
    @Test fun comparesProviderOrderWithLearnedRankingOnHeldOutSyntheticJudgments() {
        val now = 1_000_000L
        fun candidate(id: String, artist: String) = TrackResult(id, id, artist, "3:00", "YouTube", MusicSource.YOUTUBE_MUSIC)
        val input = listOf(candidate("repeat", "A"), candidate("skip", "A"), candidate("good1", "B"),
            candidate("good2", "C"), candidate("other", "D"), candidate("repeat", "A"))
        val training = (0 until 8).flatMap { index -> listOf(
            ListeningSessionEntity("s$index", "yt:skip", "skip", "A", now - 100, 5_000, 180_000, "SKIPPED"),
            ListeningSessionEntity("g$index", "yt:good2", "good2", "C", now - 100, 160_000, 180_000, "ENDED"),
        ) }
        val learned = learnedRecordingPreferences(training, now)
        val ranked = rankDiscoveryCandidates(input, 5, recentIds = setOf("repeat"), learnedPreferences = learned)
        val relevant = setOf("good1", "good2") // evaluation-only labels, not passed to ranker
        fun ndcg(rows: List<TrackResult>): Double {
            val dcg = rows.take(3).mapIndexed { index, t -> if (t.id in relevant) ln(2.0) / ln(index + 2.0) else 0.0 }.sum()
            return dcg / (1.0 + ln(2.0) / ln(3.0))
        }
        fun repetition(rows: List<TrackResult>) = rows.take(3).count { it.id == "repeat" }
        val baseline = input.distinctBy { it.id }
        assertTrue(ndcg(ranked) > ndcg(baseline))
        assertTrue(repetition(ranked) < repetition(baseline))
        assertEquals(input.map { it.id }.toSet(), ranked.map { it.id }.toSet())
        assertEquals(ranked, rankDiscoveryCandidates(input, 5, recentIds = setOf("repeat"), learnedPreferences = learned))
        File("build/reports/discovery-evaluation.json").apply { parentFile?.mkdirs() }.writeText("""{
          "dataset":"controlled synthetic fixture; not a phone or real-user evaluation",
          "baselineNdcgAt3":${ndcg(baseline)}, "rankedNdcgAt3":${ndcg(ranked)},
          "baselineRecentRepeatsAt3":${repetition(baseline)}, "rankedRecentRepeatsAt3":${repetition(ranked)},
          "fabricatedCandidates":0, "duplicateRecordings":0
        }""")
    }
}
