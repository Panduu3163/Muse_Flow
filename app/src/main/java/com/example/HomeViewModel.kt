package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * canned genre/mood search against JioSaavn via [Provider] - a stand-in for a real
 * recommendation system, which doesn't exist yet. Each shelf loads independently (its entry in
 * [moodShelves] is null until that one search resolves) so a slow or failed shelf doesn't block
 * the others from appearing.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)
    private val provider: Provider<TrackResult> = JioSaavnProvider()

    /** Null while the first read from Room is still in flight; empty once loaded with no history. */
    val recentlyPlayed: StateFlow<List<Track>?> = playbackHistoryRepository.observeRecent(10)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shelfTitles: List<String> = moodShelfQueries.map { it.title }

    /** Shelf title -> tracks. A missing/null value means that shelf's search hasn't resolved
     * yet; an empty list means it resolved but found nothing playable (e.g. offline). */
    private val _moodShelves = MutableStateFlow<Map<String, List<Track>?>>(emptyMap())
    val moodShelves: StateFlow<Map<String, List<Track>?>> = _moodShelves

    init {
        moodShelfQueries.forEach { shelf ->
            viewModelScope.launch {
                val tracks = runCatching {
                    provider.search(shelf.query)
                        .filter { it.directStreamUrl != null }
                        .take(10)
                        .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
                }.getOrDefault(emptyList())
                _moodShelves.update { it + (shelf.title to tracks) }
            }
        }
    }
}
