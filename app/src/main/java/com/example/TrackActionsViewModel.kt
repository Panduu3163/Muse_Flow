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
    val downloadFailures = downloadRepository.failures

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

    /**
     * Likes or unlikes [tracks] as a batch.
     *
     * Takes the target state rather than toggling each track, because a mixed selection has no
     * sensible per-track toggle: half the rows would flip one way and half the other, and the user
     * asked for one thing. The caller decides the direction from what it can see.
     */
    fun setLiked(tracks: List<Track>, liked: Boolean) {
        viewModelScope.launch {
            tracks.forEach { track ->
                if (liked) likedRepository.like(track) else likedRepository.unlike(track)
            }
        }
    }

    fun download(track: Track) = downloadRepository.startDownload(track)

    /**
     * Queues a download for each of [tracks].
     *
     * The repository skips completed tracks and deduplicates concurrent requests, so this is safe
     * for a playlist that is only partly downloaded.
     */
    fun downloadAll(tracks: List<Track>) = tracks.forEach(downloadRepository::startDownload)

    /** Makes an online playlist a real Library item and downloads all of its tracks. */
    fun saveAndDownloadPlaylist(name: String, coverImageUrl: String?, tracks: List<Track>, remoteId: String? = null) {
        viewModelScope.launch {
            playlistRepository.importOnlinePlaylist(name, coverImageUrl, tracks, remoteId)
            tracks.forEach(downloadRepository::startDownload)
        }
    }

    fun cancelDownload(track: Track) = downloadRepository.cancelDownload(track)

    /** Deletes a completed download's file and its row, freeing the storage it holds. */
    fun deleteDownload(track: Track) = deleteDownloads(listOf(track))

    fun deleteDownloads(tracks: List<Track>) {
        viewModelScope.launch { tracks.forEach { downloadRepository.deleteDownload(it) } }
    }

    fun removeFromPlaylist(playlistId: Long, track: Track) =
        removeFromPlaylist(playlistId, listOf(track))

    fun removeFromPlaylist(playlistId: Long, tracks: List<Track>) {
        viewModelScope.launch {
            tracks.forEach { playlistRepository.removeTrack(playlistId, it.downloadKey()) }
        }
    }

    fun addToPlaylist(playlistId: Long, track: Track) = addToPlaylist(playlistId, listOf(track))

    fun addToPlaylist(playlistId: Long, tracks: List<Track>) {
        viewModelScope.launch { playlistRepository.addTracks(playlistId, tracks) }
    }

    fun createPlaylistWith(name: String, track: Track) = createPlaylistWith(name, listOf(track))

    fun createPlaylistWith(name: String, tracks: List<Track>) {
        viewModelScope.launch {
            val id = playlistRepository.create(name)
            playlistRepository.addTracks(id, tracks)
        }
    }

    /** One-tap "Add" for a readymade remote playlist (Search/Browse result) - saves it to Library
     * under its own name and cover, no naming prompt. Unlike [createPlaylistWith], the name and
     * artwork are already known (they came from the source playlist itself), so there is nothing
     * for the user to decide here. */
    fun addRemotePlaylistToLibrary(name: String, coverImageUrl: String?, tracks: List<Track>, remoteId: String? = null) {
        viewModelScope.launch { playlistRepository.importOnlinePlaylist(name, coverImageUrl, tracks, remoteId) }
    }
}
