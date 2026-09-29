package com.example

import android.app.Application
import android.content.ComponentName
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** [artist] broken back out into individual credits (name + that artist's own browseId), so
     * Now Playing's byline can send a tap on one artist's name to *that* artist's page on a
     * multi-artist track instead of always the first one - see [ArtistCredit]. Always has at
     * least one entry once [artist] is non-blank (synthesized from [artist] as a whole when the
     * source never provided structured per-artist credits, e.g. a local file), so this is always
     * the single source of truth for rendering/clicking the artist line. */
    val artistCredits: List<ArtistCredit> = emptyList(),
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
    val nextArtworkUrl: String? = null,
    val previousArtworkUrl: String? = null,
    /** True for a track playing straight off this device's own media store, not something
     * downloaded through the app or streamed. Now Playing shows this as its own glyph, distinct
     * from "downloaded" - see [com.example.ui.component.TrackRow]'s own isLocalDevice doc for why
     * the two shouldn't share one icon. */
    val isLocalDevice: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val speed: Float = 1f,
    val pitch: Float = 1f,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Same `"title::artist"` shape as [Track.downloadKey]/[TrackResult.downloadKey], so a track
     * list can tell whether one of its own rows is the one currently loaded in the player by
     * comparing keys - the "now playing" equalizer badge on [com.example.ui.component.TrackRow]
     * is driven by this. Null while nothing is loaded. */
    val currentTrackKey: String?
        get() = title.trim().takeIf { it.isNotBlank() }?.let { "${it.lowercase()}::${artist.trim().lowercase()}" }
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

    // One-shot, not state: a playback failure is an event ("this attempt just failed"), not a
    // persistent condition to keep re-showing on every recomposition/screen revisit. Previously
    // there was no feedback at all when a track failed to open - it just silently didn't play (or,
    // mid-queue, silently skipped to another track via PlaybackService's own recovery), which was
    // indistinguishable from "nothing happened" and made a real failure impossible to diagnose.
    private val _playbackErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playbackErrors: SharedFlow<String> = _playbackErrors.asSharedFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer(player)

        override fun onPlayerError(error: PlaybackException) {
            val title = _state.value.title.takeIf { it.isNotBlank() }
            _playbackErrors.tryEmit(
                if (title != null) "Couldn't play \"$title\"" else "Couldn't play this track"
            )
        }
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

        val artistDisplay = metadata.artist?.toString().orEmpty()
        val creditNames = metadata.extras?.getStringArrayList("artistCreditNames")
        val creditIds = metadata.extras?.getStringArrayList("artistCreditIds")
        val artistCredits = if (!creditNames.isNullOrEmpty() && creditNames.size == creditIds?.size) {
            creditNames.zip(creditIds).map { (name, id) -> ArtistCredit(name, id.takeIf { it.isNotEmpty() }) }
        } else if (artistDisplay.isNotBlank()) {
            // No structured per-artist credits (a local file, or a queue item restored from the
            // persisted queue - see QueueRepository) - one credit spanning the whole display
            // string, same single-target behavior this always had.
            listOf(ArtistCredit(artistDisplay, metadata.extras?.getString("artistId")))
        } else {
            emptyList()
        }

        _state.value = NowPlayingState(
            title = metadata.title?.toString().orEmpty(),
            artist = artistDisplay,
            artistCredits = artistCredits,
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
            nextArtworkUrl = player.nextMediaItemIndex.takeIf { it in queue.indices }
                ?.let { queue[it].artworkUrl },
            previousArtworkUrl = player.previousMediaItemIndex.takeIf { it in queue.indices }
                ?.let { queue[it].artworkUrl },
            // A device media-store track's mediaId is its own content:// URI (see
            // LocalAudioProvider) - nothing else in the app produces a mediaId with that scheme,
            // so it's a reliable, self-contained signal with no extra plumbing needed.
            isLocalDevice = player.currentMediaItem?.mediaId?.startsWith("content://") == true,
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
            // PlaybackService records actual playback, including automatic queue transitions.
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

    /**
     * Skips to the next item, resuming playback even if paused - a paused skip used to leave the
     * next track sitting paused too, which reads as broken ("I hit next, why isn't it playing?").
     *
     * On the last item, [MediaController.hasNextMediaItem] is false and a plain
     * `seekToNextMediaItem()` would silently do nothing (this was the "hit next on the last song,
     * nothing happens" bug). Sends [ACTION_AUTOPLAY_AND_ADVANCE] instead, which reuses the exact
     * taste-blended continuation a queue ending naturally already gets - the queue should always
     * have more of the user's own taste to fall into, not just stop.
     */
    fun next() {
        val controller = controller ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        } else {
            controller.sendCustomCommand(SessionCommand(ACTION_AUTOPLAY_AND_ADVANCE, Bundle.EMPTY), Bundle.EMPTY)
        }
    }

    fun previous() {
        val controller = controller ?: return
        controller.seekToPreviousMediaItem()
        controller.play()
    }

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
        // Skipped entirely for a track already sourced from the device's own media store - it has
        // its own correct content:// URI in directStreamUrl below, and this lookup matches purely
        // by title+artist (downloadKey has no other identity to go on), so a same-named track the
        // app separately downloaded could otherwise shadow it with an unrelated file.
        val downloadedPath = if (sourceType == MusicSource.LOCAL_DEVICE) null else {
            runCatching { downloadRepository.getByKey(downloadKey()) }
                .getOrNull()
                ?.filePath
                ?.takeIf { File(it).exists() }
        }

        // A downloaded track's `imageUrl` is still the original *remote* thumbnail URL -
        // DownloadRepository doesn't rewrite it. What it does instead: save a plain sibling image
        // file at download time (DownloadRepository.localCoverFile) - the reliable source here,
        // since it works no matter what audio container the download actually is (most are
        // Opus-in-WebM, which AudioTagger's ID3/MP4 embedding can't touch at all - see its own
        // download-time comment).
        //
        // Set as `artworkUri` (a real file:// URI), not `artworkData` (raw bytes) - this app's own
        // NowPlayingState.artworkUrl (below, and in the media-item-transition listener) only ever
        // reads `mediaMetadata.artworkUri`, never `artworkData`. Setting bytes instead of a URI is
        // exactly what silently broke in-app artwork for every downloaded track, online or offline,
        // while the system notification (whose own builder reads both) kept working - the split
        // that made this look like an offline-only bug when it wasn't.
        val localCoverUri = downloadedPath?.let { path ->
            DownloadRepository.localCoverFile(getApplication(), downloadKey())?.let { Uri.fromFile(it) }
        }
        // Embedded-tag bytes are kept as a last-resort *notification-only* fallback for whatever
        // handful of MP3/M4A downloads predate the local-cover-file fix and never got one - there's
        // no URI to give those to Coil, so in-app art for that narrow legacy case still won't show,
        // but the alternative (a temp file rewritten from the tag every playback) isn't worth it
        // for a shrinking edge case new downloads no longer hit at all.
        val embeddedArtBytes = if (localCoverUri == null && downloadedPath != null) readEmbeddedArtwork(downloadedPath) else null

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(source)
            // Carried through extras so Now Playing's artist name can navigate straight to the
            // artist page - MediaMetadata has no first-class artistId field of its own. The two
            // parallel arrays (rather than a single delimited string) are what let a multi-artist
            // credit survive round-tripping through a Bundle without needing to invent an escaping
            // scheme for a name that happens to contain the join separator.
            .setExtras(android.os.Bundle().apply {
                artistId?.let { putString("artistId", it) }
                albumId?.let { putString("albumId", it) }
                if (artistCredits.isNotEmpty()) {
                    putStringArrayList("artistCreditNames", ArrayList(artistCredits.map { it.name }))
                    putStringArrayList("artistCreditIds", ArrayList(artistCredits.map { it.id.orEmpty() }))
                }
            })
            .apply {
                when {
                    localCoverUri != null -> setArtworkUri(localCoverUri)
                    embeddedArtBytes != null -> setArtworkData(embeddedArtBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    downloadedPath == null -> imageUrl?.let { setArtworkUri(it.toUri()) }
                    // Downloaded but no local cover and no embedded art either (fetch failed at
                    // download time) - nothing reliable to show, matching how a track with no
                    // imageUrl at all already renders (the row/notification's placeholder).
                }
            }
            .build()

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

    companion object {
        /** Reads whatever picture [AudioTagger.embedIfSupported] wrote into a downloaded file's own
         * ID3/MP4 tag, straight off disk - `MediaMetadataRetriever` doesn't touch the network, so
         * this is the one artwork source actually guaranteed to work offline. Returns null (not an
         * exception) for a file with no embedded picture, or any read failure - artwork is always
         * optional, never worth failing playback over. */
        internal suspend fun readFileBytes(file: File): ByteArray? = withContext(Dispatchers.IO) {
            runCatching { file.readBytes() }.getOrNull()
        }

        internal suspend fun readEmbeddedArtwork(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
            // Not `.use { }` - MediaMetadataRetriever only implements AutoCloseable from API 29,
            // and this app's minSdk is 24; `release()` has always existed, so that's the one call
            // safe across every supported version.
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                retriever.embeddedPicture
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
