package com.example

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    /** Real ExoPlayer playback position, in milliseconds - precise enough to time-match
     * against LRC-synced lyrics, unlike [progress] which is only a 0f..1f fraction. */
    val positionMs: Long = 0L,
    /** A user-facing message for the most recent playback failure (e.g. no network reaching
     * JioSaavn), or null when nothing's currently wrong. Cleared as soon as something actually
     * starts playing again. */
    val errorMessage: String? = null,
    /** Real codec/bitrate of the audio actually being decoded right now (e.g. "OPUS · 165 kbps"),
     * read straight from ExoPlayer's selected [androidx.media3.common.Format] - not a display
     * gimmick derived from the track's nominal source quality. Null until the first track/format
     * selection event fires. */
    val audioFormatLabel: String? = null,
    val isShuffleEnabled: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_ALL,
    /** Wall-clock time (epoch ms) the sleep timer will pause playback at, or null when no timer
     * is running. */
    val sleepTimerEndAtMs: Long? = null
)

/**
 * Activity-scoped bridge between the Compose UI and [PlaybackService]. Holds a [MediaController]
 * connected to the service's [androidx.media3.session.MediaSession] and republishes its state as
 * a [StateFlow]. Because the controller talks to the service (not a local player), playback -
 * and this ViewModel's view of it - keeps going whether or not the app is in the foreground.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java))
    ).buildAsync()

    // The list a mediaId's index is resolved against - i.e. whatever list was last passed to
    // [playTrack]. Search results build their own queue (the current results list); anything
    // else (Home, Library) defaults to browsing the whole mock catalog, same as before.
    private var activeQueue: List<Track> = MusicData.tracks

    private val downloadRepository = DownloadRepository.getInstance(application)
    // Track downloadKey() -> local file path, kept in sync with Room. Checked ahead of
    // Track.streamUrl when building queue items, so a downloaded track plays from disk - and
    // works fully offline - even if it's also (or only) reachable by streaming.
    private var downloadedFilePathsByKey: Map<String, String> = emptyMap()

    private val playbackHistoryRepository = PlaybackHistoryRepository.getInstance(application)

    // Used only for the "fall back to another provider" step below - a YouTube-sourced track
    // that still fails after one fresh-resolve retry gets substituted with a matching JioSaavn
    // track instead. Not the same instance DownloadRepository/TrackStreamResolver use; this one
    // is scoped to (and lives as long as) this ViewModel, matching its own lifecycle.
    private val fallbackSearchProvider = JioSaavnProvider()

    // downloadKey() of the YouTube-sourced track most recently retried after a failure - so the
    // NEXT failure for that same track falls back to another provider instead of retrying
    // forever. Cleared once something starts playing successfully.
    private var lastYouTubeRetryKey: String? = null

    // Backs the Now Playing queue view - the same list playTrack() built the controller's
    // MediaItems from, so index i here always corresponds to mediaId i on the controller.
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    private var sleepTimerJob: Job? = null

    init {
        viewModelScope.launch {
            downloadRepository.completedDownloads.collect { entities ->
                downloadedFilePathsByKey = entities.associate { it.key to it.filePath }
            }
        }
        controllerFuture.addListener(
            {
                val mediaController = controllerFuture.get()
                controller = mediaController
                mediaController.addListener(PlayerEventListener())
                syncStateFromController(mediaController)
                startProgressTicker()
            },
            MoreExecutors.directExecutor()
        )
    }

    /**
     * Starts playback of [track] within [queue] (defaults to the whole mock catalog, matching
     * the app's original "cycle through MusicData.tracks" next/previous behavior). Tracks that
     * already carry a real [Track.streamUrl] (e.g. JioSaavn search results) are handed to
     * ExoPlayer fully resolved; tracks that don't (the mock catalog) are sent as placeholders
     * that [PlaybackService] resolves lazily via a title/artist search.
     */
    fun playTrack(track: Track, queue: List<Track> = MusicData.tracks) {
        val controller = controller ?: return
        val startIndex = queue.indexOf(track)
        if (startIndex == -1) return
        activeQueue = queue
        _queue.value = queue
        val mediaItems = queue.mapIndexed { index, queuedTrack ->
            queuedTrack.toQueueMediaItem(index, downloadedFilePathsByKey[queuedTrack.downloadKey()])
        }
        _uiState.update { it.copy(errorMessage = null) }
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    /** Jumps straight to [index] within the current queue (see [queue]) - used by the Now Playing
     * queue view, so tapping an upcoming track plays it immediately instead of skipping through
     * one at a time. */
    fun jumpToQueueIndex(index: Int) {
        controller?.seekTo(index, 0L)
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    /** Toggles real ExoPlayer shuffle mode - the controller reorders playback itself
     * ([Player.setShuffleModeEnabled]), this isn't a cosmetic UI-only toggle. */
    fun toggleShuffle() {
        val controller = controller ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    /** Cycles OFF -> ALL -> ONE -> OFF, applied straight to the real [Player.repeatMode] so it
     * actually changes how skip/auto-advance behaves, not just an icon. */
    fun cycleRepeatMode() {
        val controller = controller ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Starts a real countdown that pauses playback after [minutes] - replaces any timer already
     * running. Passing null/0 cancels without scheduling a new one. */
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerEndAtMs = null) }
            return
        }
        val durationMs = minutes * 60_000L
        val endAt = System.currentTimeMillis() + durationMs
        _uiState.update { it.copy(sleepTimerEndAtMs = endAt) }
        sleepTimerJob = viewModelScope.launch {
            delay(durationMs)
            controller?.pause()
            _uiState.update { it.copy(sleepTimerEndAtMs = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerEndAtMs = null) }
    }

    fun seekTo(fraction: Float) {
        val controller = controller ?: return
        val duration = controller.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return
        val clamped = fraction.coerceIn(0f, 1f)
        val targetPositionMs = (duration * clamped).toLong()
        controller.seekTo(targetPositionMs)
        _uiState.update { it.copy(progress = clamped, positionMs = targetPositionMs) }
    }

    // Queue repeat mode is REPEAT_MODE_ALL (set in PlaybackService), so these wrap around at
    // either end of the active queue, same as the old manual index-modulo cycling did.
    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun stopPlayback() {
        controller?.stop()
        controller?.clearMediaItems()
    }

    private fun syncStateFromController(controller: MediaController) {
        val track = resolveTrackFromMediaItem(controller.currentMediaItem)
        _uiState.update {
            it.copy(
                track = track,
                isPlaying = controller.isPlaying,
                progress = progressFraction(controller),
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                isShuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode
            )
        }
    }

    /** Resolves [mediaItem] to a [Track] for display. [activeQueue] only reflects reality when
     * this exact ViewModel instance is the one that called [playTrack] - a fresh instance
     * reconnecting to an already-playing session (e.g. the Activity was recreated from scratch
     * while [PlaybackService] kept running) starts with [activeQueue] defaulted back to
     * [MusicData.tracks], so a raw index lookup can silently return the wrong track instead of
     * null. Guards against that by only trusting the lookup when its title actually matches the
     * MediaItem's own metadata - otherwise falls back to reconstructing a [Track] straight from
     * that metadata (always present - [Track.toQueueMediaItem]/[PlaybackService.resolveMediaItem]
     * both set it), which is always correct even if incomplete (no [Track.sourceId], for
     * instance - fine for display; playback itself keeps working via the controller's own
     * session regardless of what we show here). */
    private fun resolveTrackFromMediaItem(mediaItem: MediaItem?): Track? {
        val index = mediaItem?.mediaId?.toIntOrNull()
        val candidate = index?.let { activeQueue.getOrNull(it) }
        val metadataTitle = mediaItem?.mediaMetadata?.title?.toString()
        return if (candidate != null && (metadataTitle == null || candidate.title.equals(metadataTitle, ignoreCase = true))) {
            candidate
        } else {
            trackFromMediaMetadata(mediaItem, controller?.duration ?: C.TIME_UNSET)
        }
    }

    private fun progressFraction(controller: MediaController): Float {
        val duration = controller.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return 0f
        return (controller.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (true) {
                // 250ms, not 500ms: lyric-line sync (a few hundred ms of slack reads as visibly
                // "off the beat") benefits from this being tighter than the old progress-bar-only
                // cadence did.
                delay(250)
                val controller = controller ?: continue
                if (controller.isPlaying) {
                    _uiState.update {
                        it.copy(
                            progress = progressFraction(controller),
                            positionMs = controller.currentPosition.coerceAtLeast(0L)
                        )
                    }
                }
            }
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying, errorMessage = if (isPlaying) null else it.errorMessage) }
            // Recorded here (actual playback starting), not in playTrack() - so a track that
            // failed to resolve/stream (see onPlayerError) never shows up in "Recently Played".
            if (isPlaying) {
                _uiState.value.track?.let { playbackHistoryRepository.recordPlayed(it) }
                lastYouTubeRetryKey = null
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val track = resolveTrackFromMediaItem(mediaItem)
            _uiState.update { it.copy(track = track, progress = 0f, positionMs = 0L, audioFormatLabel = null) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
        }

        // Fires whenever the actually-decoding audio format becomes known/changes (track
        // selection, quality switch, or a fresh media item) - real playback data, not derived
        // from the track's nominal/advertised quality.
        override fun onTracksChanged(tracks: Tracks) {
            val format = tracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat) }
            _uiState.update { it.copy(audioFormatLabel = format?.toAudioFormatLabel()) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val failedTrack = _uiState.value.track
            if (failedTrack?.sourceType == MusicSource.YOUTUBE_MUSIC) {
                val key = failedTrack.downloadKey()
                if (lastYouTubeRetryKey != key) {
                    // First failure for this specific track: retry once, guaranteed fresh -
                    // playTrack() rebuilds the queue's MediaItems from scratch, and
                    // PlaybackService's resolving data source never caches a YouTube resolution
                    // across calls, so this is never a repeat of the same stale URL. Handles the
                    // confirmed-by-testing case (a resolved URL 403ing within minutes) without
                    // depending on ExoPlayer's own default retry policy actually retrying a 403.
                    lastYouTubeRetryKey = key
                    playTrack(failedTrack, activeQueue)
                    return
                }
                // Already retried once for this exact track and it failed again - fall back to
                // a different provider instead of retrying forever.
                lastYouTubeRetryKey = null
                _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
                attemptFallbackForFailedYouTubeTrack(failedTrack)
                return
            }
            _uiState.update { it.copy(errorMessage = error.toUserMessage()) }
        }
    }

    /** Called only after a YouTube-sourced track has already failed once AND been retried fresh
     * once more (see [PlayerEventListener.onPlayerError]) - searches JioSaavn for a matching
     * track and, if found, plays that instead. Leaves the existing error message in place (set
     * by the caller) if no match is found, rather than failing silently. */
    private fun attemptFallbackForFailedYouTubeTrack(failedTrack: Track) {
        viewModelScope.launch {
            val match = runCatching {
                fallbackSearchProvider.findPlayableMatch(failedTrack.title, failedTrack.artist)
            }.getOrNull()
            if (match != null) {
                val fallbackTrack = match.toPlayableTrack(gradientIndex = failedTrack.gradientIndex)
                playTrack(fallbackTrack, listOf(fallbackTrack))
            }
        }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }
}

