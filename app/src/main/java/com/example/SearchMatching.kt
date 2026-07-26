package com.example

import kotlin.math.max

/** Normalizes a title/artist string for cross-source matching: lowercase, strip bracketed
 * annotations like "(Official Video)"/"[Lyrics]", strip punctuation, collapse whitespace. Titles
 * and artist credits are formatted quite differently between sources (e.g. "Song Title" vs
 * "Song Title (Official Audio)"), so exact-string matching would almost never fire. */
private fun normalizeForMatch(text: String): String = text
    .lowercase()
    .replace(Regex("""[\(\[][^)\]]*[\)\]]"""), " ")
    .replace(Regex("[^a-z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[b.length]
}

/** 1.0 = identical after normalization, 0.0 = completely different. */
private fun similarity(a: String, b: String): Double {
    val na = normalizeForMatch(a)
    val nb = normalizeForMatch(b)
    if (na == nb) return 1.0
    val maxLen = max(na.length, nb.length)
    if (maxLen == 0) return 1.0
    return 1.0 - levenshtein(na, nb).toDouble() / maxLen
}

/** Whether ([aTitle], [aArtist]) and ([bTitle], [bArtist]) plausibly refer to the same recording.
 * Title is weighted much more heavily than artist, since artist-credit formatting varies far more
 * across sources than song titles do (features, "&" vs ",", romanization, ...). Used to
 * de-duplicate online results against on-device ones. */
fun isLikelyMatch(aTitle: String, aArtist: String, bTitle: String, bArtist: String): Boolean {
    val titleSim = similarity(aTitle, bTitle)
    val artistSim = similarity(aArtist, bArtist)
    return titleSim >= 0.82 && artistSim >= 0.5
}

/** Below this normalized title-to-query similarity, an on-device result is noise rather than a
 * plausible match. Only ever applied to local results - YouTube Music's own ranking is trusted
 * as-is (see [mergeSearchResults]). */
private const val MIN_QUERY_RELEVANCE = 0.35

/**
 * Merges on-device results with YouTube Music song results into one ranked list.
 *
 * The two sources get deliberately different treatment, not a shared relevance metric:
 * - On-device (MediaStore) search is plain substring matching with no relevance ranking of its
 *   own, so results are filtered by title similarity to [query] - otherwise a same-titled but
 *   unrelated file buries the real match.
 * - YouTube Music's search (the same backend the official YouTube Music app itself uses) already
 *   does real relevance ranking, including matching a typed *lyric snippet* to the right song -
 *   something with essentially zero string overlap with that song's actual title, which our own
 *   title-similarity check would score near zero and wrongly discard. So YouTube's results are
 *   never filtered or re-scored by title similarity here; its own result order is trusted as-is.
 *   This is what makes "type a line you remember, not the song name" work at all.
 *
 * Final order: primarily by relevance (YouTube results always rank as maximally relevant, trusting
 * the source); ties broken by each source's own rank position (earlier = that engine's own higher
 * confidence), with YouTube winning a full tie.
 */
fun mergeSearchResults(
    query: String,
    localResults: List<TrackResult>,
    youTubeResults: List<TrackResult>
): List<TrackResult> {
    data class Ranked(val result: TrackResult, val relevance: Double, val rank: Int, val fromYouTube: Boolean)

    val localRanked = localResults.mapIndexedNotNull { index, r ->
        val relevance = similarity(query, r.title)
        if (relevance < MIN_QUERY_RELEVANCE) null else Ranked(r, relevance, index, fromYouTube = false)
    }
    val youTubeRanked = youTubeResults.mapIndexedNotNull { index, r ->
        if (localRanked.any { local -> isLikelyMatch(local.result.title, local.result.artist, r.title, r.artist) }) {
            return@mapIndexedNotNull null
        }
        Ranked(r, relevance = 1.0, rank = index, fromYouTube = true)
    }

    return (localRanked + youTubeRanked)
        .sortedWith(
            compareByDescending<Ranked> { it.relevance }
                .thenBy { it.rank }
                .thenBy { !it.fromYouTube } // false < true, so YouTube wins a full tie
        )
        .map { it.result }
}

/** Merges on-device and YouTube Music album results: local first, then only the YouTube albums
 * that don't plausibly duplicate one of them (by title + artist). */
fun mergeAlbumResults(localResults: List<AlbumResult>, youTubeResults: List<AlbumResult>): List<AlbumResult> {
    val uniqueYouTube = youTubeResults.filter { yt ->
        localResults.none { local -> isLikelyMatch(local.title, local.artist, yt.title, yt.artist) }
    }
    return localResults + uniqueYouTube
}

/** Merges on-device and YouTube Music artist results, de-duplicating by name only (an artist has
 * no separate "artist" field to also compare, unlike a song/album). */
fun mergeArtistResults(localResults: List<ArtistResult>, youTubeResults: List<ArtistResult>): List<ArtistResult> {
    val uniqueYouTube = youTubeResults.filter { yt ->
        localResults.none { local -> isLikelyMatch(local.name, "", yt.name, "") }
    }
    return localResults + uniqueYouTube
}

/** Merges playlist results. Playlists are user/platform-curated mixes rather than a fixed
 * canonical work, so - unlike songs/albums/artists - a title match across sources isn't good
 * evidence of being "the same" playlist; both sources' results are kept as-is, local first. */
fun mergePlaylistResults(localResults: List<PlaylistResult>, youTubeResults: List<PlaylistResult>): List<PlaylistResult> =
    localResults + youTubeResults
