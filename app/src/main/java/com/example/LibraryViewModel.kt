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

    /** One query for whichever section is currently open - see [selectSection], which clears it
     * on every section switch so a filter set on "Downloads" doesn't silently hide everything the
     * next time "Playlists" opens. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setTrackSort(option: TrackSortOption) { _trackSort.value = option }
    fun setPlaylistSort(option: PlaylistSortOption) { _playlistSort.value = option }
    fun toggleDirection() { _ascending.value = !_ascending.value }
    fun toggleGridView() { _gridView.value = !_gridView.value }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /** Applies the current search query, then the current sort, to any track list - filtering
     * folded in here rather than as a separate call site covers every section that already routes
     * through this function (Liked/Downloads/Top 50/Recent/On device) with this one change.
     *
     * While a search is active, relevance replaces the user's chosen sort entirely (same as any
     * real search box - "closest match first" is the point, not an alphabetical/date ordering of
     * whatever happened to match). The chosen sort still applies as-is once the query is cleared. */
    fun sortTracks(tracks: List<Track>): List<Track> {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return tracks.sortedByLibraryOption(_trackSort.value, _ascending.value)
        return tracks
            .mapNotNull { track ->
                val score = listOfNotNull(matchRank(track.title, query), matchRank(track.artist, query)).minOrNull()
                score?.let { track to it }
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** Same query, applied to Playlists - the one section [sortTracks] doesn't cover, since a
     * playlist isn't a [Track]. */
    fun filterPlaylists(playlists: List<PlaylistEntity>): List<PlaylistEntity> {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return playlists
        return playlists.mapNotNull { p -> matchRank(p.name, query)?.let { p to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** Same query, applied to Following. */
    fun filterArtists(artists: List<FollowedArtistEntity>): List<FollowedArtistEntity> {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return artists
        return artists.mapNotNull { a -> matchRank(a.name, query)?.let { a to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<Track>> = likedRepository.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<Track>> = downloadedDao.observeCompleted()
        .map { entities -> entities.map { it.toTrack(application) } }
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
        _searchQuery.value = ""
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
 * How well [text] matches [query], as a rank where lower is a better match - or null when it
 * doesn't match at all. Plain substring-contains (what this used to be) ranks every match
 * identically regardless of where it lands, so "Her" surfaced a song with "her" buried mid-title
 * above the actual "Her" track, in whatever order the underlying list happened to already be in.
 * This ranks an exact match first, then a prefix match, then a match at a word boundary (the start
 * of any word, not just the whole string), then anywhere else - the same rough tiering a real
 * search box uses, instead of "matched or didn't".
 */
private fun matchRank(text: String, query: String): Int? {
    if (query.isEmpty()) return 0
    val t = text.trim()
    val q = query.trim()
    return when {
        t.equals(q, ignoreCase = true) -> 0
        t.startsWith(q, ignoreCase = true) -> 1
        Regex("\\b${Regex.escape(q)}", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 2
        t.contains(q, ignoreCase = true) -> 3
        else -> null
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
 * file, so playback never touches the network for a downloaded track. [imageUrl] prefers the
 * locally-saved cover ([DownloadRepository.localCoverFile]) over the original remote thumbnail -
 * see that function's own doc for why the remote URL alone isn't reliable offline. */
fun DownloadedTrackEntity.toTrack(context: android.content.Context): Track = Track(
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    plays = "",
    gradientIndex = gradientIndex,
    // A bare absolute path string (`File.absolutePath`) isn't reliably a Coil-loadable model -
    // its String mapper resolves by URI scheme, and a path with no "file://" prefix doesn't
    // parse as one. Android's Uri.fromFile is what actually gets a real, scheme-qualified URI.
    imageUrl = DownloadRepository.localCoverFile(context, key)?.let { android.net.Uri.fromFile(it).toString() }
        ?: imageUrl?.let(::upgradeThumbnailSize),
    streamUrl = filePath,
    sourceType = sourceType?.let { saved -> runCatching { MusicSource.valueOf(saved) }.getOrNull() },
    sourceId = sourceId,
    albumId = albumId,
    artistId = artistId,
)
