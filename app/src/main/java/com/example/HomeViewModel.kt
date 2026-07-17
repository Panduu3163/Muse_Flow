package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A Home shelf backed by a canned search query, standing in for a real recommendation system. */
private data class MoodShelfQuery(val title: String, val query: String)

private val moodShelfQueries = listOf(
    MoodShelfQuery("Chill Vibes", "lofi chill beats"),
    MoodShelfQuery("Workout Energy", "workout gym hype"),
    MoodShelfQuery("Party Hits", "party dance hits"),
    MoodShelfQuery("Focus Flow", "instrumental focus study")
)

/**
 * Real data for Home, in place of the static [MusicData] shelves: "Recently Played" comes from
 * actual playback history in Room ([PlaybackHistoryRepository]), and every other shelf is a
 * canned genre/mood search against JioSaavn and YouTube Music - a stand-in for a real
 * recommendation system, which doesn't exist yet. Each shelf loads independently (its entry in
 * [moodShelves] is missing until that one search resolves) so a slow or failed shelf doesn't
 * block the others from appearing.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)
    private val jioSaavnProvider = JioSaavnProvider()
    private val youTubeProvider = YouTubeMusicProvider(application)

    /** Null while the first read from Room is still in flight; empty once loaded with no history. */
    val recentlyPlayed: StateFlow<List<Track>?> = playbackHistoryRepository.observeRecent(10)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shelfTitles: List<String> = moodShelfQueries.map { it.title }

    /** Shelf title -> [UiState]. A missing entry (or [UiState.Loading]) means that shelf's search
     * hasn't resolved yet; [UiState.Error] means a genuine failure or timeout, distinct from
     * [UiState.Success] with an empty list (resolved, just nothing playable found). */
    private val _moodShelves = MutableStateFlow<Map<String, UiState<List<Track>>>>(emptyMap())
    val moodShelves: StateFlow<Map<String, UiState<List<Track>>>> = _moodShelves

    init {
        moodShelfQueries.forEach { shelf ->
            viewModelScope.launch {
                // Same JioSaavn-first-with-YouTube-fallback pattern Search uses: both sources are
                // queried in parallel (so a slow one can't hold up the whole shelf), merged
                // (JioSaavn results first, then any YouTube ones that aren't already covered), and
                // only treated as a genuine failure if both come back empty/erroring - this is
                // what keeps a shelf resilient to a JioSaavn-side outage or regional throttling,
                // rather than depending on JioSaavn alone the way this shelf used to.
                var jioFailed = false
                var ytFailed = false
                val (jioResults, ytResults) = coroutineScope {
                    val jioDeferred = async {
                        withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                            try {
                                jioSaavnProvider.search(shelf.query).filter { it.directStreamUrl != null }
                            } catch (e: Exception) {
                                null
                            }
                        } ?: run { jioFailed = true; emptyList() }
                    }
                    val ytDeferred = async {
                        withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                            try {
                                youTubeProvider.search(shelf.query)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: run { ytFailed = true; emptyList() }
                    }
                    jioDeferred.await() to ytDeferred.await()
                }
                val merged = mergeSearchResults(jioResults, ytResults)
                    .take(10)
                    .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
                val state = if (jioFailed && ytFailed) {
                    UiState.Error("Couldn't load \"${shelf.title}\" right now.")
                } else {
                    UiState.Success(merged)
                }
                _moodShelves.update { it + (shelf.title to state) }
            }
        }
    }
}
