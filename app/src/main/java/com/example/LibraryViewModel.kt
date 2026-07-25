package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The sections Library can show, each backed by a different surviving repository. */
enum class LibrarySection(val label: String) {
    Playlists("Playlists"),
    Liked("Liked"),
    Downloads("Downloads"),
    TopPlayed("Top 50"),
    Recent("Recent"),
}

/**
 * Library state. Every list here comes from a repository that survived the frontend wipe - nothing
 * is fetched from the network, so Library works fully offline.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val likedRepository = LikedSongsRepository.getInstance(application)
    private val historyRepository = PlaybackHistoryRepository.getInstance(application)
    private val downloadedDao = MuseFlowDatabase.getInstance(application).downloadedTrackDao()

    private val _section = MutableStateFlow(LibrarySection.Playlists)
    val section: StateFlow<LibrarySection> = _section.asStateFlow()

    private val _trackSort = MutableStateFlow(TrackSortOption.DEFAULT)
    val trackSort: StateFlow<TrackSortOption> = _trackSort.asStateFlow()

    private val _playlistSort = MutableStateFlow(PlaylistSortOption.DEFAULT)
    val playlistSort: StateFlow<PlaylistSortOption> = _playlistSort.asStateFlow()

    private val _ascending = MutableStateFlow(true)
    val ascending: StateFlow<Boolean> = _ascending.asStateFlow()

    private val _gridView = MutableStateFlow(false)
    val gridView: StateFlow<Boolean> = _gridView.asStateFlow()

    fun setTrackSort(option: TrackSortOption) { _trackSort.value = option }
    fun setPlaylistSort(option: PlaylistSortOption) { _playlistSort.value = option }
    fun toggleDirection() { _ascending.value = !_ascending.value }
    fun toggleGridView() { _gridView.value = !_gridView.value }

    /** Applies the current sort to any track list. Kept as a function rather than pre-sorting each
     * StateFlow so one sort selection governs every section without duplicating the plumbing. */
    fun sortTracks(tracks: List<Track>): List<Track> =
        tracks.sortedByLibraryOption(_trackSort.value, _ascending.value)

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<Track>> = likedRepository.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<Track>> = downloadedDao.observeCompleted()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPlayed: StateFlow<List<Track>> = historyRepository.observeTopPlayed(50)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = historyRepository.observeRecent(50)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectSection(section: LibrarySection) {
        _section.value = section
    }

    fun tracksForPlaylist(playlistId: Long): StateFlow<List<Track>> =
        playlistRepository.observeTracks(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.create(name) }
    }
}

/** Rebuilds a playable [Track] from a completed download - [Track.streamUrl] points at the local
 * file, so playback never touches the network for a downloaded track. */
fun DownloadedTrackEntity.toTrack(): Track = Track(
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    plays = "",
    gradientIndex = gradientIndex,
    imageUrl = imageUrl,
    streamUrl = filePath,
    sourceType = sourceType?.let { saved -> runCatching { MusicSource.valueOf(saved) }.getOrNull() },
    sourceId = sourceId,
)
