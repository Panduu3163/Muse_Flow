package com.example

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Reject known duration/version mismatches before ranking text similarity.
 * Artist/title strings are a fallback, never a substitute for an available recording ID. */
internal fun lyricsRecordingMatchScore(
    title: String, artist: String, durationSeconds: Int?,
    candidateTitle: String, candidateArtist: String, candidateDuration: Int?,
): Double? {
    if (!lyricsDurationMatches(durationSeconds, candidateDuration)) return null
    fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    fun versions(value: String): Set<String> = Regex("\\b(live|remix|acoustic|instrumental|karaoke|slowed|sped up|nightcore|demo|cover|extended|remaster(?:ed)?)\\b")
        .findAll(normalize(value)).map { it.value.replace("remastered", "remaster") }.toSet()
    if (versions(title) != versions(candidateTitle)) return null
    fun titleKey(value: String) = normalize(value.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " "))
        .replace(Regex("\\b(official music video|official video|official audio|lyric video|lyrics|audio)\\b"), " ")
        .trim().replace(Regex("\\s+"), " ")
    fun overlap(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val left = a.split(' ').toSet()
        val right = b.split(' ').toSet()
        return left.intersect(right).size.toDouble() / maxOf(left.size, right.size)
    }
    val titleScore = overlap(titleKey(title), titleKey(candidateTitle))
    if (titleScore < 0.6) return null
    val artistScore = overlap(normalize(artist), normalize(candidateArtist))
    if (artist.isNotBlank() && artistScore == 0.0) return null
    val durationScore = if (durationSeconds != null && candidateDuration != null && durationSeconds > 0 && candidateDuration > 0) {
        40.0 - abs(durationSeconds.toLong() - candidateDuration) * 3.0
    } else 0.0
    return titleScore * 80.0 + artistScore * 40.0 + durationScore
}

internal fun lyricsDurationMatches(expected: Int?, candidate: Int?): Boolean =
    expected == null || expected <= 0 || candidate == null || candidate <= 0 ||
        abs(expected.toLong() - candidate) <= 10
