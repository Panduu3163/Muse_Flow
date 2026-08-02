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

/**
 * Fetches lyrics for whatever is playing.
 *
 * Six network providers - YouLyPlus, PaxSenix, BetterLyrics, SimpMusic, LRCLib, Kugou - are tried
 * in the user's configured order (see [AppSettingsState.lyricsProviderOrder], reorderable from
 * Settings > Lyrics > "Lyrics provider order"), with YouTube Music's own lyrics tab always tried
 * last regardless of that order - it has no timing at all, only plain text, so it's strictly worse
 * than any synced source and only worth reaching for once everything else has failed. A provider
 * that returns [LyricsResult.NotFound] is not treated as a failure - the next one is simply tried,
 * and only a genuine miss from all of them surfaces as "no lyrics".
 *
 * Results are cached per track for the session, so scrolling in and out of the lyrics view or
 * pausing doesn't refetch.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val lrcLib = LrcLibProvider()
    private val betterLyrics = BetterLyricsProvider()
    private val youLyPlus = YouLyPlusProvider()
    private val paxSenix = PaxSenixProvider()
    private val simpMusic = SimpMusicProvider()
    private val kugou = KugouLyricsProvider()
    private val router = MusicSearchRouter(application)
    private val settingsRepository = AppSettingsRepository(application)

    private val _state = MutableStateFlow<LyricsResult?>(null)
    val state: StateFlow<LyricsResult?> = _state.asStateFlow()

    private val cache = mutableMapOf<String, LyricsResult>()
    private var loadJob: Job? = null
    private var loadedKey: String? = null

    /** Loads lyrics for [title]/[artist], reusing the cached result when the track hasn't changed.
     * [videoId], when supplied, must already be a confirmed real YouTube video id (see
     * [hasRealVideoId]) - the caller's job, since a fabricated "title|artist" stand-in id would
     * otherwise reach [MusicSearchRouter.getLyricsText] and resolve to nothing. */
    fun load(title: String, artist: String, durationSeconds: Int?, videoId: String? = null) {
        if (title.isBlank()) return
        val key = "${title.trim().lowercase()}::${artist.trim().lowercase()}"
        if (key == loadedKey) return

        loadedKey = key
        cache[key]?.let {
            _state.value = it
            return
        }

        loadJob?.cancel()
        _state.value = null // null = loading, distinct from NotFound
        loadJob = viewModelScope.launch {
            val result = fetchFirstUsable(title, artist, durationSeconds, videoId)
            cache[key] = result
            // Guard against a stale response landing after the user skipped to another track.
            if (loadedKey == key) _state.value = result
        }
    }

    private suspend fun fetchFirstUsable(
        title: String,
        artist: String,
        durationSeconds: Int?,
        videoId: String?,
    ): LyricsResult {
        // All three providers are network-only - there's no local/cached lyrics source to fall
        // back to - so offline, every one of them was guaranteed to fail anyway, just after each
        // paying its own ~10s connect timeout first (sequentially, not in parallel: up to 20-30s of
        // spinner before finally landing on the exact same NotFound this reaches instantly). Failing
        // fast here isn't just faster, it's also what actually surfaces a message to the user in
        // practice - anyone who isn't willing to stare at a spinner for half a minute would give up
        // and conclude lyrics "don't show a message" offline, even though one was technically coming.
        if (!isOnline(getApplication())) {
            return LyricsResult.Error("No lyrics - you're offline.")
        }

        val order = settingsRepository.state.first().lyricsProviderOrder
        val providers = buildList<suspend () -> LyricsResult> {
            for (id in order) {
                when (id) {
                    LyricsProviderId.YouLyPlus -> add { youLyPlus.fetchLyrics(title, artist, durationSeconds) }
                    LyricsProviderId.PaxSenix -> add { paxSenix.fetchLyrics(title, artist, durationSeconds) }
                    LyricsProviderId.BetterLyrics -> add { betterLyrics.fetchLyrics(title, artist, durationSeconds) }
                    // SimpMusic is keyed by YouTube video id, not title/artist search - skipped
                    // entirely (not just "tried and NotFound") when no confirmed real id is
                    // available, same as the YouTube tab fallback below.
                    LyricsProviderId.SimpMusic -> if (videoId != null) {
                        add { simpMusic.fetchLyrics(videoId, durationSeconds) }
                    }
                    LyricsProviderId.LrcLib -> add { lrcLib.fetchLyrics(title, artist, durationSeconds) }
                    LyricsProviderId.Kugou -> add { kugou.fetchLyrics(title, artist, durationSeconds) }
                }
            }
            // Always last, regardless of the user's configured order - see class doc.
            if (videoId != null) {
                add {
                    router.getLyricsText(videoId)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { LyricsResult.PlainOnly(it) }
                        ?: LyricsResult.NotFound
                }
            }
        }

        // Not simply "first Synced wins": some providers (LRCLib) only ever have line-level
        // timing, never word-level - so returning on the first synced result immediately would
        // mean a later provider's word-level timing (the only thing that makes karaoke word sync
        // possible) never gets a chance to run for any track an earlier, line-only provider also
        // covers. A synced result WITH word timing is the only thing that short-circuits the loop;
        // a synced result without it is kept as a candidate while later providers are still tried,
        // in case one of them has the word-level version.
        var bestSynced: LyricsResult.Synced? = null
        var plainFallback: LyricsResult? = null
        for (provider in providers) {
            when (val result = runCatching { provider() }.getOrElse { LyricsResult.NotFound }) {
                is LyricsResult.Synced -> {
                    if (result.lines.any { it.words != null }) return result // Best case possible.
                    if (bestSynced == null) bestSynced = result
                }
                is LyricsResult.PlainOnly -> if (plainFallback == null) plainFallback = result
                is LyricsResult.Instrumental -> return result
                else -> Unit
            }
        }
        return bestSynced ?: plainFallback ?: LyricsResult.NotFound
    }

    fun clear() {
        loadJob?.cancel()
        loadedKey = null
        _state.value = null
    }
}

/** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
fun List<LyricLine>.activeLineIndex(positionMs: Long): Int {
    if (isEmpty()) return -1
    // indexOfLast is O(n) but lyric lists are small (tens of lines) and this runs at most a few
    // times a second, so a binary search would be premature.
    return indexOfLast { it.timeMs <= positionMs }
}
