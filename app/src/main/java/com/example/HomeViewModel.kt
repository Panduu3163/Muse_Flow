package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home's state: entirely local data first (recently played, most played, playlists), plus a set of
 * genre/mood shelves fetched through whichever extractor is selected.
 *
 * Shelves are cached to Room ([HomeShelfCacheDao]) as they arrive and served from that cache on the
 * next launch, so Home renders instantly and still shows something useful with no connectivity -
 * a fetch failure falls back to the cached copy rather than an empty screen.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = PlaybackHistoryRepository.getInstance(application)
    private val playlistRepository = PlaylistRepository.getInstance(application)
    private val shelfCacheDao = MuseFlowDatabase.getInstance(application).homeShelfCacheDao()
    private val searchRouter = MusicSearchRouter(application)

    /** The canned genre/mood shelves Home shows below its local sections. */
    val shelfTitles = listOf("Trending Now", "Chill Vibes", "Workout Energy", "Throwback Hits")

    val recentlyPlayed: StateFlow<List<Track>> = historyRepository.observeRecent(20)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPlayed: StateFlow<List<Track>> = historyRepository.observeTopPlayed(20)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The single most recent track, for the "Continue Listening" resume card. */
    val continueListening: StateFlow<Track?> = recentlyPlayed
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    /** The artist the user plays most, used to label and seed the "more like this" shelf. */
    val favouriteArtist: StateFlow<String?> = topPlayed
        .map { tracks ->
            tracks.mapNotNull { it.artist.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _similarToFavourite = MutableStateFlow<List<Track>>(emptyList())
    val similarToFavourite: StateFlow<List<Track>> = _similarToFavourite.asStateFlow()

    init {
        // Re-fetch whenever the user's most-played artist changes, so this shelf tracks taste
        // drift instead of being pinned to whoever they listened to first.
        viewModelScope.launch {
            favouriteArtist.collect { artist ->
                if (artist.isNullOrBlank()) {
                    _similarToFavourite.value = emptyList()
                    return@collect
                }
                _similarToFavourite.value = runCatching { searchRouter.searchTracks(artist) }
                    .getOrNull()
                    .orEmpty()
                    .map { it.toPlayableTrack(artist.hashCode()) }
                    .take(12)
            }
        }
    }

    private val _shelves = MutableStateFlow<Map<String, UiState<List<Track>>>>(
        shelfTitles.associateWith { UiState.Loading }
    )
    val shelves: StateFlow<Map<String, UiState<List<Track>>>> = _shelves.asStateFlow()

    init {
        loadShelves()
    }

    fun refresh() = loadShelves()

    private fun loadShelves() {
        shelfTitles.forEach { title ->
            viewModelScope.launch {
                // Cached copy first, so the shelf has content while the network call is in flight.
                val cached = runCatching { shelfCacheDao.get(title) }.getOrNull()
                    ?.let { parseTracksJson(it.tracksJson) }
                    .orEmpty()
                if (cached.isNotEmpty()) {
                    _shelves.update(title, UiState.Success(cached))
                }

                val fresh = runCatching { searchRouter.searchTracks(title) }.getOrNull()
                    ?.map { it.toPlayableTrack(title.hashCode()) }

                when {
                    !fresh.isNullOrEmpty() -> {
                        _shelves.update(title, UiState.Success(fresh))
                        runCatching {
                            shelfCacheDao.upsert(
                                HomeShelfCacheEntity(
                                    shelfTitle = title,
                                    tracksJson = fresh.toJson(),
                                    cachedAt = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    // Network gave nothing but the cache already did - keep showing the cache.
                    cached.isNotEmpty() -> Unit
                    else -> _shelves.update(title, UiState.Error("Couldn't load \"$title\" right now."))
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
