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
 * Facade for the parts of Library that aren't covered by [DownloadViewModel] (Downloads tile) or
 * [LikedSongsViewModel] (Liked tile): the user's created playlists, "My Top 50" (real play
 * counts, not just recency), and "Cached" (whatever Home has actually cached for offline
 * browsing) - all Room-backed so they start empty on a fresh install and update live.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)
    private val shelfCacheDao = MuseFlowDatabase.getInstance(application).homeShelfCacheDao()

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPlayed: StateFlow<List<Track>> = playbackHistoryRepository.observeTopPlayed(50)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whatever Home's offline shelf cache actually has on disk right now, deduped across shelves
     * - real cached content, the same one Home falls back to when offline, not a separate cache
     * of its own. */
    val cachedTracks: StateFlow<List<Track>> = shelfCacheDao.observeAll()
        .map { shelves ->
            shelves.flatMap { parseTracksJson(it.tracksJson) }.distinctBy { it.downloadKey() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepository.create(name) }
    }

    /** Creates a new playlist with [tracks] already in it - the "+ New Playlist" flow's
     * add-songs step, after naming. */
    fun createPlaylistWithTracks(name: String, tracks: List<Track>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = playlistRepository.create(name)
            if (tracks.isNotEmpty()) playlistRepository.addTracks(id, tracks)
        }
    }

    /** Saves a full online playlist (real cover + tracks) as a new local playlist - "Add to my
     * library" on an online playlist's detail screen. */
    fun importPlaylist(name: String, coverImageUrl: String?, tracks: List<Track>) {
        if (name.isBlank() || tracks.isEmpty()) return
        viewModelScope.launch { playlistRepository.importOnlinePlaylist(name, coverImageUrl, tracks) }
    }

    fun removeTrackFromPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, track.downloadKey()) }
    }

    // Cached per playlist id so every card/detail screen observing the same playlist shares one
    // collector instead of each recomposition spinning up its own - same pattern would apply if
    // this ever needed manual invalidation, but playlists are never deleted today so it doesn't.
    private val playlistTracksCache = mutableMapOf<Long, StateFlow<List<Track>>>()

    fun tracksForPlaylist(playlistId: Long): StateFlow<List<Track>> =
        playlistTracksCache.getOrPut(playlistId) {
            playlistRepository.observeTracks(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
}
