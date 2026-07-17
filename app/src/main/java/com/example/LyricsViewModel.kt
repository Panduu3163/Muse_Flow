package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Fetches real synced lyrics for whatever track is currently loaded, keyed by
 * title/artist/duration, trying a chain of sources in order: LRCLib first, then
 * [BetterLyricsProvider] (word-synced, via Kugou) only if LRCLib comes back with no match or
 * fails outright - the same "first source, fallback on failure/empty" resilience pattern already
 * used for search/Home/downloads. Scoped the same way [ThemeViewModel] is (Activity-wide, via the
 * `LocalViewModelStoreOwner` override around `NavHost` in `MainActivity`), so navigating away
 * from and back to Now Playing doesn't throw away lyrics already fetched for the still-playing
 * track.
 */
class LyricsViewModel(
    private val lrcLibProvider: LrcLibProvider = LrcLibProvider(),
    private val betterLyricsProvider: BetterLyricsProvider = BetterLyricsProvider()
) : ViewModel() {

    private val _lyricsResult = MutableStateFlow<LyricsResult?>(null)
    /** Null while loading (or before any track has been requested); otherwise the outcome for
     * the most recently requested track. */
    val lyricsResult: StateFlow<LyricsResult?> = _lyricsResult

    private var loadedForTrack: Track? = null

    fun loadLyricsFor(track: Track) {
        if (track == loadedForTrack) return
        loadedForTrack = track
        _lyricsResult.value = null
        viewModelScope.launch {
            val durationSeconds = parseDurationToSeconds(track.duration)
            var result = lrcLibProvider.fetchLyrics(track.title, track.artist, durationSeconds)
            if (result is LyricsResult.NotFound || result is LyricsResult.Error) {
                val fallback = betterLyricsProvider.fetchLyrics(track.title, track.artist, durationSeconds)
                // Only replace LRCLib's outcome with a genuine hit - otherwise keep LRCLib's own
                // NotFound/Error rather than masking it with BetterLyrics' (likely identical) one.
                if (fallback !is LyricsResult.NotFound && fallback !is LyricsResult.Error) {
                    result = fallback
                }
            }
            // Guards against a slow request for a track the user has since skipped past landing
            // on top of whatever's actually playing now.
            if (loadedForTrack == track) {
                _lyricsResult.value = result
            }
        }
    }
}