/** Reconstructs a display-only [Track] straight from a [MediaItem]'s own [MediaMetadata] - see
 * [PlayerViewModel.resolveTrackFromMediaItem] for why this exists. Returns null only if the
 * MediaItem has no title at all (nothing playable to show). */
private fun trackFromMediaMetadata(mediaItem: MediaItem?, durationMs: Long): Track? {
    val title = mediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val hasDuration = durationMs > 0 && durationMs != C.TIME_UNSET
    return Track(
        title = title,
        artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
        album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "",
        duration = if (hasDuration) "%d:%02d".format((durationMs / 1000) / 60, (durationMs / 1000) % 60) else "-:--",
        plays = "",
        gradientIndex = title.hashCode(),
        imageUrl = mediaItem.mediaMetadata.artworkUri?.toString()
    )
}

/** Turns a real ExoPlayer [Format] into a label like "OPUS · 165 kbps" - codec from
 * [Format.sampleMimeType], bitrate from [Format.bitrate] (bits/sec, converted to kbps). Either
 * half is dropped if genuinely unknown (a local/downloaded file's container not reporting a
 * fixed bitrate, for instance), rather than showing a made-up number. */
private fun Format.toAudioFormatLabel(): String? {
    val codec = sampleMimeType?.substringAfterLast('/')?.let {
        when (it.lowercase()) {
            "mp4a-latm", "mp4a.40.2" -> "AAC"
            "opus" -> "OPUS"
            "mpeg", "mp3" -> "MP3"
            "vorbis" -> "VORBIS"
            "flac" -> "FLAC"
            else -> it.uppercase()
        }
    }
    val kbps = bitrate.takeIf { it != Format.NO_VALUE && it > 0 }?.let { it / 1000 }
    return when {
        codec != null && kbps != null -> "$codec · $kbps kbps"
        codec != null -> codec
        kbps != null -> "$kbps kbps"
        else -> null
    }
}

