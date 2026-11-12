package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.screens.asTrackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** A fetched shelf: [title] is what's shown, [query] is what's actually searched for - split
 * apart because a personalized shelf's title ("More Radiohead") isn't itself a good search query
 * for anything ("More Radiohead" the search vs "Radiohead" the artist). */
data class HomeShelfSpec(val title: String, val query: String)

internal enum class HeroSeedSource { LIKED, RECENT, GENERIC }

internal fun heroSeedSource(likedCount: Int, recentCount: Int): HeroSeedSource = when {
    likedCount > 0 -> HeroSeedSource.LIKED
    recentCount > 0 -> HeroSeedSource.RECENT
    else -> HeroSeedSource.GENERIC
}

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

    /** Same fixed On repeat shelf; recent genuine listening takes precedence over lifetime starts. */
    val onRepeat: StateFlow<List<Track>> = combine(
        historyRepository.observeAll(), historyRepository.observeListeningSessions(), topPlayed,
    ) { history, sessions, fallback ->
        recentFavourites(history, sessions, System.currentTimeMillis()).ifEmpty { fallback }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Offline hero items come only from completed downloads whose audio file still exists.
     * Null means Room has not emitted yet, avoiding an online-artwork flash on cold offline load. */
    val offlineHeroTracks: StateFlow<List<Track>?> =
        DownloadRepository.getInstance(application).completedDownloads
            .map<List<DownloadedTrackEntity>, List<Track>?> { downloads ->
                downloads.asSequence()
                    .filter { File(it.filePath).isFile && File(it.filePath).length() > 0L }
                    .sortedByDescending { it.updatedAt }
                    .map { entry ->
                        entry.toTrack(application).copy(
                            // A remote thumbnail is not an offline artwork source. Older
                            // downloads without a local cover use the hero's music placeholder.
                            imageUrl = DownloadRepository.localCoverFile(application, entry.key, entry.filePath)
                                ?.let { android.net.Uri.fromFile(it).toString() },
                        )
                    }
                    .distinctBy { it.downloadKey() }
                    .take(6)
                    .toList()
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Null until both Room sources have emitted their actual first snapshot. Home must not build
     * its hero from two independent empty-first UI StateFlows and briefly show the wrong cover. */
    private val _heroLocalTracks = MutableStateFlow<List<Track>?>(null)
    val heroLocalTracks: StateFlow<List<Track>?> = _heroLocalTracks.asStateFlow()

    /** One related track per liked-song seed, from whichever extractor's radio/related endpoint
     * is available - Echo's "Daily Discover" without a login of its own to seed from. */
    private val _dailyDiscover = MutableStateFlow<UiState<List<Track>>>(UiState.Loading)
    val dailyDiscover: StateFlow<UiState<List<Track>>> = _dailyDiscover.asStateFlow()

    /** Third-party playlists surfaced from the user's own top artists, rather than a curated
     * editorial pick - "From the community" without an account to source community activity from. */
    private val _communityPlaylists = MutableStateFlow<UiState<List<PlaylistResult>>>(UiState.Loading)
    val communityPlaylists: StateFlow<UiState<List<PlaylistResult>>> = _communityPlaylists.asStateFlow()

    private var heroSource: HeroSeedSource? = null
    private var heroSeeds: List<Track> = emptyList()
    private var heroRequestJob: Job? = null
    @Volatile private var heroRequestId = 0
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
        // Combine the repositories themselves, not their empty-first StateFlow snapshots. Room
        // emits each real initial value before combine runs, so a returning user cannot briefly
        // look like a new user and start the generic "New releases" hero request.
        viewModelScope.launch {
            combine(
                likedSongsRepository.observeAll().map { rows -> rows.map { it.toTrack() } },
                historyRepository.observeRecent(20).map { rows -> rows.map { it.toTrack() } },
            ) { liked, recent -> liked to recent }.collect { (liked, recent) ->
                val source = heroSeedSource(liked.size, recent.size)
                heroSeeds = if (liked.isNotEmpty()) liked else recent
                _heroLocalTracks.value = heroSeeds.take(6)
                if (source != heroSource) {
                    heroSource = source
                    requestHero(source, heroSeeds, preserveCurrent = false)
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
        communityPlaylistsLoaded = false
        heroSource?.let { requestHero(it, heroSeeds, preserveCurrent = true) }
        topArtists.value.takeIf { it.isNotEmpty() }?.let { loadCommunityPlaylists(it) }
    }

    private fun requestHero(source: HeroSeedSource, seeds: List<Track>, preserveCurrent: Boolean) {
        heroRequestJob?.cancel()
        val requestId = ++heroRequestId
        if (!preserveCurrent || _dailyDiscover.value !is UiState.Success) {
            _dailyDiscover.value = UiState.Loading
        }
        heroRequestJob = viewModelScope.launch(Dispatchers.Default) {
            val tracks = if (source == HeroSeedSource.GENERIC) loadTrendingFallback() else loadDailyDiscover(seeds)
            // A slow generic fetch (or an older refresh) must never replace personalized results.
            if (requestId == heroRequestId && isActive) {
                if (tracks.isNotEmpty() || _dailyDiscover.value !is UiState.Success) {
                    _dailyDiscover.value = UiState.Success(tracks)
                }
            }
        }
    }

    private suspend fun loadDailyDiscover(seeds: List<Track>): List<Track> {
        // With only 1-2 seeds, take several related songs from each so the hero is a carousel.
        val targetCount = 18
        val selectedSeeds = seeds.shuffled().take(5)
        val perSeedRadio = kotlinx.coroutines.coroutineScope {
            selectedSeeds.map { seed ->
                async {
                    val seedResult = seed.asTrackResult()
                    if (!seedResult.hasRealVideoId()) return@async emptyList()
                    val radio = try { searchRouter.getRadioTracks(seedResult.id) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { emptyList() }
                    radio.filter { it.id != seedResult.id }
                }
            }.awaitAll()
        }
        // Round-robin across seeds for variety without starving a single-seed library.
        val discovered = mutableListOf<TrackResult>()
        val seenIds = mutableSetOf<String>()
        var round = 0
        while (discovered.size < 96 && perSeedRadio.any { round < it.size }) {
            for (radio in perSeedRadio) {
                if (discovered.size >= 96) break
                val candidate = radio.getOrNull(round) ?: continue
                if (seenIds.add(candidate.id)) discovered += candidate
            }
            round++
        }
        val listening = historyRepository.observeListeningSessions().first()
        val now = System.currentTimeMillis()
        return rankDiscoveryCandidates(
            discovered, targetCount, excludedIds = selectedSeeds.map { it.playbackId() }.toSet(),
            familiarArtists = seeds.map { it.artist }.toSet(),
            recentIds = historyRepository.observeRecent(30).first().mapNotNull { it.sourceId }.toSet(),
            learnedPreferences = learnedRecordingPreferences(listening, now),
            learnedArtists = learnedArtistPreferences(listening, now),
        ).map { it.toPlayableTrack("Daily Discover".hashCode()) }
    }

    /** Generic hero only after both Room sources have confirmed this is a new listener. */
    private suspend fun loadTrendingFallback(): List<Track> {
        val results = try { searchRouter.searchDiscoveryTracks("New releases") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
        val fallback = results.map { it.toPlayableTrack("Daily Discover".hashCode()) }
        return fallback.distinctBy { it.playbackId() }
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

                val fresh = runCatching { searchRouter.searchDiscoveryTracks(spec.query) }.getOrNull()
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
