package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.screens.asTrackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    /** Same cached-per-id pattern as [LibraryViewModel.tracksForPlaylist] - the "Your playlists"
     * shelf needs each playlist's own tracks too now, to build the same mosaic-cover fallback
     * Library's grid already has for a playlist with no [PlaylistEntity.coverImageUrl] (e.g. one
     * that came in through a Spotify import, which never sets one). Building the flow inline in
     * the composable instead of caching it here would start a fresh empty-first collector on every
     * recomposition - the same flicker [LibraryViewModel]'s own doc warns about. */
    private val playlistTrackFlows = mutableMapOf<Long, StateFlow<List<Track>>>()

    fun tracksForPlaylist(playlistId: Long): StateFlow<List<Track>> =
        playlistTrackFlows.getOrPut(playlistId) {
            playlistRepository.observeTracks(playlistId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

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
    private var trendingFallbackLoaded = false
    private var communityPlaylistsLoaded = false

    init {
        // Re-fetch whenever the user's top artists change (taste drift, or history existing for
        // the first time) - each spec already fetched is left alone, so a play recorded elsewhere
        // in the app doesn't restart every shelf's network call, only add/replace what changed.
        viewModelScope.launch(Dispatchers.Default) {
            shelfSpecs.collect { specs ->
                _shelves.value = _shelves.value.filterKeys { title -> specs.any { it.title == title } }
                loadShelves(specs, forceRefresh = false)
            }
        }
        // Both loaded once per non-empty seed set, same guarded pattern as the shelf cache above -
        // a like/unlike elsewhere in the app shouldn't restart an in-flight or already-loaded fetch.
        viewModelScope.launch(Dispatchers.Default) {
            likedSongs.collect { liked ->
                if (liked.isEmpty()) {
                    // A brand-new install has nothing liked yet to seed a personalized radio from
                    // - without this, dailyDiscover (and Home's hero carousel, which reads it)
                    // would stay an empty Success forever, never showing anything at all. Loaded
                    // once, same as the real fetch below; replaced by it the moment the user likes
                    // their first song, since dailyDiscoverLoaded is deliberately left false here.
                    if (!trendingFallbackLoaded) {
                        trendingFallbackLoaded = true
                        loadTrendingFallback()
                    }
                } else if (!dailyDiscoverLoaded) {
                    dailyDiscoverLoaded = true
                    loadDailyDiscover(liked)
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
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
        trendingFallbackLoaded = false
        communityPlaylistsLoaded = false
        val liked = likedSongs.value
        if (liked.isNotEmpty()) loadDailyDiscover(liked) else loadTrendingFallback()
        topArtists.value.takeIf { it.isNotEmpty() }?.let { loadCommunityPlaylists(it) }
    }

    private fun loadDailyDiscover(liked: List<Track>) {
        _dailyDiscover.value = UiState.Loading
        viewModelScope.launch(Dispatchers.Default) {
            // Home's hero carousel wants at least this many tracks to feel like a real carousel,
            // not a strict "one per seed" cap - with only 1-2 liked songs, that cap meant the hero
            // only ever had 1-2 tracks in it, barely a carousel at all.
            val targetCount = 6
            val seeds = liked.shuffled().take(5)
            // Concurrent, not one-seed-at-a-time: five sequential radio fetches could take several
            // seconds combined, during which Home's hero carousel (reading this same state) had
            // nothing to show - reads as "doesn't appear until I scroll around a bit", when it was
            // really just still loading. Running them in parallel cuts that wait to roughly one
            // request's latency instead of five stacked end to end.
            val perSeedRadio = seeds.map { seed ->
                async {
                    val seedResult = seed.asTrackResult()
                    if (!seedResult.hasRealVideoId()) return@async emptyList()
                    runCatching { searchRouter.getRadioTracks(seedResult.id) }.getOrDefault(emptyList())
                        .filter { it.id != seedResult.id }
                }
            }.awaitAll()
            // Round-robin across each seed's radio list - one track from each seed first (keeps
            // variety when there are several), then a second pass into the same lists, and so on,
            // until either the target is reached or every seed's radio is exhausted. A single
            // liked song still has a full radio list of its own to draw more than one track from.
            val discovered = mutableListOf<TrackResult>()
            val seenIds = mutableSetOf<String>()
            var round = 0
            while (discovered.size < targetCount && perSeedRadio.any { round < it.size }) {
                for (radio in perSeedRadio) {
                    if (discovered.size >= targetCount) break
                    val candidate = radio.getOrNull(round) ?: continue
                    if (seenIds.add(candidate.id)) discovered += candidate
                }
                round++
            }
            val tracks = discovered.map { it.toPlayableTrack("Daily Discover".hashCode()) }
            _dailyDiscover.value = UiState.Success(tracks.distinctBy { it.downloadKey() })
        }
    }

    /** Cold-start stand-in for [loadDailyDiscover] when there are no liked songs yet to seed a
     * personalized radio from - the same kind of generic query [fallbackShelves] already falls
     * back to before there's any listening history, so a fresh install's hero carousel shows
     * something rather than staying empty forever. */
    private fun loadTrendingFallback() {
        _dailyDiscover.value = UiState.Loading
        viewModelScope.launch(Dispatchers.Default) {
            val fallback = runCatching { searchRouter.searchTracks("New releases") }
                .getOrDefault(emptyList())
                .map { it.toPlayableTrack("Daily Discover".hashCode()) }
            _dailyDiscover.value = UiState.Success(fallback.distinctBy { it.downloadKey() })
        }
    }

    private fun loadCommunityPlaylists(artists: List<String>) {
        _communityPlaylists.value = UiState.Loading
        viewModelScope.launch(Dispatchers.Default) {
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
            viewModelScope.launch(Dispatchers.Default) {
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
