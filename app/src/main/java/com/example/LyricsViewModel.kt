package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Fetches lyrics for whatever is playing.
 *
 * Six network providers - YouLyPlus, PaxSenix, BetterLyrics, SimpMusic, LRCLib, Kugou - are queried
 * concurrently and equal-quality results respect the user's order (see
 * [AppSettingsState.lyricsProviderOrder], reorderable from
 * Settings > Lyrics > "Lyrics provider order"), with YouTube Music's own lyrics tab always tried
 * last regardless of that order - it has no timing at all, only plain text, so it's strictly worse
 * than any synced source and only worth reaching for once everything else has failed. A provider
 * that returns [LyricsResult.NotFound] is not treated as a failure - the next one is simply tried,
 * and only a genuine miss from all of them surfaces as "no lyrics".
 *
 * Successful results are cached per track in memory and on disk, so offline reopen and
 * scrolling in and out of the lyrics view do not refetch.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private data class Request(val title: String, val artist: String, val durationSeconds: Int?, val videoId: String?)
    private data class CacheKey(
        val title: String,
        val artist: String,
        val durationSeconds: Int?,
        val videoId: String?,
        val provider: LyricsProviderId?,
    )

    private val lrcLib = LrcLibProvider()
    private val betterLyrics = BetterLyricsProvider()
    private val youLyPlus = YouLyPlusProvider()
    private val paxSenix = PaxSenixProvider()
    private val simpMusic = SimpMusicProvider()
    private val kugou = KugouLyricsProvider()
    private val router = MusicSearchRouter(application)
    private val settingsRepository = AppSettingsRepository(application)
    private val diskCache = LyricsCacheStore(application)

    private val _state = MutableStateFlow<LyricsResult?>(null)
    val state: StateFlow<LyricsResult?> = _state.asStateFlow()

    private val _selectedProvider = MutableStateFlow<LyricsProviderId?>(null)
    val selectedProvider: StateFlow<LyricsProviderId?> = _selectedProvider.asStateFlow()
    private val _resultSource = MutableStateFlow<String?>(null)
    val resultSource: StateFlow<String?> = _resultSource.asStateFlow()

    private val cache = LinkedHashMap<CacheKey, LyricsLookup>()
    private var loadJob: Job? = null
    private var loadedKey: CacheKey? = null
    private var lastRequest: Request? = null

    private fun cacheKey(request: Request) = CacheKey(
        request.title.trim().lowercase(), request.artist.trim().lowercase(),
        request.durationSeconds, request.videoId, _selectedProvider.value,
    )

    private fun diskKey(key: CacheKey): String = JSONArray()
        .put(key.title).put(key.artist).put(key.durationSeconds).put(key.videoId)
        .put(key.provider?.name).toString()

    /** Loads lyrics for [title]/[artist], reusing the cached result when the track hasn't changed.
     * [videoId], when supplied, must already be a confirmed real YouTube video id (see
     * [hasRealVideoId]) - the caller's job, since a fabricated "title|artist" stand-in id would
     * otherwise reach [MusicSearchRouter.getLyricsText] and resolve to nothing. */
    fun load(title: String, artist: String, durationSeconds: Int?, videoId: String? = null) {
        if (title.isBlank()) {
            lastRequest = null
            clear()
            return
        }
        val request = Request(title, artist, durationSeconds, videoId)
        lastRequest = request
        val key = cacheKey(request)
        if (key == loadedKey) return

        loadedKey = key
        loadJob?.cancel()
        cache[key]?.let {
            _state.value = it.result
            _resultSource.value = it.source
            return
        }

        _state.value = null // null = loading, distinct from NotFound
        _resultSource.value = null
        loadJob = viewModelScope.launch {
            diskCache.get(diskKey(key))?.let { saved ->
                cache[key] = saved
                if (loadedKey == key) {
                    _state.value = saved.result
                    _resultSource.value = saved.source
                }
                return@launch
            }
            val lookup = fetchFirstUsable(title, artist, durationSeconds, videoId, key.provider)
            if (lookup.result !is LyricsResult.Error && lookup.result !is LyricsResult.NotFound) {
                cache[key] = lookup
                if (cache.size > 12) cache.remove(cache.keys.first())
            }
            // Guard against a stale response landing after the user skipped to another track.
            if (loadedKey == key) {
                _state.value = lookup.result
                _resultSource.value = lookup.source
            }
            if (lookup.result !is LyricsResult.Error && lookup.result !is LyricsResult.NotFound) {
                diskCache.put(diskKey(key), lookup)
            }
        }
    }

    /** A session-level override. Auto returns to the user's configured priority order. */
    fun selectProvider(provider: LyricsProviderId?) {
        if (_selectedProvider.value == provider) return
        _selectedProvider.value = provider
        loadedKey = null
        lastRequest?.let { load(it.title, it.artist, it.durationSeconds, it.videoId) }
    }

    fun retry() {
        val request = lastRequest ?: return
        val key = cacheKey(request)
        loadJob?.cancel()
        cache.remove(key)
        loadedKey = null
        _state.value = null
        _resultSource.value = null
        viewModelScope.launch {
            diskCache.remove(diskKey(key))
            if (lastRequest == request && cacheKey(request) == key && loadedKey == null) {
                load(request.title, request.artist, request.durationSeconds, request.videoId)
            }
        }
    }

    private suspend fun fetchFirstUsable(
        title: String,
        artist: String,
        durationSeconds: Int?,
        videoId: String?,
        selectedProvider: LyricsProviderId?,
    ): LyricsLookup {
        // The disk cache is checked before this point. Without a saved result, searching online
        // providers while offline would only wait for connection timeouts.
        if (!isOnline(getApplication())) {
            return LyricsLookup(LyricsResult.Error("Lyrics need an internet connection. Reconnect and retry."), null)
        }

        if (selectedProvider == LyricsProviderId.SimpMusic && videoId == null) {
            return LyricsLookup(LyricsResult.Error("SimpMusic needs a YouTube track. Choose Auto or another source."), null)
        }
        val order = selectedProvider?.let(::listOf) ?: settingsRepository.state.first().lyricsProviderOrder
        val providers = buildList<LyricsSourceRequest> {
            for (id in order) {
                when (id) {
                    LyricsProviderId.YouLyPlus -> add(LyricsSourceRequest(id.label) { youLyPlus.fetchLyrics(title, artist, durationSeconds) })
                    LyricsProviderId.PaxSenix -> add(LyricsSourceRequest(id.label) { paxSenix.fetchLyrics(title, artist, durationSeconds) })
                    LyricsProviderId.BetterLyrics -> add(LyricsSourceRequest(id.label) { betterLyrics.fetchLyrics(title, artist, durationSeconds, videoId) })
                    // SimpMusic is keyed by YouTube video id, not title/artist search - skipped
                    // entirely (not just "tried and NotFound") when no confirmed real id is
                    // available, same as the YouTube tab fallback below.
                    LyricsProviderId.SimpMusic -> if (videoId != null) {
                        add(LyricsSourceRequest(id.label) { simpMusic.fetchLyrics(videoId, durationSeconds) })
                    }
                    LyricsProviderId.LrcLib -> add(LyricsSourceRequest(id.label) { lrcLib.fetchLyrics(title, artist, durationSeconds) })
                    LyricsProviderId.Kugou -> add(LyricsSourceRequest(id.label) { kugou.fetchLyrics(title, artist, durationSeconds) })
                }
            }
            // Always last, regardless of the user's configured order - see class doc.
            if (selectedProvider == null && videoId != null) {
                add(LyricsSourceRequest("YouTube Music") {
                    router.getLyricsText(videoId)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { LyricsResult.PlainOnly(it) }
                        ?: LyricsResult.NotFound
                })
            }
        }
        return fetchBestLyrics(providers)
    }

    fun clear() {
        loadJob?.cancel()
        loadedKey = null
        _state.value = null
        _resultSource.value = null
    }
}

/** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
fun List<LyricLine>.activeLineIndex(positionMs: Long): Int {
    if (isEmpty()) return -1
    // indexOfLast is O(n) but lyric lists are small (tens of lines) and this runs at most a few
    // times a second, so a binary search would be premature.
    return indexOfLast { it.timeMs <= positionMs }
}
