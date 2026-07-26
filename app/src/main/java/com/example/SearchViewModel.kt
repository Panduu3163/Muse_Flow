package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which kind of result the Search screen is showing. YouTube Music answers each of these with a
 * different search filter, so they are genuinely separate queries rather than one result set
 * sliced four ways. */
enum class SearchFilter(val label: String) {
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
}

/** Which kind of thing a [CollectionTracks] is. Artists are drawn round and albums square, and the
 * placeholder glyph differs, so the distinction survives past the fetch that flattens all three
 * into a plain tracklist. */
enum class CollectionKind { Album, Artist, Playlist }

/**
 * An album/artist/playlist the user opened from search, together with its tracks.
 *
 * These three collapse into one type because everything the sheet does with them is identical -
 * show a header, list tracks, play them. What differs is only how the tracks were fetched.
 */
data class CollectionTracks(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val kind: CollectionKind,
    val tracks: UiState<List<TrackResult>>,
    /** Set only for [CollectionKind.Artist] - the browseId the follow toggle acts on. Albums and
     * playlists aren't followable, so their sheets show no toggle at all. */
    val artist: ArtistResult? = null,
)

/**
 * Search state for the Search screen.
 *
 * Results are held as a [UiState] so the screen can distinguish "still loading" from "loaded, but
 * genuinely nothing matched" - a distinction that matters more than usual here, because with the
 * extractor router in strict mode an empty result is real evidence about the selected backend
 * rather than something to paper over.
 *
 * Each [SearchFilter] is fetched lazily, on first view: committing a query fires one request, not
 * four, and switching to a tab already loaded for that query re-shows it without a refetch.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val router = MusicSearchRouter(application)
    private val history = SearchHistoryRepository.getInstance(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.Songs)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<TrackResult>>>(UiState.Success(emptyList()))
    val results: StateFlow<UiState<List<TrackResult>>> = _results.asStateFlow()

    private val _albums = MutableStateFlow<UiState<List<AlbumResult>>>(UiState.Success(emptyList()))
    val albums: StateFlow<UiState<List<AlbumResult>>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<UiState<List<ArtistResult>>>(UiState.Success(emptyList()))
    val artists: StateFlow<UiState<List<ArtistResult>>> = _artists.asStateFlow()

    private val _playlists = MutableStateFlow<UiState<List<PlaylistResult>>>(UiState.Success(emptyList()))
    val playlists: StateFlow<UiState<List<PlaylistResult>>> = _playlists.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Whether a query has actually been run, so the screen can tell "nothing searched yet" from
     * "searched, and this filter genuinely has no matches" - which read identically before, both
     * being an empty list. */
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    /** Which backend actually served the visible results, surfaced in the UI so the extractor
     * toggle's effect is observable rather than guesswork. */
    private val _activeBackend = MutableStateFlow(ExtractorPreference.default)
    val activeBackend: StateFlow<ExtractorBackend> = _activeBackend.asStateFlow()

    val recentQueries = history.observeRecent()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    /** The query the user last committed, as opposed to what they are still typing. */
    private var committedQuery = ""

    /** Which query each filter's currently-held results belong to, so switching tabs back and
     * forth doesn't refetch what is already on screen. */
    private val loadedFor = mutableMapOf<SearchFilter, String>()

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        // Debounced so a fast typist doesn't fire a request per keystroke.
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            _suggestions.value = runCatching { router.suggestions(newQuery) }.getOrDefault(emptyList())
        }
    }

    fun search(query: String = _query.value) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        _query.value = trimmed
        _suggestions.value = emptyList()
        committedQuery = trimmed
        _hasSearched.value = true
        // A new query invalidates every tab, including the ones not currently visible.
        loadedFor.clear()
        history.record(trimmed)

        runSearch(trimmed, _filter.value)
    }

    fun selectFilter(filter: SearchFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        // Nothing to show for a tab the user hasn't committed a query for yet.
        if (committedQuery.isEmpty() || loadedFor[filter] == committedQuery) return
        runSearch(committedQuery, filter)
    }

    /**
     * Fetches one filter's results.
     *
     * Only one search runs at a time: switching tabs mid-flight cancels the previous request
     * rather than racing it, since its results are no longer the ones on screen.
     */
    private fun runSearch(query: String, filter: SearchFilter) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _activeBackend.value = StreamResolverRouter.activeBackend(getApplication())

            val succeeded = when (filter) {
                SearchFilter.Songs -> load(_results) { router.searchTracks(query) }
                SearchFilter.Albums -> load(_albums) { router.searchAlbums(query) }
                SearchFilter.Artists -> load(_artists) { router.searchArtists(query) }
                SearchFilter.Playlists -> load(_playlists) { router.searchPlaylists(query) }
            }

            // Only a success is worth remembering - a failed tab should retry when revisited.
            if (succeeded) loadedFor[filter] = query
        }
    }

    /** Drives one result flow through loading -> success/error, reporting whether it succeeded. */
    private suspend fun <T> load(
        state: MutableStateFlow<UiState<List<T>>>,
        fetch: suspend () -> List<T>,
    ): Boolean {
        state.value = UiState.Loading
        return runCatching { fetch() }.fold(
            onSuccess = {
                state.value = UiState.Success(it)
                true
            },
            onFailure = {
                state.value = errorState(it)
                false
            },
        )
    }

    /**
     * Being offline is by far the most common failure and isn't something the user can act on from
     * a stack-trace-flavoured message, so it gets plain language. Anything else still names the
     * backend, which is what makes a genuine extractor problem diagnosable.
     */
    private fun errorState(error: Throwable): UiState.Error = UiState.Error(
        if (!isOnline(getApplication())) {
            "Oops! You don't have internet. Connect and try again."
        } else {
            "${_activeBackend.value.label} search failed: " +
                (error.message ?: error::class.simpleName ?: "unknown error")
        }
    )

    fun clearQuery() {
        searchJob?.cancel()
        suggestJob?.cancel()
        _query.value = ""
        _suggestions.value = emptyList()
        committedQuery = ""
        _hasSearched.value = false
        loadedFor.clear()
        _results.value = UiState.Success(emptyList())
        _albums.value = UiState.Success(emptyList())
        _artists.value = UiState.Success(emptyList())
        _playlists.value = UiState.Success(emptyList())
    }

    fun deleteRecent(query: String) = history.delete(query)
}
