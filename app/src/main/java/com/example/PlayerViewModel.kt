package com.example

import kotlinx.coroutines.flow.collectLatest

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
import com.example.ui.screens.asTrackResult
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val durationMs: Long = 0L,
    val artistId: String? = null,
    val albumId: String? = null,
    val album: String? = null,
)

/** Walk the actual next-item order once, including one repeat-all wrap when enabled, without
 * getting trapped by a malformed/cyclic timeline. Indices remain Media3 timeline indices. */
internal fun upcomingQueueIndices(
    currentIndex: Int,
    itemCount: Int,
    nextIndex: (Int) -> Int,
): List<Int> {
    if (currentIndex !in 0 until itemCount) return emptyList()
    val seen = mutableSetOf(currentIndex)
    val result = ArrayList<Int>(itemCount - 1)
    var cursor = currentIndex
    while (result.size < itemCount - 1) {
        val next = nextIndex(cursor)
        if (next !in 0 until itemCount || !seen.add(next)) break
        result += next
        cursor = next
    }
    return result
}

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
    val playWhenReady: Boolean = false,
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
    /** Items reached by Next from the current song, in actual playback order (including shuffle). */
    val upcomingQueue: List<QueueItem> = emptyList(),
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** Changes on a seek or track transition so lyric motion can restart at the real position. */
    val positionRevision: Int = 0,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Stable recording identity for list playing indicators, including paused playback. */
    val currentPlaybackId: String?
        get() = queue.firstOrNull { it.isCurrent }?.mediaId

    /** Buffering is not a user pause: keep the action stable and allow cancelling pending play. */
    val showPauseAction: Boolean
        get() = isPlaying || (isBuffering && playWhenReady)

    /** Recording-aware repository key; legacy metadata fallback only when the item has no ID. */
    val currentTrackKey: String?
        get() = title.takeIf { it.isNotBlank() }?.let { recordingStorageKey(it, artist, currentPlaybackId, null) }
}

