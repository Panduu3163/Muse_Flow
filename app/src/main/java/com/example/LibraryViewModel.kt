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
 * Facade for the parts of Library that aren't covered by [DownloadViewModel] (Downloads tab) or
 * [LikedSongsViewModel] (Liked Songs tab): the user's created playlists and real playback
 * history, both Room-backed so they start empty on a fresh install and update live.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = playbackHistoryRepository.observeRecent(50)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepository.create(name) }
    }
}
