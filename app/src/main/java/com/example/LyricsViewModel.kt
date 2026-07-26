package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Fetches lyrics for whatever is playing.
 *
 * Three providers are tried in order: LRCLib first (it returns real line-by-line LRC timing, which
 * is what makes a scrolling view possible), then BetterLyrics (word-level KRC timing), then
 * YouTube Music's own lyrics tab as a last resort - it has no timing at all, only plain text, so
 * it's worse than either synced source but still better than "no lyrics found". A provider that
 * returns [LyricsResult.NotFound] is not treated as a failure - the next one is simply tried, and
 * only a genuine miss from all of them surfaces as "no lyrics".
 *
 * Results are cached per track for the session, so scrolling in and out of the lyrics view or
 * pausing doesn't refetch.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val lrcLib = LrcLibProvider()
    private val betterLyrics = BetterLyricsProvider()
    private val router = MusicSearchRouter(application)

    private val _state = MutableStateFlow<LyricsResult?>(null)
    val state: StateFlow<LyricsResult?> = _state.asStateFlow()

    private val cache = mutableMapOf<String, LyricsResult>()
    private var loadJob: Job? = null
    private var loadedKey: String? = null

    /** Loads lyrics for [title]/[artist], reusing the cached result when the track hasn't changed.
     * [videoId], when supplied, must already be a confirmed real YouTube video id (see
     * [hasRealVideoId]) - the caller's job, since a fabricated "title|artist" stand-in id would
     * otherwise reach [MusicSearchRouter.getLyricsText] and resolve to nothing. */
    fun load(title: String, artist: String, durationSeconds: Int?, videoId: String? = null) {
        if (title.isBlank()) return
        val key = "${title.trim().lowercase()}::${artist.trim().lowercase()}"
        if (key == loadedKey) return

        loadedKey = key
        cache[key]?.let {
            _state.value = it
            return
        }

        loadJob?.cancel()
        _state.value = null // null = loading, distinct from NotFound
        loadJob = viewModelScope.launch {
            val result = fetchFirstUsable(title, artist, durationSeconds, videoId)
            cache[key] = result
            // Guard against a stale response landing after the user skipped to another track.
            if (loadedKey == key) _state.value = result
        }
    }

    private suspend fun fetchFirstUsable(
        title: String,
        artist: String,
        durationSeconds: Int?,
        videoId: String?,
    ): LyricsResult {
        val providers = buildList<suspend () -> LyricsResult> {
            add { lrcLib.fetchLyrics(title, artist, durationSeconds) }
            add { betterLyrics.fetchLyrics(title, artist, durationSeconds) }
            if (videoId != null) {
                add {
                    router.getLyricsText(videoId)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { LyricsResult.PlainOnly(it) }
                        ?: LyricsResult.NotFound
                }
            }
        }

        // Not simply "first Synced wins": LRCLib (tried first, for its broader coverage) only
        // ever has line-level timing, never word-level - so returning on its result immediately
        // would mean BetterLyrics's word-level KRC data (the only source that makes karaoke word
        // sync possible) never gets a chance to run for any track LRCLib also covers, which in
        // practice is most of them. A synced result WITH word timing is the only thing that
        // short-circuits the loop; a synced result without it is kept as a candidate while later
        // providers are still tried, in case one of them has the word-level version.
        var bestSynced: LyricsResult.Synced? = null
        var plainFallback: LyricsResult? = null
        for (provider in providers) {
            when (val result = runCatching { provider() }.getOrElse { LyricsResult.NotFound }) {
                is LyricsResult.Synced -> {
                    if (result.lines.any { it.words != null }) return result // Best case possible.
                    if (bestSynced == null) bestSynced = result
                }
                is LyricsResult.PlainOnly -> if (plainFallback == null) plainFallback = result
                is LyricsResult.Instrumental -> return result
                else -> Unit
            }
        }
        return bestSynced ?: plainFallback ?: LyricsResult.NotFound
    }

    fun clear() {
        loadJob?.cancel()
        loadedKey = null
        _state.value = null
    }
}

/** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
fun List<LyricLine>.activeLineIndex(positionMs: Long): Int {
    if (isEmpty()) return -1
    // indexOfLast is O(n) but lyric lists are small (tens of lines) and this runs at most a few
    // times a second, so a binary search would be premature.
    return indexOfLast { it.timeMs <= positionMs }
}