/**
 * Builds this queue entry's [MediaItem]. Priority is: a downloaded local file ([localFilePath],
 * so playback works fully offline) - then a YouTube-sourced track ([Track.sourceId], never a
 * known [Track.streamUrl] since one is never pre-resolved for these - given a `museflow.invalid`
 * placeholder URI that [PlaybackService]'s resolving data source re-resolves fresh on every
 * single HTTP (re)open (never once at add-time; see that class's doc comment for why) - then a
 * known [Track.streamUrl] (JioSaavn/NetEase search results), resolved fully up front - and only
 * otherwise (the mock catalog, never downloaded) a URI-less placeholder, which Media3 routes to
 * [PlaybackService]'s `onAddMediaItems` for lazy resolution. [Track.imageUrl], when present, is
 * set as artwork either way - both the Now Playing screen's controller-driven state and the
 * system media notification read it from the same [MediaMetadata].
 */
private fun Track.toQueueMediaItem(index: Int, localFilePath: String?): MediaItem {
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setIsPlayable(true)
    imageUrl?.let { metadataBuilder.setArtworkUri(it.toUri()) }

    val itemBuilder = MediaItem.Builder()
        .setMediaId(index.toString())
        .setMediaMetadata(metadataBuilder.build())
    when {
        localFilePath != null -> itemBuilder.setUri(Uri.fromFile(File(localFilePath)))
        sourceType == MusicSource.YOUTUBE_MUSIC && sourceId != null ->
            itemBuilder.setUri(youTubeResolvePlaceholderUri(sourceId))
        streamUrl != null -> itemBuilder.setUri(streamUrl)
    }
    return itemBuilder.build()
}

/** Media3 groups every IO-related error (dead link, unreachable host, timeout, the
 * `.invalid`-URI fallback [PlaybackService] uses for a track it couldn't resolve, a
 * [YouTubeStreamResolver] failure/timeout, ...) into error codes in the 2000-2999 range - but
 * that range covers more than just "no network": YouTube Music's multi-step authenticated
 * resolution pipeline can fail or time out for reasons that have nothing to do with connectivity
 * (an expired PoToken, a blocked video, BotGuard hiccuping) while the network itself is fine.
 * Only blame "no internet" when the underlying cause is actually a connectivity-flavored
 * exception; anything else in that range gets an accurate, connectivity-agnostic message instead
 * of a misleading one. */
private fun PlaybackException.toUserMessage(): String {
    if (errorCode !in 2000..2999) return "Playback failed. Please try again."
    val isGenuineConnectivityFailure = generateSequence(cause as Throwable?) { it.cause }
        .any {
            it is java.net.UnknownHostException ||
                it is java.net.ConnectException ||
                it is java.net.SocketTimeoutException
        }
    return if (isGenuineConnectivityFailure) {
        "No internet connection. Check your connection and try again."
    } else {
        "Couldn't load this track. Please try again."
    }
}
