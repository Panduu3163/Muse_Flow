package com.example

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One entry in the playback queue, as the Now Playing queue list renders it. */
data class QueueItem(
    val index: Int,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isCurrent: Boolean,
)

/** What the UI needs to know about whatever is currently playing. */
data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasMedia: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    /** Mirrors `Player.REPEAT_MODE_*`: 0 off, 1 one, 2 all. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** `mm:ss`, or `-:--` before a duration is known. */
fun Long.asPlaybackTime(): String {
    if (this <= 0L) return "-:--"
    val totalSeconds = this / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * Owns the [MediaController] connection to [PlaybackService] and exposes playback as state the
 * Compose UI can observe.
 *
 * A controller is used rather than an ExoPlayer instance so playback keeps running in the
 * foreground service - surviving this ViewModel, the Activity, and the app being backgrounded -
 * and so the media notification stays the single source of truth for what's playing.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var controller: MediaController? = null
    private val historyRepository = PlaybackHistoryRepository.getInstance(application)

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer(player)
    }

    init {
        connect()
        // Media3 pushes state changes through the listener, but not a continuously ticking
        // position - so the progress bar is refreshed on a light timer while something plays.
        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.takeIf { it.isPlaying }?.let(::syncFromPlayer)
            }
        }
    }

    private fun connect() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener(
            {
                controller = future.get().also { newController ->
                    newController.addListener(listener)
                    syncFromPlayer(newController)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun syncFromPlayer(player: Player) {
        val metadata = player.mediaMetadata
        val currentIndex = player.currentMediaItemIndex
        val queue = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueItem(
                index = index,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                artworkUrl = item.mediaMetadata.artworkUri?.toString(),
                isCurrent = index == currentIndex,
            )
        }

        _state.value = NowPlayingState(
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUrl = metadata.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            hasMedia = player.currentMediaItem != null,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            queue = queue,
        )
    }

    /**
     * Plays [track] and queues the rest of [queue] behind it.
     *
     * YouTube results carry only a videoId, so each item is handed to the service as a
     * [youTubeResolvePlaceholderUri]; [PlaybackService] swaps in a real URL at HTTP-open time -
     * which is what routes playback through whichever extractor is selected.
     */
    fun play(track: TrackResult, queue: List<TrackResult> = listOf(track)) {
        val controller = controller ?: return
        val items = queue.map { it.toMediaItem() }
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        controller.setMediaItems(items, startIndex, 0L)
        controller.prepare()
        controller.play()

        // Without this, Home's "Recently played"/"On repeat" shelves and Library's Top 50 would
        // never populate - nothing else in the app writes playback history.
        historyRepository.recordPlayed(track.toPlayableTrack(track.id.hashCode()))
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(fraction: Float) {
        val controller = controller ?: return
        val duration = controller.duration
        if (duration > 0L) controller.seekTo((duration * fraction).toLong())
    }

    /** Absolute seek, used by lyrics tap-to-seek where the target is a timestamp, not a fraction. */
    fun seekToMs(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val controller = controller ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    /** Cycles off -> repeat all -> repeat one, the order every music player uses. */
    fun cycleRepeatMode() {
        val controller = controller ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * The currently playing item rebuilt as a [Track], so like/download actions can key off the
     * same title/artist identity the repositories use. Null when nothing is loaded.
     */
    fun currentTrackForActions(): Track? {
        val item = controller?.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        if (title.isBlank()) return null

        return Track(
            title = title,
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = (controller?.duration ?: 0L).asPlaybackTime(),
            plays = "",
            gradientIndex = title.hashCode(),
            imageUrl = metadata.artworkUri?.toString(),
            sourceType = MusicSource.YOUTUBE_MUSIC,
            sourceId = item.mediaId,
        )
    }

    fun playQueueItem(index: Int) {
        val controller = controller ?: return
        controller.seekTo(index, 0L)
        controller.play()
    }

    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}

private fun TrackResult.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(source)
        .apply { imageUrl?.let { setArtworkUri(it.toUri()) } }
        .build()

    // A directStreamUrl is playable as-is; everything else goes through the resolve placeholder
    // so the service resolves it lazily, right before playback.
    //
    // Downloaded tracks store a bare filesystem path, not a URI. `String.toUri()` on one of those
    // yields a scheme-less Uri that DefaultDataSource can't dispatch (ExoPlayer reports a bare
    // "Source error"), and any space or ':' in the filename would need escaping too - both of
    // which Uri.fromFile handles.
    val uri = directStreamUrl?.let { url ->
        if (url.startsWith("/")) Uri.fromFile(File(url)) else url.toUri()
    } ?: youTubeResolvePlaceholderUri(id)

    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}
