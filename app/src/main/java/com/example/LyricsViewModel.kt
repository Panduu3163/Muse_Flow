package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Fetches real synced lyrics from LRCLib for whatever track is currently loaded, keyed by
 * title/artist/duration. Scoped the same way [ThemeViewModel] is (Activity-wide, via the
 * `LocalViewModelStoreOwner` override around `NavHost` in `MainActivity`), so navigating away
 * from and back to Now Playing doesn't throw away lyrics already fetched for the still-playing
 * track.
 */
class LyricsViewModel(
    private val lrcLibProvider: LrcLibProvider = LrcLibProvider()
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
            val result = lrcLibProvider.fetchLyrics(track.title, track.artist, durationSeconds)
            // Guards against a slow request for a track the user has since skipped past landing
            // on top of whatever's actually playing now.
            if (loadedForTrack == track) {
                _lyricsResult.value = result
            }
        }
    }
}
