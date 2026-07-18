package com.example

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Search's two data sources: [ONLINE] is the merged JioSaavn+YouTube Music search; [ON_DEVICE]
 * scans local audio files via [LocalAudioProvider]/MediaStore instead - no network involved. */
enum class SearchMode { ONLINE, ON_DEVICE }

/**
 * Holds Search's UI state (query text, mode, selected tab) and every tab's fetched results,
 * scoped via `viewModel()` to Search's own [androidx.navigation.NavBackStackEntry]. That entry -
 * and this ViewModel along with it - survives being pushed under Now Playing and popped back to;
 * the plain `remember{}` state SearchScreen used to hold didn't, since its whole composition gets
 * torn down and rebuilt fresh across that navigation, discarding anything not backed by something
 * that outlives it.
 *
 * Each tab's fetch is idempotent per query (see the `xFetchedFor` fields) - re-entering the
 * screen re-triggers the same `LaunchedEffect(searchQuery)` call as always, but it's now a no-op
 * if that tab already has (or is already fetching) results for the current query, rather than
 * firing a fresh network round-trip every time.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    val searchQueryState = mutableStateOf("")
    var searchQuery: String
        get() = searchQueryState.value
        set(value) { searchQueryState.value = value }

    val selectedTabState = mutableStateOf(0)
    var selectedTab: Int
        get() = selectedTabState.value
        set(value) { selectedTabState.value = value }

    // True once the user has actually submitted a search (IME search action, or tapping a recent
    // query) - gates the tab row/results away entirely until then, rather than showing them as
    // soon as the user starts typing. Reset back to false when the query is cleared to empty, so
    // clearing the bar returns to the same bare, pre-search state.
    val hasSubmittedState = mutableStateOf(false)
    var hasSubmitted: Boolean
        get() = hasSubmittedState.value
        set(value) { hasSubmittedState.value = value }

    val searchModeState = mutableStateOf(SearchMode.ONLINE)
    var searchMode: SearchMode
        get() = searchModeState.value
        set(value) { searchModeState.value = value }

    val songsResultState = mutableStateOf<UiState<List<Track>>>(UiState.Loading)
    val albumsResultState = mutableStateOf<UiState<List<AlbumResult>>>(UiState.Loading)
    val artistsResultState = mutableStateOf<UiState<List<ArtistResult>>>(UiState.Loading)
    val playlistsResultState = mutableStateOf<UiState<List<PlaylistResult>>>(UiState.Loading)
    val onDeviceResultState = mutableStateOf<UiState<List<Track>>>(UiState.Loading)

    private val jioSaavnProvider = JioSaavnProvider()
    private val youTubeProvider = YouTubeMusicProvider(application)
    private val localAudioProvider = LocalAudioProvider(application)

    private var songsFetchedFor: String? = null
    private var albumsFetchedFor: String? = null
    private var artistsFetchedFor: String? = null
    private var playlistsFetchedFor: String? = null
    private var onDeviceFetchedFor: Pair<String, Boolean>? = null

    private var songsJob: Job? = null
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var playlistsJob: Job? = null
    private var onDeviceJob: Job? = null

    fun ensureSongsLoaded() {
        val query = searchQuery
        if (songsFetchedFor == query) return
        songsFetchedFor = query
        songsJob?.cancel()
        if (query.isBlank()) {
            songsResultState.value = UiState.Loading
            return
        }
        songsResultState.value = UiState.Loading
        songsJob = viewModelScope.launch {
            delay(350) // debounce so we don't fire a search per keystroke
            var jioFailed = false
            var ytFailed = false
            val (jioResults, ytResults) = coroutineScope {
                val jioDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            jioSaavnProvider.search(query).filter { it.directStreamUrl != null }
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { jioFailed = true; emptyList() }
                }
                val ytDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            youTubeProvider.search(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { ytFailed = true; emptyList() }
                }
                jioDeferred.await() to ytDeferred.await()
            }
            val merged = mergeSearchResults(query, jioResults, ytResults)
                .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
            songsResultState.value = if (jioFailed && ytFailed) {
                UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
            } else {
                UiState.Success(merged)
            }
        }
    }

    fun ensureAlbumsLoaded() {
        val query = searchQuery
        if (albumsFetchedFor == query) return
        albumsFetchedFor = query
        albumsJob?.cancel()
        if (query.isBlank()) {
            albumsResultState.value = UiState.Loading
            return
        }
        albumsResultState.value = UiState.Loading
        albumsJob = viewModelScope.launch {
            delay(350)
            var jioFailed = false
            var ytFailed = false
            val (jioResults, ytResults) = coroutineScope {
                val jioDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            jioSaavnProvider.searchAlbums(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { jioFailed = true; emptyList() }
                }
                val ytDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            youTubeProvider.searchAlbums(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { ytFailed = true; emptyList() }
                }
                jioDeferred.await() to ytDeferred.await()
            }
            albumsResultState.value = if (jioFailed && ytFailed) {
                UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
            } else {
                UiState.Success(mergeAlbumResults(jioResults, ytResults))
            }
        }
    }

    fun ensureArtistsLoaded() {
        val query = searchQuery
        if (artistsFetchedFor == query) return
        artistsFetchedFor = query
        artistsJob?.cancel()
        if (query.isBlank()) {
            artistsResultState.value = UiState.Loading
            return
        }
        artistsResultState.value = UiState.Loading
        artistsJob = viewModelScope.launch {
            delay(350)
            var jioFailed = false
            var ytFailed = false
            val (jioResults, ytResults) = coroutineScope {
                val jioDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            jioSaavnProvider.searchArtists(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { jioFailed = true; emptyList() }
                }
                val ytDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            youTubeProvider.searchArtists(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { ytFailed = true; emptyList() }
                }
                jioDeferred.await() to ytDeferred.await()
            }
            artistsResultState.value = if (jioFailed && ytFailed) {
                UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
            } else {
                UiState.Success(mergeArtistResults(jioResults, ytResults))
            }
        }
    }

    fun ensurePlaylistsLoaded() {
        val query = searchQuery
        if (playlistsFetchedFor == query) return
        playlistsFetchedFor = query
        playlistsJob?.cancel()
        if (query.isBlank()) {
            playlistsResultState.value = UiState.Loading
            return
        }
        playlistsResultState.value = UiState.Loading
        playlistsJob = viewModelScope.launch {
            delay(350)
            var jioFailed = false
            var ytFailed = false
            val (jioResults, ytResults) = coroutineScope {
                val jioDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            jioSaavnProvider.searchPlaylists(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { jioFailed = true; emptyList() }
                }
                val ytDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        try {
                            youTubeProvider.searchPlaylists(query)
                        } catch (e: Exception) {
                            null
                        }
                    } ?: run { ytFailed = true; emptyList() }
                }
                jioDeferred.await() to ytDeferred.await()
            }
            playlistsResultState.value = if (jioFailed && ytFailed) {
                UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
            } else {
                UiState.Success(mergePlaylistResults(jioResults, ytResults))
            }
        }
    }

    fun ensureOnDeviceLoaded(hasPermission: Boolean) {
        if (!hasPermission) return
        val query = searchQuery
        val key = query to hasPermission
        if (onDeviceFetchedFor == key) return
        onDeviceFetchedFor = key
        onDeviceJob?.cancel()
        onDeviceResultState.value = UiState.Loading
        onDeviceJob = viewModelScope.launch {
            delay(150) // still worth a light debounce so fast typing doesn't fire a query per key
            onDeviceResultState.value = loadAsUiState(errorMessage = "Couldn't scan audio files on this device.") {
                localAudioProvider.search(query)
                    .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
            }
        }
    }
}
