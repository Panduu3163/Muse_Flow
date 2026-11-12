package com.example

/**
 * Weighted-random pick from the user's most-played history, used to blend a seed track's own
 * "radio" with the user's broader taste. Shared by [PlaybackService]'s autoplay continuation and
 * the Search screen's post-search recommendation feed - both want the same "keep drifting toward
 * what this listener actually plays, not just wherever one song's radio goes" behavior.
 *
 * Weighted by play count so heavier favorites surface more often, without it being deterministically
 * the single most-played track every time. Needs at least [minCandidates] eligible history entries
 * to kick in - a fresh install with little/no history returns null rather than a degenerate pick.
 */
internal fun List<PlaybackHistoryEntity>.pickTasteSeed(
    exclude: Set<String> = emptySet(),
    minCandidates: Int = 5,
): PlaybackHistoryEntity? {
    val candidates = this
        .filter { it.sourceType == MusicSource.YOUTUBE_MUSIC.name }
        .filter { it.sourceId?.looksLikeYouTubeVideoId() == true }
        .filter { it.sourceId !in exclude }
    if (candidates.size < minCandidates) return null

    // Weighted by play count: sum every candidate's weight, land a random point in that range,
    // and walk the list subtracting weights until the point falls inside one entry's slice.
    val totalWeight = candidates.sumOf { it.playCount.coerceAtLeast(1) }
    var pick = (0 until totalWeight).random()
    return candidates.firstOrNull { entry ->
        pick -= entry.playCount.coerceAtLeast(1)
        pick < 0
    } ?: candidates.random()
}

/** Interleaves [primary] and [secondary] roughly 2:1, so [secondary]'s tracks aren't all stuck at
 * the tail of a batch while [primary] - the more directly relevant signal - still dominates. */
internal fun <T> interleaveTwoToOne(primary: List<T>, secondary: List<T>): List<T> = buildList {
    val p = primary.iterator()
    val s = secondary.iterator()
    while (p.hasNext() || s.hasNext()) {
        repeat(2) { if (p.hasNext()) add(p.next()) }
        if (s.hasNext()) add(s.next())
    }
}
