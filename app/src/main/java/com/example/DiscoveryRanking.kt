package com.example

import java.util.Locale

/** A small local ranking baseline for discovery and radio continuations only.
 * Source relevance stays important; recent repeats and consecutive artists receive a soft penalty.
 * This never changes an explicit playlist/album order, merges recording variants, or uploads history. */
internal fun rankDiscoveryCandidates(
    candidates: List<TrackResult>,
    limit: Int,
    excludedIds: Set<String> = emptySet(),
    familiarArtists: Set<String> = emptySet(),
    recentIds: Set<String> = emptySet(),
    learnedPreferences: Map<String, Double> = emptyMap(),
    learnedArtists: Map<String, Double> = emptyMap(),
): List<TrackResult> {
    if (limit <= 0) return emptyList()
    fun String.artistKey() = trim().lowercase(Locale.ROOT)
    val familiar = familiarArtists.map { it.artistKey() }.filter { it.isNotEmpty() }.toSet()
    val pool = candidates.asSequence()
        .filter { it.id.isNotBlank() && it.id !in excludedIds && it.sourceType != MusicSource.LOCAL_DEVICE }
        .distinctBy { it.id }.take(256).mapIndexed { index, track -> index to track }.toMutableList()
    val count = pool.size.coerceAtLeast(1)
    val result = mutableListOf<TrackResult>()
    while (result.size < limit && pool.isNotEmpty()) {
        val next = pool.maxBy { (index, track) ->
            val artist = track.artist.artistKey()
            val repeated = if (artist.isBlank()) 0 else result.takeLast(3).count { it.artist.artistKey() == artist }
            1.0 - index.toDouble() / count +
                (if (artist in familiar) 0.15 else 0.0) -
                (if (track.id in recentIds) 0.55 else 0.0) - repeated * 0.55 +
                ((learnedPreferences[track.id]?.takeIf { it.isFinite() } ?: 0.0).coerceIn(-0.3, 0.3) +
                    (learnedArtists[artist]?.takeIf { it.isFinite() } ?: 0.0).coerceIn(-0.15, 0.15)).coerceIn(-0.3, 0.3)
        }
        result += next.second
        pool.remove(next)
    }
    return result
}
