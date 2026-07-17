package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Facade over [LikedSongsRepository] for the UI: which tracks are liked (for Library's Liked
 * Songs tab), and the toggle action (used by the heart button on Now Playing and, per-row, in
 * Library itself).
 */
class LikedSongsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LikedSongsRepository.getInstance(application)

    val likedSongs: StateFlow<List<Track>> = repository.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedKeys: StateFlow<Set<String>> = likedSongs
        .map { tracks -> tracks.map { it.downloadKey() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun isLiked(track: Track): Boolean = likedKeys.value.contains(track.downloadKey())

    fun toggle(track: Track) {
        viewModelScope.launch {
            if (isLiked(track)) repository.unlike(track) else repository.like(track)
        }
    }
}
