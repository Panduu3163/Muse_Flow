package com.example

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.Immutable
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

/** One entry in the playback queue, as the Now Playing queue list renders it. [mediaId] is the
 * only field stable across a *different* item shifting into this [index] (e.g. after removing an
 * earlier row) - it's what the queue list keys its rows by, so a swipe-to-remove's per-row gesture
 * state doesn't leak onto whatever song slides up to take the removed row's place. */
@Immutable
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isCurrent: Boolean,
)

/** What the UI needs to know about whatever is currently playing. Marked [Immutable] so Compose
 * can skip recomposing a composable that receives an structurally-equal instance as a parameter -
 * meaningful now that [PlayerViewModel] no longer rebuilds this (queue included) on every position
 * tick, only on a real player event (see the tick loop's own comment). */
@Immutable
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
    val speed: Float = 1f,
    val pitch: Float = 1f,
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
    private val downloadRepository = DownloadRepository.getInstance(application)
    private val router = MusicSearchRouter(application)
    private val appSettingsRepository = AppSettingsRepository(application)
    private var latestAppSettings = AppSettingsState()

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    // A separate, faster-ticking position stream for the lyrics karaoke sweep. `_state.positionMs`
    // only refreshes every 500ms (see below), which is fine for the scrubber but far too coarse to
    // animate a single word's in-progress highlight smoothly - most sung words (200-600ms) would
    // get zero or one intermediate update and visibly snap instead of glide. This ticks independently
    // so lyrics can read it without forcing the heavier full NowPlayingState (queue list included)
    // to rebuild at the same rate.
    private val _lyricsPositionMs = MutableStateFlow(0L)
    val lyricsPositionMs: StateFlow<Long> = _lyricsPositionMs.asStateFlow()

    val sleepTimerRemainingMs: StateFlow<Long?> = SleepTimer.remainingMs

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer(player)
    }

    init {
        connect()
        viewModelScope.launch {
            appSettingsRepository.state.collect { latestAppSettings = it }
        }
        // Media3 pushes state changes through the listener, but not a continuously ticking
        // position - so the progress bar is refreshed on a light timer while something plays.
        // The same tick drives crossfade: a fixed formula from position/duration/fade length,
        // recomputed every tick rather than tracked as separate fade-in/fade-out timers, so toggling
        // the setting mid-track or a fade window longer than half the track self-corrects instead
        // of leaving volume stuck partway.
        //
        // Deliberately does NOT call the full `syncFromPlayer()` here - that rebuilds the entire
        // queue list (a `getMediaItemAt` + allocation per queue entry) and every other metadata
        // field from scratch, for state that essentially never changes between ticks. Doing that
        // twice a second, forever, while anything plays was real recomposition/allocation pressure
        // behind every screen that reads `state` (Home/Library's mini-player visibility check, the
        // mini-player itself, Now Playing) even when nothing but the position had moved. Metadata
        // and queue changes still reach `_state` immediately through the real `onEvents` listener
        // below - this tick only ever touches the one field that has no event of its own.
        viewModelScope.launch {
            while (true) {
                delay(500)
                val controller = controller ?: continue
                if (controller.isPlaying) {
                    _state.value = _state.value.copy(positionMs = controller.currentPosition.coerceAtLeast(0L))
                }
                applyCrossfadeVolume(controller)
            }
        }
        // Lyrics-only position tick, fast enough for the karaoke sweep to read as continuous
        // rather than stepped. Deliberately its own loop instead of just shortening the loop above:
        // that one rebuilds the whole NowPlayingState (queue list included) on every tick, which
        // would mean redoing that work 5x as often for no benefit to anything but lyrics.
        viewModelScope.launch {
            while (true) {
                delay(60)
                val controller = controller ?: continue
                if (controller.isPlaying) {
                    _lyricsPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                }
            }
        }
    }

    private fun applyCrossfadeVolume(controller: MediaController) {
        val durationMs = controller.duration
        val settings = latestAppSettings
        if (!settings.crossfadeEnabled || durationMs <= 0L) {
            if (controller.volume != 1f) controller.volume = 1f
            return
        }
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val fadeMs = settings.crossfadeDurationMs.toLong().coerceAtMost(durationMs / 2).coerceAtLeast(1L)
        val fadeInFactor = (positionMs.toFloat() / fadeMs).coerceIn(0f, 1f)
        val fadeOutFactor = ((durationMs - positionMs).toFloat() / fadeMs).coerceIn(0f, 1f)
        controller.volume = minOf(fadeInFactor, fadeOutFactor, 1f)
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
        // Keeps the fast lyrics tick in sync with seeks/track changes/pauses, which only reach it
        // otherwise via `onEvents` - without this a seek would leave karaoke stuck at the pre-seek
        // word until the next 60ms tick happens to land while playing.
        _lyricsPositionMs.value = player.currentPosition.coerceAtLeast(0L)

        val metadata = player.mediaMetadata
        val currentIndex = player.currentMediaItemIndex
        val queue = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueItem(
                index = index,
                mediaId = item.mediaId,
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
            speed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
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
        viewModelScope.launch {
            val items = queue.map { it.toMediaItem() }
            val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

            controller.setMediaItems(items, startIndex, 0L)
            controller.prepare()
            controller.play()

            // Without this, Home's "Recently played"/"On repeat" shelves and Library's Top 50
            // would never populate - nothing else in the app writes playback history.
            historyRepository.recordPlayed(track.toPlayableTrack(track.id.hashCode()))
        }
    }

    /**
     * Queues [track] directly after whatever is playing.
     *
     * With nothing loaded there is no "after" to insert into, so this plays the track outright.
     * A "Play next" that silently did nothing on a cold start would read as a broken control.
     */
    fun playNext(track: TrackResult) = playNext(listOf(track))

    /**
     * Queues [tracks] directly after whatever is playing, keeping their order.
     *
     * Inserted as one block rather than one call per track: repeated single inserts at
     * `currentIndex + 1` would play the selection backwards, and each would push its own timeline
     * change through the controller.
     */
    fun playNext(tracks: List<TrackResult>) {
        val controller = controller ?: return
        if (tracks.isEmpty()) return
        if (controller.mediaItemCount == 0) return play(tracks.first(), tracks)
        viewModelScope.launch {
            val items = tracks.map { it.toMediaItem() }
            controller.addMediaItems(controller.currentMediaItemIndex + 1, items)
        }
    }

    /** Appends [track] to the end of the queue, or plays it when the queue is empty. */
    fun addToQueue(track: TrackResult) = addToQueue(listOf(track))

    fun addToQueue(tracks: List<TrackResult>) {
        val controller = controller ?: return
        if (tracks.isEmpty()) return
        if (controller.mediaItemCount == 0) return play(tracks.first(), tracks)
        viewModelScope.launch {
            controller.addMediaItems(tracks.map { it.toMediaItem() })
        }
    }

    /**
     * Replaces the queue with a radio mix seeded from [seed].
     *
     * [onEmpty] fires when the backend returns nothing - the legacy extractor has no radio
     * endpoint, and a network failure looks the same from here. The caller reports it rather than
     * leaving the tap looking ignored.
     */
    fun startRadio(seed: TrackResult, onEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            val mix = runCatching { router.getRadioTracks(seed.id) }.getOrDefault(emptyList())
            if (mix.isEmpty()) {
                onEmpty()
                return@launch
            }
            play(mix.first(), mix)
        }
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

    /** [speed]/[pitch] are both 0.25x-2x multipliers; Media3 handles the actual resampling. */
    fun setPlaybackSpeed(speed: Float, pitch: Float = speed) {
        controller?.playbackParameters = androidx.media3.common.PlaybackParameters(
            speed.coerceIn(0.25f, 2f),
            pitch.coerceIn(0.25f, 2f),
        )
    }

    fun startSleepTimer(minutes: Int) {
        SleepTimer.start(minutes * 60_000L) { controller?.pause() }
    }

    fun cancelSleepTimer() = SleepTimer.cancel()

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

    /**
     * Moves the queue entry at [from] to [to].
     *
     * Both are timeline indices, matching what [QueueItem.index] carries - the queue list renders
     * the timeline order, not the shuffle order, so a drag means the same thing with shuffle on.
     */
    fun moveQueueItem(from: Int, to: Int) {
        val controller = controller ?: return
        val count = controller.mediaItemCount
        if (from == to || from !in 0 until count || to !in 0 until count) return
        controller.moveMediaItem(from, to)
    }

    /**
     * Drops the queue entry at [index].
     *
     * Removing whatever is playing is allowed: Media3 advances to the next item, which is what
     * someone clearing the current track out of the queue is asking for.
     */
    fun removeQueueItem(index: Int) {
        val controller = controller ?: return
        if (index in 0 until controller.mediaItemCount) controller.removeMediaItem(index)
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

    /**
     * A directStreamUrl is playable as-is; a track already downloaded reads from its local file
     * even when reached from search/a shelf rather than Library's own Downloads section (there's
     * no reason to re-stream something already on disk, resolve delay included); everything else
     * goes through the resolve placeholder so [PlaybackService] resolves it lazily.
     *
     * Downloaded tracks store a bare filesystem path, not a URI. `String.toUri()` on one of those
     * yields a scheme-less Uri that DefaultDataSource can't dispatch (ExoPlayer reports a bare
     * "Source error"), and any space or ':' in the filename would need escaping too - both of
     * which Uri.fromFile handles.
     */
    private suspend fun TrackResult.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(source)
            .apply { imageUrl?.let { setArtworkUri(it.toUri()) } }
            .build()

        val downloadedPath = runCatching { downloadRepository.getByKey(downloadKey()) }
            .getOrNull()
            ?.filePath
            ?.takeIf { File(it).exists() }

        val uri = when {
            downloadedPath != null -> Uri.fromFile(File(downloadedPath))
            directStreamUrl != null -> {
                if (directStreamUrl.startsWith("/")) Uri.fromFile(File(directStreamUrl)) else directStreamUrl.toUri()
            }
            else -> youTubeResolvePlaceholderUri(id)
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
