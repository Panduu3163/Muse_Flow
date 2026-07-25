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
 * Two providers are tried in order: LRCLib first (it returns real line-by-line LRC timing, which is
 * what makes a scrolling view possible), then BetterLyrics. A provider that returns
 * [LyricsResult.NotFound] is not treated as a failure - the next one is simply tried, and only a
 * genuine miss from all of them surfaces as "no lyrics".
 *
 * Results are cached per track for the session, so scrolling in and out of the lyrics view or
 * pausing doesn't refetch.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val lrcLib = LrcLibProvider()
    private val betterLyrics = BetterLyricsProvider()

    private val _state = MutableStateFlow<LyricsResult?>(null)
    val state: StateFlow<LyricsResult?> = _state.asStateFlow()

    private val cache = mutableMapOf<String, LyricsResult>()
    private var loadJob: Job? = null
    private var loadedKey: String? = null

    /** Loads lyrics for [title]/[artist], reusing the cached result when the track hasn't changed. */
    fun load(title: String, artist: String, durationSeconds: Int?) {
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
            val result = fetchFirstUsable(title, artist, durationSeconds)
            cache[key] = result
            // Guard against a stale response landing after the user skipped to another track.
            if (loadedKey == key) _state.value = result
        }
    }

    private suspend fun fetchFirstUsable(
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): LyricsResult {
        val providers = listOf<suspend () -> LyricsResult>(
            { lrcLib.fetchLyrics(title, artist, durationSeconds) },
            { betterLyrics.fetchLyrics(title, artist, durationSeconds) },
        )

        var fallback: LyricsResult = LyricsResult.NotFound
        for (provider in providers) {
            when (val result = runCatching { provider() }.getOrElse { LyricsResult.NotFound }) {
                is LyricsResult.Synced -> return result // Best case, stop immediately.
                is LyricsResult.PlainOnly -> fallback = result // Keep looking for a synced version.
                is LyricsResult.Instrumental -> return result
                else -> Unit
            }
        }
        return fallback
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
