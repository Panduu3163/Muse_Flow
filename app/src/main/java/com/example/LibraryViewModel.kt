package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.models.upgradeThumbnailSize
import kotlinx.coroutines.Job
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
    OnDevice("On device"),
    Following("Following"),
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
    private val followedArtistsRepository = FollowedArtistsRepository.getInstance(application)

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

    /** Backs Library's "Following" section - the list that "no way to see who you follow" gap
     * (follow/unfollow only ever existed on the artist sheet) was blocking on. */
    val followedArtists: StateFlow<List<FollowedArtistEntity>> = followedArtistsRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unfollowArtist(artistId: String) = followedArtistsRepository.unfollow(artistId)

    /**
     * The device's own music files.
     *
     * Unlike every other section this isn't a Room [kotlinx.coroutines.flow.Flow] - MediaStore is
     * queried, not observed - so it's a [UiState] the screen refreshes explicitly rather than
     * something that keeps itself current.
     */
    private val _localTracks = MutableStateFlow<UiState<List<Track>>>(UiState.Success(emptyList()))
    val localTracks: StateFlow<UiState<List<Track>>> = _localTracks.asStateFlow()

    private var localScanJob: Job? = null

    /**
     * Rescans the device for audio files.
     *
     * Called when the section is opened rather than once at startup, so music added since the app
     * launched shows up on the next visit. A rescan already in flight is cancelled - reopening the
     * section twice quickly shouldn't queue two MediaStore sweeps.
     */
    fun scanLocalTracks() {
        localScanJob?.cancel()
        localScanJob = viewModelScope.launch {
            _localTracks.value = UiState.Loading
            _localTracks.value = runCatching {
                LocalAudioProvider(getApplication()).search("").map { it.toLocalTrack() }
            }.fold(
                onSuccess = { UiState.Success(it) },
                // In practice this is a SecurityException from the permission being revoked while
                // the app was backgrounded - the screen's permission gate covers the normal case.
                onFailure = { UiState.Error("Couldn't read files on this device.") },
            )
        }
    }

    fun selectSection(section: LibrarySection) {
        _section.value = section
    }

    /**
     * One flow per playlist, cached.
     *
     * The cache is the whole point: this is called from composition, and building the flow inline
     * meant every recomposition created a *new* [StateFlow] starting at `emptyList()`. The detail
     * screen drew an empty list, Room refilled it, that recomposed, and round it went - a visible
     * flicker, plus a leaked collector per pass. Returning the same instance makes the read stable.
     */
    private val playlistTrackFlows = mutableMapOf<Long, StateFlow<List<Track>>>()

    fun tracksForPlaylist(playlistId: Long): StateFlow<List<Track>> =
        playlistTrackFlows.getOrPut(playlistId) {
            playlistRepository.observeTracks(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.create(name) }
    }

    fun togglePin(playlist: PlaylistEntity) {
        viewModelScope.launch { playlistRepository.setPinned(playlist.id, !playlist.isPinned) }
    }

    fun setCustomCover(playlistId: Long, uri: String?) {
        viewModelScope.launch { playlistRepository.setCustomCover(playlistId, uri) }
    }

    /** Also drops the playlist's own cached track flow - otherwise a new playlist created later
     * could reuse the freed id and inherit a stale [StateFlow] still holding the deleted tracks. */
    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch { playlistRepository.delete(playlist.id) }
        playlistTrackFlows.remove(playlist.id)
    }
}

/**
 * A MediaStore result as a Library [Track].
 *
 * [Track.streamUrl] keeps the `content://` URI so the file plays straight from disk with no
 * resolve step, and [Track.sourceType] stays [MusicSource.LOCAL_DEVICE] so nothing downstream
 * mistakes it for something that needs fetching from YouTube.
 */
private fun TrackResult.toLocalTrack(): Track = Track(
    title = title,
    artist = artist,
    album = source,
    duration = duration ?: "-:--",
    plays = "",
    gradientIndex = id.hashCode(),
    imageUrl = imageUrl,
    streamUrl = directStreamUrl,
    sourceType = sourceType,
    sourceId = id,
)

/** Rebuilds a playable [Track] from a completed download - [Track.streamUrl] points at the local
 * file, so playback never touches the network for a downloaded track. */
fun DownloadedTrackEntity.toTrack(): Track = Track(
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    plays = "",
    gradientIndex = gradientIndex,
    // Upgraded at read time - see PlaybackHistoryEntity.toTrack's comment on why.
    imageUrl = imageUrl?.let(::upgradeThumbnailSize),
    streamUrl = filePath,
    sourceType = sourceType?.let { saved -> runCatching { MusicSource.valueOf(saved) }.getOrNull() },
    sourceId = sourceId,
    albumId = albumId,
    artistId = artistId,
)