/** `mm:ss`, or `-:--` before a duration is known. */
fun Long.asPlaybackTime(): String {
    if (this <= 0L) return "-:--"
    val totalSeconds = this / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** A real YouTube id has a stable thumbnail endpoint when a restored item lacks artwork metadata. */
private fun MediaItem.displayArtworkUrl(): String? =
    mediaMetadata.artworkUri?.toString()
        ?: mediaId.takeIf { it.looksLikeYouTubeVideoId() }
            ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

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
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var connectionTimeout: Job? = null
    private var playRequestJob: Job? = null
    private var cleared = false
    private data class PlayRequest(val track: TrackResult, val queue: List<TrackResult>, val fromSearch: Boolean)
    private data class RetrySnapshot(val items: List<MediaItem>, val index: Int, val positionMs: Long)
    private var pendingPlay: PlayRequest? = null
    private var pendingRetry: RetrySnapshot? = null
    private var lastKnownItems: List<MediaItem> = emptyList()
    private var positionRevision = 0
    private var lastRemovedQueueItem: Pair<Int, MediaItem>? = null
    private val historyRepository = PlaybackHistoryRepository.getInstance(application)
    private val downloadRepository = DownloadRepository.getInstance(application)
    private val router = MusicSearchRouter(application)

    /** Older downloads predate stored artist IDs. Resolve their displayed credit on demand. */
    suspend fun resolveArtistId(name: String, songTitle: String): String? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null
        val stored = downloadRepository.completedDownloads.first()
            .firstOrNull { it.artist.equals(normalized, ignoreCase = true) && !it.artistId.isNullOrBlank() }
            ?.artistId
        if (stored != null) return stored
        if (!isOnline(getApplication())) return null
        val songMatches = runCatching { router.searchTracks("$songTitle $normalized") }
            .getOrNull().orEmpty().filter { result ->
                result.title.equals(songTitle, ignoreCase = true) &&
                    result.artist.contains(normalized, ignoreCase = true)
            }
        songMatches.firstNotNullOfOrNull { result ->
            result.artistCredits.firstOrNull { it.name.equals(normalized, ignoreCase = true) }?.id
        }?.let { return it }
        songMatches.firstOrNull()?.artistId?.let { return it }
        val matches = runCatching { router.searchArtists(normalized) }.getOrNull().orEmpty()
        return matches.firstOrNull { it.name.equals(normalized, ignoreCase = true) }?.id
    }

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()
    private val _playbackFailure = MutableStateFlow<String?>(null)
    val playbackFailure: StateFlow<String?> = _playbackFailure.asStateFlow()
    private val _artworkRetryRevision = MutableStateFlow(0)
    val artworkRetryRevision: StateFlow<Int> = _artworkRetryRevision.asStateFlow()

    // A separate, faster-ticking position stream for the lyrics karaoke sweep. `_state.positionMs`
    // only refreshes every 500ms (see below), which is fine for the scrubber but far too coarse to
    // animate a single word's in-progress highlight smoothly - most sung words (200-600ms) would
    // get zero or one intermediate update and visibly snap instead of glide. This ticks independently
    // so lyrics can read it without forcing the heavier full NowPlayingState (queue list included)
    // to rebuild at the same rate.
    private val playingForUi = MutableStateFlow(false)
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
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.size() == 1 && events.contains(Player.EVENT_VOLUME_CHANGED)) return
            if (player.isPlaying || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                _playbackFailure.value = null
            }
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                positionRevision++
            }
            syncFromPlayer(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = _state.value.title.takeIf { it.isNotBlank() }
            val message = if (title != null) "Couldn't play \"$title\"" else "Couldn't play this track"
            _playbackFailure.value = message
            _playbackErrors.tryEmit(message)
        }
    }

    init {
        connect()
        // UI clocks exist only while visible collectors need them and playback advances.
        // Audio effects belong to PlaybackService, so closing the UI cannot disable them.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_state.subscriptionCount, playingForUi) { count, playing -> count > 0 && playing }
                .collectLatest { needed ->
                    if (needed) while (true) {
                        controller?.let { _state.value = _state.value.copy(positionMs = it.currentPosition.coerceAtLeast(0L)) }
                        delay(500)
                    }
                }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_lyricsPositionMs.subscriptionCount, playingForUi) { count, playing -> count > 0 && playing }
                .collectLatest { needed ->
                    if (needed) while (true) {
                        controller?.let { _lyricsPositionMs.value = it.currentPosition.coerceAtLeast(0L) }
                        delay(60)
                    }
                }
        }
    }

    private fun connect() {
        if (cleared || controller?.isConnected == true || controllerFuture != null) return
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerViewModel.controller !== controller || cleared) return
                    controller.removeListener(listener)
                    this@PlayerViewModel.controller = null
                    playRequestJob?.cancel()
                    playingForUi.value = false
                    _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                    _playbackFailure.value = "Playback connection was interrupted. Retry or choose a song."
                }
            }).buildAsync()
        controllerFuture = future
        connectionTimeout = viewModelScope.launch {
            delay(12_000)
            if (controllerFuture === future) {
                controllerFuture = null
                MediaController.releaseFuture(future)
                connectionFailed("Couldn't start the player. Tap the song to retry.")
            }
        }
        future.addListener(
            {
                if (cleared || controllerFuture !== future) {
                    MediaController.releaseFuture(future)
                } else {
                    controllerFuture = null
                    connectionTimeout?.cancel()
                    runCatching { future.get() }.onSuccess { newController ->
                        controller = newController
                        newController.addListener(listener)
                        val play = pendingPlay.also { pendingPlay = null }
                        val retry = pendingRetry.also { pendingRetry = null }
                        syncFromPlayer(newController)
                        when {
                            play != null -> playWithContext(play.track, play.queue, play.fromSearch)
                            retry != null -> {
                                newController.setMediaItems(retry.items, retry.index, retry.positionMs)
                                newController.prepare()
                                newController.play()
                            }
                        }
                    }.onFailure {
                        connectionFailed("Couldn't connect to playback. Tap the song to retry.")
                    }
                }
            },
            ContextCompat.getMainExecutor(getApplication())
        )
    }

    private fun connectionFailed(message: String) {
        pendingPlay = null
        pendingRetry = null
        _playbackFailure.value = message
        _playbackErrors.tryEmit(message)
    }

    private fun syncFromPlayer(player: Player) {
        playingForUi.value = player.isPlaying
        // Keeps the fast lyrics tick in sync with seeks/track changes/pauses, which only reach it
        // otherwise via `onEvents` - without this a seek would leave karaoke stuck at the pre-seek
        // word until the next 60ms tick happens to land while playing.
        _lyricsPositionMs.value = player.currentPosition.coerceAtLeast(0L)

        val metadata = player.mediaMetadata
        val currentIndex = player.currentMediaItemIndex
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        lastKnownItems = items
        val queue = items.mapIndexed { index, item ->
            QueueItem(
                index = index,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                artworkUrl = item.displayArtworkUrl(),
                isCurrent = index == currentIndex,
                durationMs = if (index == currentIndex && player.duration > 0) player.duration
                    else item.mediaMetadata.extras?.getLong("durationMs") ?: 0L,
                artistId = item.mediaMetadata.extras?.getString("artistId"),
                albumId = item.mediaMetadata.extras?.getString("albumId"),
                album = item.mediaMetadata.albumTitle?.toString(),
            )
        }
        val upcoming = upcomingIndices(player).map(queue::get)

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
            artworkUrl = player.currentMediaItem?.displayArtworkUrl(),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
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
            upcomingQueue = upcoming,
            speed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
            positionRevision = positionRevision,
        )
    }

    private fun upcomingIndices(player: Player): List<Int> {
        val timeline = player.currentTimeline
        val traversalRepeatMode = if (player.repeatMode == Player.REPEAT_MODE_ALL) {
            Player.REPEAT_MODE_ALL
        } else {
            // Repeat-one keeps the current song looping, but the queued songs still need to be
            // editable and savable for when the listener turns repeat-one off.
            Player.REPEAT_MODE_OFF
        }
        return upcomingQueueIndices(player.currentMediaItemIndex, player.mediaItemCount) { index ->
            timeline.getNextWindowIndex(index, traversalRepeatMode, player.shuffleModeEnabled)
        }
    }

    /**
     * Plays [track] and queues the rest of [queue] behind it.
     *
     * YouTube results carry only a videoId, so each item is handed to the service as a
     * [youTubeResolvePlaceholderUri]; [PlaybackService] swaps in a real URL at HTTP-open time -
     * which is what routes playback through whichever extractor is selected.
     */
    fun play(track: TrackResult, queue: List<TrackResult> = listOf(track)) =
        playWithContext(track, queue, fromSearch = false)

    /** Online Search starts one chosen recording plus radio, never the other search matches. */
    fun playFromSearch(track: TrackResult, results: List<TrackResult>) =
        playWithContext(track, results, fromSearch = true)

    private fun playWithContext(track: TrackResult, queue: List<TrackResult>, fromSearch: Boolean) {
        if (cleared) return
        pendingRetry = null
        val controller = controller?.takeIf { it.isConnected }
        if (controller == null) {
            pendingPlay = PlayRequest(track, queue.toList(), fromSearch)
            connect()
            return
        }
        if (!controller.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) ||
            !controller.isCommandAvailable(Player.COMMAND_PREPARE) || !controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
            connectionFailed("Playback controls are unavailable. Close and reopen MuseFlow, then retry.")
            return
        }
        playRequestJob?.cancel()
        playRequestJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val origin = playbackQueueOrigin(track.sourceType, isOnline(app), fromSearch)
            val source = initialPlaybackQueue(track, queue, origin)
            val playable = when (origin) {
                QueueOrigin.ONLINE, QueueOrigin.SEARCH_RADIO -> source
                QueueOrigin.ON_DEVICE -> {
                    val local = runCatching { LocalAudioProvider(app).search("") }.getOrDefault(emptyList())
                    val deviceSource = source.filter { it.sourceType == MusicSource.LOCAL_DEVICE }
                    val window = queueWindowAround(deviceSource,
                        deviceSource.indexOfFirst { it.id == track.id }.coerceAtLeast(0)).first
                    fillLocalQueue(window, local, TrackResult::id)
                }
                QueueOrigin.OFFLINE_DOWNLOADS -> {
                    val downloads = downloadRepository.completedDownloads.first()
                        .filter { File(it.filePath).isFile }
                    val downloadResults = downloads.map { it.toTrack(app).asTrackResult() }
                    val ids = downloadResults.map { it.playbackId() }.toSet()
                    val downloadedSource = source.filter { it.playbackId() in ids }
                    val window = queueWindowAround(downloadedSource,
                        downloadedSource.indexOfFirst { it.id == track.id }.coerceAtLeast(0)).first
                    fillLocalQueue(window, downloadResults, TrackResult::playbackId)
                }
            }
            if (playable.none { it.id == track.id }) {
                _playbackErrors.tryEmit("This song is not available offline")
                return@launch
            }
            val selected = playable.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val (window, startIndex) = queueWindowAround(playable, selected)
            val items = window.map { it.toMediaItem(origin) }

            controller.setMediaItems(items, startIndex, 0L)
            controller.prepare()
            controller.play()
            if (origin in setOf(QueueOrigin.ONLINE, QueueOrigin.SEARCH_RADIO) && window.size < MAX_QUEUE_ITEMS) {
                controller.sendCustomCommand(SessionCommand(ACTION_FILL_QUEUE, Bundle.EMPTY), Bundle.EMPTY)
            }

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
            val items = tracks.take(MAX_QUEUE_ITEMS).map { it.toMediaItem() }
                .take((MAX_QUEUE_ITEMS - controller.mediaItemCount).coerceAtLeast(0))
            if (items.isEmpty()) return@launch
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
            val items = tracks.take(MAX_QUEUE_ITEMS).map { it.toMediaItem() }
                .take((MAX_QUEUE_ITEMS - controller.mediaItemCount).coerceAtLeast(0))
            if (items.isNotEmpty()) controller.addMediaItems(items)
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

    /** Add a radio continuation without replacing the song, played history or manual queue.
     * A radio result is useful here; auto-filling every search/album queue would override intent. */
    fun addRadioFromCurrent(onResult: (Int) -> Unit) {
        val controller = controller ?: return onResult(0)
        if (controller.mediaItemCount >= MAX_QUEUE_ITEMS) return onResult(-1)
        val mediaId = controller.currentMediaItem?.mediaId
        if (mediaId == null || !mediaId.looksLikeYouTubeVideoId() || !isOnline(getApplication())) {
            onResult(-2)
            return
        }
        viewModelScope.launch {
            val candidates = try { router.getRadioTracks(mediaId) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            if (controller.currentMediaItem?.mediaId != mediaId) {
                onResult(0)
                return@launch
            }
            val existing = (0 until controller.mediaItemCount)
                .map { controller.getMediaItemAt(it).mediaId }.toSet()
            val additions = candidates.distinctBy { it.id }
                .filter { it.id !in existing }
                .take((MAX_QUEUE_ITEMS - controller.mediaItemCount).coerceAtLeast(0))
                .map { it.toMediaItem() }
                .take((MAX_QUEUE_ITEMS - controller.mediaItemCount).coerceAtLeast(0))
            if (additions.isNotEmpty()) controller.addMediaItems(additions)
            onResult(if (additions.isEmpty() && controller.mediaItemCount >= MAX_QUEUE_ITEMS) -1
                else additions.size)
        }
    }

    fun togglePlayPause() {
        val controller = controller?.takeIf { it.isConnected } ?: return retryCurrent()
        if (controller.playWhenReady && controller.playbackState != Player.STATE_ENDED) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_ENDED) controller.seekToDefaultPosition()
            // A queue restored while offline intentionally remains idle until the listener asks
            // to play. Preparing here makes that explicit Play work after connectivity returns.
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    /** Retry the current queue item without rebuilding or advancing the queue. A failed cached
     * YouTube URL is discarded so the next open resolves a fresh one. */
    fun retryCurrent() {
        val controller = controller?.takeIf { it.isConnected }
        if (controller == null) {
            if (lastKnownItems.isNotEmpty()) {
                val state = _state.value
                pendingRetry = RetrySnapshot(lastKnownItems.toList(),
                    state.queue.firstOrNull { it.isCurrent }?.index?.coerceIn(lastKnownItems.indices) ?: 0,
                    state.positionMs.coerceAtLeast(0L))
            }
            connect()
            return
        }
        if (controller.mediaItemCount == 0) return
        controller.currentMediaItem?.localConfiguration?.uri
            ?.let(::youTubeVideoIdFromResolvePlaceholder)
            ?.let(StreamResolverRouter::invalidate)
        _playbackFailure.value = null
        // Retry artwork too: its URL is unchanged when connectivity returns.
        _artworkRetryRevision.value++
        controller.sendCustomCommand(
            SessionCommand(ACTION_RESET_PLAYBACK_ERRORS, Bundle.EMPTY), Bundle.EMPTY,
        ).addListener({
            viewModelScope.launch {
                if (this@PlayerViewModel.controller === controller) {
                    controller.prepare()
                    controller.play()
                }
            }
        }, MoreExecutors.directExecutor())
    }

    /** Discard a queued stream's resolved URL; replaying it requests a fresh source. */
    fun refetchQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.getMediaItemAt(index).localConfiguration?.uri
            ?.let(::youTubeVideoIdFromResolvePlaceholder)
            ?.let(StreamResolverRouter::invalidate)
        if (index == player.currentMediaItemIndex) retryCurrent()
        else _artworkRetryRevision.value++
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
            streamUrl = item.localConfiguration?.uri?.takeIf { it.scheme in setOf("file", "content") }?.toString(),
            sourceType = if (item.mediaId.startsWith("content://") || item.mediaId.startsWith("file://")) MusicSource.LOCAL_DEVICE else MusicSource.YOUTUBE_MUSIC,
            sourceId = item.mediaId,
            artistId = metadata.extras?.getString("artistId"),
            albumId = metadata.extras?.getString("albumId"),
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
        if (index in 0 until controller.mediaItemCount) {
            lastRemovedQueueItem = index to controller.getMediaItemAt(index)
            controller.removeMediaItem(index)
        }
    }

    fun undoLastQueueRemoval() {
        val controller = controller ?: return
        val (index, item) = lastRemovedQueueItem ?: return
        lastRemovedQueueItem = null
        controller.addMediaItem(index.coerceIn(0, controller.mediaItemCount), item)
    }

    /** Keep only the current song. The service cancels an in-flight autoplay refill too. */
    fun clearUpcomingQueue() {
        val controller = controller ?: return
        lastRemovedQueueItem = null
        controller.sendCustomCommand(SessionCommand(ACTION_CLEAR_QUEUE, Bundle.EMPTY), Bundle.EMPTY)
    }

    /** Save the current song followed by the actual Next order as a local playlist. */
    suspend fun saveRemainingQueueAsPlaylist(name: String): Boolean {
        val controller = controller ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty() || controller.currentMediaItemIndex !in 0 until controller.mediaItemCount) return false
        val indices = listOf(controller.currentMediaItemIndex) + upcomingIndices(controller)
        val tracks = indices.map { index ->
            val item = controller.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            val local = item.mediaId.startsWith("content://")
            Track(
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                album = metadata.albumTitle?.toString().orEmpty(),
                duration = if (index == controller.currentMediaItemIndex) controller.duration.asPlaybackTime() else "-:--",
                plays = "",
                gradientIndex = item.mediaId.hashCode(),
                imageUrl = metadata.artworkUri?.toString(),
                streamUrl = if (local) item.localConfiguration?.uri?.toString() else null,
                sourceType = if (local) MusicSource.LOCAL_DEVICE else MusicSource.YOUTUBE_MUSIC,
                sourceId = item.mediaId,
                artistId = metadata.extras?.getString("artistId"),
                albumId = metadata.extras?.getString("albumId"),
            )
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                PlaylistRepository.getInstance(getApplication()).importOnlinePlaylist(
                    trimmed, tracks.firstOrNull()?.imageUrl, tracks,
                )
            }.isSuccess
        }
    }

    fun playQueueItem(index: Int) {
        val controller = controller ?: return
        controller.seekTo(index, 0L)
        controller.play()
    }

    fun stop() {
        pendingPlay = null
        pendingRetry = null
        playRequestJob?.cancel()
        connectionTimeout?.cancel()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        controller?.stop()
        controller?.clearMediaItems()
        lastKnownItems = emptyList()
        playingForUi.value = false
        _state.value = NowPlayingState()
        _playbackFailure.value = null
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
    private suspend fun TrackResult.toMediaItem(origin: QueueOrigin = QueueOrigin.ONLINE): MediaItem {
        // Device tracks use their own URI. A cached download must match this exact recording,
        // rather than shadowing another version with the same title and artist.
        val downloadedPath = if (sourceType == MusicSource.LOCAL_DEVICE) null else {
            runCatching { downloadRepository.getByKey(downloadKey()) }
                .getOrNull()
                ?.takeIf { canReuseDownloadForPlayback(id, directStreamUrl, it.sourceId, it.filePath) }
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
            DownloadRepository.localCoverFile(getApplication(), downloadKey(), path)?.let { Uri.fromFile(it) }
        }
        // Embedded-tag bytes are a notification fallback for older downloads without a sibling
        // cover file. When online, the original URL can still fill the in-app artwork slot.
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
                putString("queueOrigin", origin.name)
                duration?.split(':')?.mapNotNull(String::toLongOrNull)?.let { parts ->
                    val seconds = parts.fold(0L) { total, part -> total * 60 + part }
                    if (parts.size in 2..3 && seconds > 0) putLong("durationMs", seconds * 1000)
                }
                if (artistCredits.isNotEmpty()) {
                    putStringArrayList("artistCreditNames", ArrayList(artistCredits.map { it.name }))
                    putStringArrayList("artistCreditIds", ArrayList(artistCredits.map { it.id.orEmpty() }))
                }
            })
            .apply {
                // A missing local cover can use the original URL once connectivity returns.
                // The embedded bytes remain available to Media3's notification artwork reader.
                (localCoverUri ?: imageUrl?.toUri())?.let { setArtworkUri(it) }
                embeddedArtBytes?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
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
        cleared = true
        pendingPlay = null
        pendingRetry = null
        connectionTimeout?.cancel()
        playRequestJob?.cancel()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        lastKnownItems = emptyList()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
