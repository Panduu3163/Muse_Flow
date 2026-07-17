package com.example

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
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
    val errorMessage: String? = null
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
        val mediaItems = queue.mapIndexed { index, queuedTrack ->
            queuedTrack.toQueueMediaItem(index, downloadedFilePathsByKey[queuedTrack.downloadKey()])
        }
        _uiState.update { it.copy(errorMessage = null) }
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
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
        val index = controller.currentMediaItem?.mediaId?.toIntOrNull()
        val track = index?.let { activeQueue.getOrNull(it) }
        _uiState.update {
            it.copy(
                track = track,
                isPlaying = controller.isPlaying,
                progress = progressFraction(controller),
                positionMs = controller.currentPosition.coerceAtLeast(0L)
            )
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
            val index = mediaItem?.mediaId?.toIntOrNull()
            val track = index?.let { activeQueue.getOrNull(it) }
            _uiState.update { it.copy(track = track, progress = 0f, positionMs = 0L) }
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
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
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
 * `.invalid`-URI fallback [PlaybackService] uses for a track it couldn't resolve, ...) into
 * error codes in the 2000-2999 range - which in this app's case is, in practice, always really
 * "no network reached JioSaavn". Anything outside that range is a genuine playback/decoding
 * problem, not a connectivity one. */
private fun PlaybackException.toUserMessage(): String =
    if (errorCode in 2000..2999) {
        "No internet connection. Check your connection and try again."
    } else {
        "Playback failed. Please try again."
    }
