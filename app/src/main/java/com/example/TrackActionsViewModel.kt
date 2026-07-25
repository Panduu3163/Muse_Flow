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
 * Like/download actions for a single track, shared by every list that shows one.
 *
 * Both repositories already existed and survived the frontend wipe - until now nothing in the
 * rebuilt UI called them, which is why Library's Liked and Downloads sections could never fill up.
 */
class TrackActionsViewModel(application: Application) : AndroidViewModel(application) {

    private val likedRepository = LikedSongsRepository.getInstance(application)
    private val downloadRepository = DownloadRepository.getInstance(application)
    private val playlistRepository = PlaylistRepository.getInstance(application)

    /** Keys of every liked track, so a list can render its heart states from one subscription
     * rather than one Flow per row. */
    val likedKeys: StateFlow<Set<String>> = likedRepository.observeAll()
        .map { entities -> entities.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> =
        MuseFlowDatabase.getInstance(application).downloadedTrackDao().observeCompleted()
            .map { entities -> entities.map { it.key }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadsInProgress: StateFlow<Map<String, Int>> = downloadRepository.inProgress

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleLike(track: Track) {
        viewModelScope.launch {
            if (likedKeys.value.contains(track.downloadKey())) {
                likedRepository.unlike(track)
            } else {
                likedRepository.like(track)
            }
        }
    }

    fun download(track: Track) = downloadRepository.startDownload(track)

    fun cancelDownload(track: Track) = downloadRepository.cancelDownload(track)

    fun addToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch { playlistRepository.addTracks(playlistId, listOf(track)) }
    }

    fun createPlaylistWith(name: String, track: Track) {
        viewModelScope.launch {
            val id = playlistRepository.create(name)
            playlistRepository.addTracks(id, listOf(track))
        }
    }
}
