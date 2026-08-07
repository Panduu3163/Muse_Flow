package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val playbackHistory = PlaybackHistoryRepository.getInstance(application)

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

    /** Set once the Songs tab's direct search matches run out and the list starts extending with
     * personalized recommendations instead - the index in [results] where that switch happened,
     * so the screen can drop a section header there. Null while every visible row is still a
     * direct match, or before any "load more" has run at all. */
    private val _recommendationsStartAt = MutableStateFlow<Int?>(null)
    val recommendationsStartAt: StateFlow<Int?> = _recommendationsStartAt.asStateFlow()

    /** Whether a "load more" is in flight, so the Songs tab can show a footer spinner and
     * [loadMoreSongs] can ignore a second scroll-triggered call that arrives before the first
     * one finishes. */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val recentQueries = history.observeRecent()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var loadMoreJob: Job? = null

    /** The query the user last committed, as opposed to what they are still typing. */
    private var committedQuery = ""

    /** Which query each filter's currently-held results belong to, so switching tabs back and
     * forth doesn't refetch what is already on screen. */
    private val loadedFor = mutableMapOf<SearchFilter, String>()

    // --- Songs-tab pagination / recommendation-blend state ---------------------------------
    // All reset together at the top of [loadSongs], every time a *new* query is committed - never
    // reset by a tab switch, so scrolling away and back to Songs resumes exactly where it left off.

    /** Where [MusicSearchRouter.searchTracksContinuation] resumes; null once real search matches
     * have run out (whether from a genuinely short result set or having paged through all of it) -
     * that's what tells [loadMoreSongs] to switch into recommendation mode. */
    private var searchContinuation: String? = null

    /** Where [MusicSearchRouter.getRadioTracksPage] resumes for the current recommendation seed;
     * null when that seed's radio has run dry and a fresh one is needed. */
    private var radioContinuation: String? = null

    /** The video id the current recommendation batch is a radio mix of - null until the feed
     * actually transitions into recommendations for the first time. */
    private var radioSeedVideoId: String? = null

    /** Every video id already used to seed a radio this session, so re-seeding after one mix runs
     * dry doesn't immediately loop back to the same mix. */
    private val usedSeedIds = mutableSetOf<String>()

    /** Every track id already shown (search matches and recommendations both), so a track that
     * would otherwise appear in both halves of the feed - or reappear across two radio batches -
     * only ever shows once. */
    private val seenTrackIds = mutableSetOf<String>()

    /** Set once neither more search pages nor a fresh recommendation seed are available - stops
     * [loadMoreSongs] from firing requests a genuinely finished feed can't answer. */
    private var songsFeedExhausted = false

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
                SearchFilter.Songs -> loadSongs(query)
                SearchFilter.Albums -> load(_albums) { router.searchAlbums(query) }
                SearchFilter.Artists -> load(_artists) { router.searchArtists(query) }
                SearchFilter.Playlists -> load(_playlists) { router.searchPlaylists(query) }
            }

            // Only a success is worth remembering - a failed tab should retry when revisited.
            if (succeeded) loadedFor[filter] = query
        }
    }

    /**
     * Fetches the first page of a songs search and resets all the pagination/recommendation state
     * [loadMoreSongs] continues from - a fresh committed query always starts the blended feed over
     * from its "direct matches" phase, never mid-recommendation.
     */
    private suspend fun loadSongs(query: String): Boolean {
        loadMoreJob?.cancel()
        searchContinuation = null
        radioContinuation = null
        radioSeedVideoId = null
        usedSeedIds.clear()
        seenTrackIds.clear()
        songsFeedExhausted = false
        _recommendationsStartAt.value = null
        _isLoadingMore.value = false

        _results.value = UiState.Loading
        return runCatching { router.searchTracksPage(query) }.fold(
            onSuccess = { page ->
                searchContinuation = page.continuation
                seenTrackIds += page.items.map { it.id }
                _results.value = UiState.Success(page.items)
                true
            },
            onFailure = {
                _results.value = errorState(it)
                false
            },
        )
    }

    /**
     * Extends the Songs tab past its current results - called when the list has been scrolled
     * near its end. Structured as one continuous feed with two phases behind a single call, so the
     * screen never has to know which phase it's in: while [searchContinuation] is still set this
     * pages through more of YouTube Music's own search-relevance ranking; once that runs out it
     * switches to a radio mix seeded from the best real match already on screen, blended once with
     * a taste-weighted pick from play history (the same blend [PlaybackService.autoplayRelated]
     * uses) so the very first recommendation batch reflects the user's broader taste and not just
     * one song's mix. If a radio mix itself runs dry, a fresh one is re-seeded from history rather
     * than ending the feed outright - matching a real "infinite scroll of recommendations" rather
     * than one fixed batch tacked onto the end of search.
     */
    fun loadMoreSongs() {
        if (_filter.value != SearchFilter.Songs) return
        if (_isLoadingMore.value || songsFeedExhausted) return
        val current = (_results.value as? UiState.Success)?.data ?: return
        if (current.isEmpty()) return // Nothing to extend from - a failed/empty search has no seed.

        loadMoreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val wasSearchPhase = searchContinuation != null
                val fetched = if (wasSearchPhase) {
                    router.searchTracksContinuation(searchContinuation!!)
                        .also { searchContinuation = it.continuation }
                        .items
                } else {
                    nextRecommendationBatch(current)
                }

                val fresh = fetched.filter { it.id !in seenTrackIds }
                if (fresh.isEmpty()) {
                    // An empty page while still mid-search just means try the next scroll trigger
                    // later; an empty page with nothing left to page through at all is real EOF.
                    if (searchContinuation == null && radioContinuation == null) songsFeedExhausted = true
                    return@launch
                }

                seenTrackIds += fresh.map { it.id }
                if (wasSearchPhase && searchContinuation == null) {
                    // This batch was the tail of search - everything appended from here on is
                    // recommendations, so the boundary sits right after it.
                    _recommendationsStartAt.value = current.size + fresh.size
                } else if (_recommendationsStartAt.value == null && !wasSearchPhase) {
                    _recommendationsStartAt.value = current.size
                }
                _results.value = UiState.Success(current + fresh)
            } catch (_: Exception) {
                // A failed "load more" leaves existing results exactly as they were - retried on
                // the next scroll trigger rather than surfacing as a full-screen error.
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /** One batch of the recommendation tail: continues the current radio seed's own pagination,
     * or - if that seed has run dry - picks a fresh one and starts a new radio chain from it. */
    private suspend fun nextRecommendationBatch(current: List<TrackResult>): List<TrackResult> {
        if (radioSeedVideoId != null) {
            val page = runCatching { router.getRadioTracksPage(radioSeedVideoId!!, radioContinuation) }
                .getOrNull() ?: return emptyList()
            radioContinuation = page.continuation
            if (page.items.isNotEmpty()) return page.items
            // Fall through to re-seeding: an exhausted mix, not a genuine failure.
        }

        val seed = pickRecommendationSeed(current) ?: run {
            songsFeedExhausted = true
            return emptyList()
        }
        usedSeedIds += seed
        radioSeedVideoId = seed
        radioContinuation = null

        val seedPage = runCatching { router.getRadioTracksPage(seed) }.getOrNull() ?: return emptyList()
        radioContinuation = seedPage.continuation

        // Only on the very first recommendation batch: blend in one taste-weighted pick from play
        // history, interleaved with the seed's own mix, so the feed opens on "search term + your
        // taste" rather than a single song's mix alone.
        if (_recommendationsStartAt.value != null) return seedPage.items
        val taste = runCatching { playbackHistory.observeTopPlayed(15).first() }.getOrNull().orEmpty()
            .pickTasteSeed(exclude = usedSeedIds)
        val tasteResults = taste?.sourceId?.let { tasteSeedId ->
            usedSeedIds += tasteSeedId
            runCatching { router.getRadioTracksPage(tasteSeedId) }.getOrNull()?.items
        }.orEmpty()

        return interleaveTwoToOne(seedPage.items, tasteResults)
    }

    /** The seed a fresh recommendation radio starts from: the first real-video-id track not
     * already used as a seed, preferring the most recently added (search matches first, then
     * earlier recommendation batches) since that's the closest signal to what the user is
     * currently looking at. */
    private fun pickRecommendationSeed(current: List<TrackResult>): String? =
        current.asReversed()
            .asSequence()
            .filter { it.hasRealVideoId() }
            .map { it.id }
            .firstOrNull { it !in usedSeedIds }

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
        loadMoreJob?.cancel()
        _query.value = ""
        _suggestions.value = emptyList()
        committedQuery = ""
        _hasSearched.value = false
        loadedFor.clear()
        _results.value = UiState.Success(emptyList())
        _albums.value = UiState.Success(emptyList())
        _artists.value = UiState.Success(emptyList())
        _playlists.value = UiState.Success(emptyList())
        searchContinuation = null
        radioContinuation = null
        radioSeedVideoId = null
        usedSeedIds.clear()
        seenTrackIds.clear()
        songsFeedExhausted = false
        _recommendationsStartAt.value = null
        _isLoadingMore.value = false
    }

    fun deleteRecent(query: String) = history.delete(query)
}
