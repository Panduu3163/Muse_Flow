package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.screens.asTrackResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A fetched shelf: [title] is what's shown, [query] is what's actually searched for - split
 * apart because a personalized shelf's title ("More Radiohead") isn't itself a good search query
 * for anything ("More Radiohead" the search vs "Radiohead" the artist). */
data class HomeShelfSpec(val title: String, val query: String)

/**
 * Home's state: entirely local data first (recently played, most played, playlists), plus a set of
 * shelves fetched through whichever extractor is selected - personalized to the user's own top
 * artists when there's enough listening history to have any, falling back to generic canned
 * mood/genre searches only for a new install with no history yet to draw from.
 *
 * Shelves are cached to Room ([HomeShelfCacheDao]) as they arrive and served from that cache on the
 * next launch, so Home renders instantly and still shows something useful with no connectivity -
 * a fetch failure falls back to the cached copy rather than an empty screen.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = PlaybackHistoryRepository.getInstance(application)
    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val likedSongsRepository = LikedSongsRepository.getInstance(application)
    private val shelfCacheDao = MuseFlowDatabase.getInstance(application).homeShelfCacheDao()
    private val searchRouter = MusicSearchRouter(application)

    /** Only shown for a brand new install: no play history yet means no listening habits to
     * personalize from. Replaced by real per-artist shelves the moment any history exists. */
    private val fallbackShelves = listOf(
        HomeShelfSpec("Trending Now", "Trending Now"),
        HomeShelfSpec("Chill Vibes", "Chill Vibes"),
        HomeShelfSpec("Workout Energy", "Workout Energy"),
        HomeShelfSpec("Throwback Hits", "Throwback Hits"),
    )

    val recentlyPlayed: StateFlow<List<Track>> = historyRepository.observeRecent(20)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPlayed: StateFlow<List<Track>> = historyRepository.observeTopPlayed(20)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Habit-driven shelves, computed from real listening history rather than fetched.
     *
     * "Forgotten favourites" is the interesting one: tracks with a high play count that haven't
     * been touched recently. That's genuinely useful *and* honest - it's derived from what the
     * user actually did, not a recommendation model pretending to know their taste.
     */
    val forgottenFavourites: StateFlow<List<Track>> =
        combine(topPlayed, recentlyPlayed) { top, recent ->
            val recentKeys = recent.take(15).map { it.downloadKey() }.toSet()
            top.filter { it.downloadKey() !in recentKeys }.take(12)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The user's own top artists, in the same rank order [observeTopPlayed] already sorts by -
     * this *is* their listening habits, not a guess at one. Capped at 4 to match how many shelves
     * Home showed before personalization; a listener with one dominant artist still gets that one
     * shelf rather than three empty-feeling repeats of it. */
    val topArtists: StateFlow<List<String>> = topPlayed
        .map { tracks -> tracks.mapNotNull { it.artist.takeIf(String::isNotBlank) }.distinct().take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** What Home's fetched-shelf row actually renders, one spec per shelf. */
    val shelfSpecs: StateFlow<List<HomeShelfSpec>> = topArtists
        .map { artists ->
            if (artists.isEmpty()) fallbackShelves else artists.map { HomeShelfSpec("More $it", it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), fallbackShelves)

    private val _shelves = MutableStateFlow<Map<String, UiState<List<Track>>>>(emptyMap())
    val shelves: StateFlow<Map<String, UiState<List<Track>>>> = _shelves.asStateFlow()

    val likedSongs: StateFlow<List<Track>> = likedSongsRepository.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** One related track per liked-song seed, from whichever extractor's radio/related endpoint
     * is available - Echo's "Daily Discover" without a login of its own to seed from. */
    private val _dailyDiscover = MutableStateFlow<UiState<List<Track>>>(UiState.Loading)
    val dailyDiscover: StateFlow<UiState<List<Track>>> = _dailyDiscover.asStateFlow()

    /** Third-party playlists surfaced from the user's own top artists, rather than a curated
     * editorial pick - "From the community" without an account to source community activity from. */
    private val _communityPlaylists = MutableStateFlow<UiState<List<PlaylistResult>>>(UiState.Loading)
    val communityPlaylists: StateFlow<UiState<List<PlaylistResult>>> = _communityPlaylists.asStateFlow()

    private var dailyDiscoverLoaded = false
    private var communityPlaylistsLoaded = false

    init {
        // Re-fetch whenever the user's top artists change (taste drift, or history existing for
        // the first time) - each spec already fetched is left alone, so a play recorded elsewhere
        // in the app doesn't restart every shelf's network call, only add/replace what changed.
        viewModelScope.launch {
            shelfSpecs.collect { specs ->
                _shelves.value = _shelves.value.filterKeys { title -> specs.any { it.title == title } }
                loadShelves(specs, forceRefresh = false)
            }
        }
        // Both loaded once per non-empty seed set, same guarded pattern as the shelf cache above -
        // a like/unlike elsewhere in the app shouldn't restart an in-flight or already-loaded fetch.
        viewModelScope.launch {
            likedSongs.collect { liked ->
                if (liked.isEmpty()) {
                    _dailyDiscover.value = UiState.Success(emptyList())
                } else if (!dailyDiscoverLoaded) {
                    dailyDiscoverLoaded = true
                    loadDailyDiscover(liked)
                }
            }
        }
        viewModelScope.launch {
            topArtists.collect { artists ->
                if (artists.isEmpty()) {
                    _communityPlaylists.value = UiState.Success(emptyList())
                } else if (!communityPlaylistsLoaded) {
                    communityPlaylistsLoaded = true
                    loadCommunityPlaylists(artists)
                }
            }
        }
    }

    fun refresh() {
        loadShelves(shelfSpecs.value, forceRefresh = true)
        dailyDiscoverLoaded = false
        communityPlaylistsLoaded = false
        likedSongs.value.takeIf { it.isNotEmpty() }?.let { loadDailyDiscover(it) }
        topArtists.value.takeIf { it.isNotEmpty() }?.let { loadCommunityPlaylists(it) }
    }

    private fun loadDailyDiscover(liked: List<Track>) {
        _dailyDiscover.value = UiState.Loading
        viewModelScope.launch {
            val seeds = liked.shuffled().take(5)
            val discovered = seeds.mapNotNull { seed ->
                val seedResult = seed.asTrackResult()
                if (!seedResult.hasRealVideoId()) return@mapNotNull null
                val radio = runCatching { searchRouter.getRadioTracks(seedResult.id) }.getOrDefault(emptyList())
                radio.firstOrNull { it.id != seedResult.id }?.toPlayableTrack("Daily Discover".hashCode())
            }
            _dailyDiscover.value = UiState.Success(discovered.distinctBy { it.downloadKey() })
        }
    }

    private fun loadCommunityPlaylists(artists: List<String>) {
        _communityPlaylists.value = UiState.Loading
        viewModelScope.launch {
            val results = artists.flatMap { artist ->
                runCatching { searchRouter.searchPlaylists(artist) }.getOrDefault(emptyList()).take(3)
            }
            _communityPlaylists.value = UiState.Success(results.distinctBy { it.id }.take(15))
        }
    }

    private fun loadShelves(specs: List<HomeShelfSpec>, forceRefresh: Boolean) {
        specs.forEach { spec ->
            if (!forceRefresh && _shelves.value[spec.title] != null) return@forEach
            _shelves.update(spec.title, UiState.Loading)
            viewModelScope.launch {
                // Cached copy first, so the shelf has content while the network call is in flight.
                val cached = runCatching { shelfCacheDao.get(spec.title) }.getOrNull()
                    ?.let { parseTracksJson(it.tracksJson) }
                    .orEmpty()
                if (cached.isNotEmpty()) {
                    _shelves.update(spec.title, UiState.Success(cached))
                }

                val fresh = runCatching { searchRouter.searchTracks(spec.query) }.getOrNull()
                    ?.map { it.toPlayableTrack(spec.title.hashCode()) }

                when {
                    !fresh.isNullOrEmpty() -> {
                        _shelves.update(spec.title, UiState.Success(fresh))
                        runCatching {
                            shelfCacheDao.upsert(
                                HomeShelfCacheEntity(
                                    shelfTitle = spec.title,
                                    tracksJson = fresh.toJson(),
                                    cachedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    // Network gave nothing but the cache already did - keep showing the cache.
                    cached.isNotEmpty() -> Unit
                    else -> _shelves.update(spec.title, UiState.Error("Couldn't load \"${spec.title}\" right now."))
                }
            }
        }
    }

    private fun MutableStateFlow<Map<String, UiState<List<Track>>>>.update(
        title: String,
        state: UiState<List<Track>>,
    ) {
        value = value.toMutableMap().apply { put(title, state) }
    }

    fun recordPlayed(track: Track) = historyRepository.recordPlayed(track)
}
