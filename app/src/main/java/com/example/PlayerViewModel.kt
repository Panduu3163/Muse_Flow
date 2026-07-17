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
    val positionMs: Long = 0L
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
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = mediaItem?.mediaId?.toIntOrNull()
            val track = index?.let { activeQueue.getOrNull(it) }
            _uiState.update { it.copy(track = track, progress = 0f, positionMs = 0L) }
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
 * so playback works fully offline) - then a known [Track.streamUrl] (real search results),
 * resolved fully up front - and only otherwise (the mock catalog, never downloaded) a URI-less
 * placeholder, which Media3 routes to [PlaybackService]'s `onAddMediaItems` for lazy resolution.
 * [Track.imageUrl], when present, is set as artwork either way - both the Now Playing screen's
 * controller-driven state and the system media notification read it from the same
 * [MediaMetadata].
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
        streamUrl != null -> itemBuilder.setUri(streamUrl)
    }
    return itemBuilder.build()
}
