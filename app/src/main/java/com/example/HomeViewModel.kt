package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A Home shelf backed by a canned search query, standing in for a real recommendation system. */
private data class MoodShelfQuery(val title: String, val query: String)

private val moodShelfQueries = listOf(
    MoodShelfQuery("Chill Vibes", "lofi chill beats"),
    MoodShelfQuery("Workout Energy", "workout gym hype"),
    MoodShelfQuery("Party Hits", "party dance hits"),
    MoodShelfQuery("Focus Flow", "instrumental focus study")
)

/**
 * Real data for Home, in place of the static [MusicData] shelves: "Recently Played" comes from
 * actual playback history in Room ([PlaybackHistoryRepository]), and every other shelf is a
 * canned genre/mood search against JioSaavn and YouTube Music - a stand-in for a real
 * recommendation system, which doesn't exist yet. Each shelf loads independently (its entry in
 * [moodShelves] is missing until that one search resolves) so a slow or failed shelf doesn't
 * block the others from appearing.
 *
 * Every successful shelf fetch is also cached to Room ([HomeShelfCacheDao]), so when there's no
 * connectivity at all, Home falls back to showing that last-fetched snapshot (cover art keeps
 * working too - Coil already has those same image URLs in its own disk cache from the original
 * load) instead of an empty "couldn't load" state for content that was already fetched once.
 * [isOffline] tells the UI to label that fallback content as such - and, via a
 * [ConnectivityManager.NetworkCallback] registered in [init], flips back to false and triggers a
 * real re-fetch the moment connectivity actually returns, instead of leaving that stale cached
 * snapshot on screen indefinitely until the user manually reopens Home.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)
    private val shelfCacheDao = MuseFlowDatabase.getInstance(application).homeShelfCacheDao()
    private val jioSaavnProvider = JioSaavnProvider()
    private val youTubeProvider = YouTubeMusicProvider(application)
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Null while the first read from Room is still in flight; empty once loaded with no history. */
    val recentlyPlayed: StateFlow<List<Track>?> = playbackHistoryRepository.observeRecent(10)
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shelfTitles: List<String> = moodShelfQueries.map { it.title }

    /** Whether the mood/genre shelves below are currently showing a cached fallback rather than
     * freshly-fetched content - reactive (not just a load-time snapshot), since a
     * [ConnectivityManager.NetworkCallback] can flip it back to false mid-session once
     * connectivity actually returns. */
    private val _isOffline = MutableStateFlow(!isOnline(application))
    val isOffline: StateFlow<Boolean> = _isOffline

    /** Shelf title -> [UiState]. A missing entry (or [UiState.Loading]) means that shelf's search
     * hasn't resolved yet; [UiState.Error] means a genuine failure or timeout with no cached
     * fallback available, distinct from [UiState.Success] with an empty list (resolved, just
     * nothing playable found). A [UiState.Success] while [isOffline] is true is cached content. */
    private val _moodShelves = MutableStateFlow<Map<String, UiState<List<Track>>>>(emptyMap())
    val moodShelves: StateFlow<Map<String, UiState<List<Track>>>> = _moodShelves

    init {
        loadShelves()
        registerNetworkCallback()
    }

    /** Watches for connectivity actually coming back (not just being newly requested/checked),
     * so a shelf that fell back to cached data while offline gets replaced with a real, fresh
     * fetch as soon as it's possible again - rather than the user having to leave and reopen
     * Home, or the stale snapshot just sitting there indefinitely. */
    private fun registerNetworkCallback() {
        val manager = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Only actually worth re-fetching if we were showing stale/cached content -
                // onAvailable also fires once just from registering while already online, and
                // again for every additional network satisfying the request (e.g. Wi-Fi joining
                // alongside cellular), neither of which should trigger a redundant re-fetch.
                if (_isOffline.value) {
                    _isOffline.value = false
                    loadShelves()
                }
            }

            override fun onLost(network: Network) {
                if (!isOnline(getApplication())) {
                    _isOffline.value = true
                }
            }
        }
        networkCallback = callback
        manager.registerNetworkCallback(request, callback)
    }

    private fun loadShelves() {
        moodShelfQueries.forEach { shelf ->
            viewModelScope.launch {
                if (_isOffline.value) {
                    // No point even trying the network - go straight to whatever was cached last.
                    val cached = shelfCacheDao.get(shelf.title)
                    _moodShelves.update {
                        it + (shelf.title to if (cached != null) {
                            UiState.Success(parseTracksJson(cached.tracksJson))
                        } else {
                            UiState.Error("You're offline, and \"${shelf.title}\" hasn't loaded before.")
                        })
                    }
                    return@launch
                }

                // Same JioSaavn-first-with-YouTube-fallback pattern Search uses: both sources are
                // queried in parallel (so a slow one can't hold up the whole shelf), merged
                // (JioSaavn results first, then any YouTube ones that aren't already covered), and
                // only treated as a genuine failure if both come back empty/erroring - this is
                // what keeps a shelf resilient to a JioSaavn-side outage or regional throttling,
                // rather than depending on JioSaavn alone the way this shelf used to.
                var jioFailed = false
                var ytFailed = false
                val (jioResults, ytResults) = coroutineScope {
                    val jioDeferred = async {
                        withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                            try {
                                jioSaavnProvider.search(shelf.query).filter { it.directStreamUrl != null }
                            } catch (e: Exception) {
                                null
                            }
                        } ?: run { jioFailed = true; emptyList() }
                    }
                    val ytDeferred = async {
                        withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                            try {
                                youTubeProvider.search(shelf.query)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: run { ytFailed = true; emptyList() }
                    }
                    jioDeferred.await() to ytDeferred.await()
                }

                if (jioFailed && ytFailed) {
                    // A genuine fetch failure despite (as far as we knew) having connectivity -
                    // still worth falling back to a cached snapshot rather than an empty error,
                    // same reasoning as the offline branch above.
                    val cached = shelfCacheDao.get(shelf.title)
                    _moodShelves.update {
                        it + (shelf.title to if (cached != null) {
                            UiState.Success(parseTracksJson(cached.tracksJson))
                        } else {
                            UiState.Error("Couldn't load \"${shelf.title}\" right now.")
                        })
                    }
                    return@launch
                }

                val merged = mergeSearchResults(jioResults, ytResults)
                    .take(10)
                    .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
                _moodShelves.update { it + (shelf.title to UiState.Success(merged)) }
                if (merged.isNotEmpty()) {
                    shelfCacheDao.upsert(HomeShelfCacheEntity(shelf.title, merged.toJson(), System.currentTimeMillis()))
                }
            }
        }
    }

    override fun onCleared() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        super.onCleared()
    }
}
