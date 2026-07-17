package com.example

import kotlin.math.max

/** Normalizes a title/artist string for cross-source matching: lowercase, strip bracketed
 * annotations like "(Official Video)"/"[Lyrics]", strip punctuation, collapse whitespace. Titles
 * and artist credits are formatted quite differently between JioSaavn and YouTube Music (e.g.
 * "Arijit Singh" vs "Arijit Singh, Pritam", "Song Title" vs "Song Title (Official Audio)"), so
 * exact-string matching would almost never fire. */
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
 * across sources than song titles do (features, "&" vs ",", romanization, ...). Used both to
 * de-duplicate merged JioSaavn/YouTube Music search results and to resolve a YouTube-sourced
 * result to a playable JioSaavn track before playback. */
fun isLikelyMatch(aTitle: String, aArtist: String, bTitle: String, bArtist: String): Boolean {
    val titleSim = similarity(aTitle, bTitle)
    val artistSim = similarity(aArtist, bArtist)
    return titleSim >= 0.82 && artistSim >= 0.5
}

/** Merges JioSaavn and YouTube Music song search results into one list: JioSaavn results first
 * (they're already directly playable), followed by only the YouTube results that don't plausibly
 * duplicate one of them. A YouTube-only result stays in the list - it just needs to be resolved
 * to a JioSaavn match before it can actually be played; see [findPlayableMatch]. */
fun mergeSearchResults(jioSaavnResults: List<TrackResult>, youTubeResults: List<TrackResult>): List<TrackResult> {
    val uniqueYouTube = youTubeResults.filter { yt ->
        jioSaavnResults.none { jio -> isLikelyMatch(jio.title, jio.artist, yt.title, yt.artist) }
    }
    return jioSaavnResults + uniqueYouTube
}

/** Merges JioSaavn and YouTube Music album search results the same way [mergeSearchResults]
 * merges songs: JioSaavn first, then only the YouTube albums that don't plausibly duplicate one
 * of them (by title + artist). */
fun mergeAlbumResults(jioSaavnResults: List<AlbumResult>, youTubeResults: List<AlbumResult>): List<AlbumResult> {
    val uniqueYouTube = youTubeResults.filter { yt ->
        jioSaavnResults.none { jio -> isLikelyMatch(jio.title, jio.artist, yt.title, yt.artist) }
    }
    return jioSaavnResults + uniqueYouTube
}

/** Merges JioSaavn and YouTube Music artist search results, de-duplicating by name only (an
 * artist has no separate "artist" field to also compare, unlike a song/album). */
fun mergeArtistResults(jioSaavnResults: List<ArtistResult>, youTubeResults: List<ArtistResult>): List<ArtistResult> {
    val uniqueYouTube = youTubeResults.filter { yt ->
        jioSaavnResults.none { jio -> isLikelyMatch(jio.name, "", yt.name, "") }
    }
    return jioSaavnResults + uniqueYouTube
}

/** Merges JioSaavn and YouTube Music playlist search results. Playlists are user/platform-curated
 * mixes rather than a fixed canonical work, so - unlike songs/albums/artists - a title match
 * across sources isn't good evidence of being "the same" playlist; both sources' results are kept
 * as-is, JioSaavn first. */
fun mergePlaylistResults(jioSaavnResults: List<PlaylistResult>, youTubeResults: List<PlaylistResult>): List<PlaylistResult> =
    jioSaavnResults + youTubeResults

/** Finds a JioSaavn track that plausibly matches [title]/[artist], so a YouTube-Music-sourced
 * result (which this app never streams from directly) can be played via JioSaavn instead. Returns
 * null if nothing close enough turns up, so the caller can show an explicit "not available"
 * message rather than silently doing nothing. */
suspend fun JioSaavnProvider.findPlayableMatch(title: String, artist: String): TrackResult? =
    search("$title $artist")
        .filter { it.directStreamUrl != null }
        .firstOrNull { isLikelyMatch(it.title, it.artist, title, artist) }
