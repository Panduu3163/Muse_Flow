package com.example

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.audio.BassBoostAudioProcessor
import com.example.audio.CrossfeedAudioProcessor
import com.example.audio.EqualizerAudioProcessor
import com.example.audio.NormalizerAudioProcessor
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Keeps ExoPlayer + a [MediaSession] alive as a foreground service so playback survives the app
 * being backgrounded or the screen turning off. We never build a notification by hand: the
 * system derives the MediaStyle notification (play/pause/skip, cover art) and the lock
 * screen/status bar/Bluetooth controls straight from the session's player + metadata.
 *
 * Audio focus (pausing for calls, ducking for notification dings) and headset-unplug pause are
 * handled by ExoPlayer itself - see the `handleAudioFocus = true` and
 * `setHandleAudioBecomingNoisy` calls below - not by any code in this class.
 *
 * Tracks arrive from the UI as placeholder [MediaItem]s carrying only a mediaId (an index into
 * [MusicData.tracks]); [PlaybackServiceCallback.onAddMediaItems] resolves each one to a real,
 * playable JioSaavn stream URL just before ExoPlayer needs it. This is Media3's documented lazy
 * playlist pattern, and it keeps all network/decryption work off the UI/controller side.
 */
/** The custom session command backing the lock-screen/notification heart button - see
 * [PlaybackService.PlaybackServiceCallback.onCustomCommand]. */
private const val ACTION_TOGGLE_LIKE = "com.example.TOGGLE_LIKE"

/** Sent by [PlayerViewModel.next] when the queue has no next item to seek to - reuses
 * [PlaybackService.autoplayRelated] (the same taste-blended continuation a queue ending naturally
 * already gets) so pressing skip on the last track behaves the same as letting it play out,
 * instead of silently doing nothing. See [PlaybackService.PlaybackServiceCallback.onCustomCommand].
 * Not `private` - [PlayerViewModel] needs it to build the same command it sends. */
internal const val ACTION_AUTOPLAY_AND_ADVANCE = "com.example.AUTOPLAY_AND_ADVANCE"
internal const val ACTION_FILL_QUEUE = "com.example.FILL_QUEUE"
internal const val ACTION_CLEAR_QUEUE = "com.example.CLEAR_QUEUE"
internal const val ACTION_RESET_PLAYBACK_ERRORS = "com.example.RESET_PLAYBACK_ERRORS"

/** Follow bounded radio pages so a short source list can be prepared up to the queue cap. */
private suspend fun MusicSearchRouter.radioPages(seedId: String, wanted: Int): List<TrackResult> {
    val results = mutableListOf<TrackResult>()
    var continuation: String? = null
    val seenTokens = mutableSetOf<String>()
    repeat(5) {
        val page = getRadioTracksPage(seedId, continuation)
        results += page.items
        if (results.distinctBy { it.id }.size >= wanted) return results.distinctBy { it.id }
        continuation = page.continuation?.takeIf(seenTokens::add) ?: return results.distinctBy { it.id }
    }
    return results.distinctBy { it.id }
}

/** Picks the next item after a playback failure. Offline, network-only entries are skipped in one
 * step so the player never parks on an unresolvable song; online, normal sequential behavior is
 * preserved. The queue does not wrap here because wrapping a fully unavailable queue would loop. */
internal fun nextPlayableQueueIndex(
    currentIndex: Int,
    itemCount: Int,
    online: Boolean,
    schemeAt: (Int) -> String?,
): Int? = ((currentIndex + 1) until itemCount).firstOrNull { index ->
    online || schemeAt(index) in setOf("file", "content", "asset")
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var mediaSessionReleased = false
    private lateinit var resumption: PlaybackResumptionCoordinator
    private var restoredPausedQueue = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val resolver by lazy { TrackStreamResolver(serviceScope, applicationContext) }
    private val equalizerController = EqualizerController()
    private val cachedDataSourceFactory by lazy { buildCachedDataSourceFactory() }
    private var prefetchJob: Job? = null
    private val prefetchCancellation = CacheWarmupCancellation()
    private var fadeJob: Job? = null
    private var fadeEnabled = false
    private var fadeDurationMs = 3000L
    private val historyRepository by lazy { PlaybackHistoryRepository.getInstance(this) }
    private val listeningTracker by lazy { ListeningSessionTracker(
        android.os.SystemClock::elapsedRealtime, System::currentTimeMillis,
        { historyRepository.generation }, historyRepository::recordListeningSession,
    ) }
    private var playbackFailed = false

    private fun currentHistoryTrack(player: Player): Track? {
        val item = player.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val youtube = item.mediaId.looksLikeYouTubeVideoId()
        return Track(title = metadata.title?.toString().orEmpty(), artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(), duration = player.duration.coerceAtLeast(0).asPlaybackTime(),
            plays = "", gradientIndex = 0, imageUrl = metadata.artworkUri?.toString(),
            streamUrl = if (youtube) null else item.localConfiguration?.uri?.toString(),
            sourceType = if (youtube) MusicSource.YOUTUBE_MUSIC else MusicSource.LOCAL_DEVICE,
            sourceId = item.mediaId, artistId = metadata.extras?.getString("artistId"),
            albumId = metadata.extras?.getString("albumId"))
    }

    private fun updatePlaybackFade(player: Player) {
        fadeJob?.cancel()
        if (!fadeEnabled) {
            if (player.volume != 1f) player.volume = 1f
            return
        }
        if (!player.isPlaying) return
        fadeJob = serviceScope.launch {
            while (player.isPlaying) {
                val step = playbackFadeStep(player.currentPosition, player.duration, fadeDurationMs, player.playbackParameters.speed)
                if (player.volume != step.volume) player.volume = step.volume
                delay(step.nextUpdateMs)
            }
        }
    }


    /** Re-subscribed on every track change - drives the lock-screen/notification heart button's
     * filled-vs-outline state (see [updateLikeCustomLayout]). */
    private var likeObserverJob: Job? = null

    // Repeat mode is ALL, so auto-skipping past a failed track (see onPlayerError below) would
    // spin through the whole queue forever if NOTHING can play - overwhelmingly the "no network"
    // case, since every mock-catalog track needs a live JioSaavn search to resolve. This counts
    // consecutive failures so playback gives up after one full lap instead of looping silently.
    private var consecutiveErrorCount = 0
    private var offlineFiles: Map<String, DownloadedTrackEntity> = emptyMap()
    private var historyRecordedForTransition = false
    private var autoplaySuppressed = false
    private var autoplayGeneration = 0
    private var autoplayJob: Job? = null

    private fun recordCurrentPlayback(player: Player) {
        if (!player.isPlaying || player.playbackState != Player.STATE_READY || historyRecordedForTransition) return
        val item = player.currentMediaItem ?: return
        historyRecordedForTransition = true
        val metadata = item.mediaMetadata
        val youtube = item.mediaId.looksLikeYouTubeVideoId()
        PlaybackHistoryRepository.getInstance(this).recordPlayed(Track(
            title = metadata.title?.toString().orEmpty(), artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(), duration = player.duration.coerceAtLeast(0).asPlaybackTime(),
            plays = "", gradientIndex = 0, imageUrl = metadata.artworkUri?.toString(),
            streamUrl = if (youtube) null else item.localConfiguration?.uri?.toString(),
            sourceType = if (youtube) MusicSource.YOUTUBE_MUSIC else MusicSource.LOCAL_DEVICE,
            sourceId = item.mediaId, artistId = metadata.extras?.getString("artistId"),
            albumId = metadata.extras?.getString("albumId"),
        ))
    }

    private fun offlineItem(item: MediaItem): MediaItem? {
        val uri = item.localConfiguration?.uri
        if (uri?.scheme in setOf("content", "asset")) return item
        if (uri?.scheme == "file" && java.io.File(uri.path.orEmpty()).isFile) return item
        val title = item.mediaMetadata.title?.toString().orEmpty()
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        val key = recordingStorageKey(title, artist, item.mediaId, null)
        val stored = offlineFiles[item.mediaId] ?: offlineFiles[key] ?: return null
        if (!canReuseDownloadForPlayback(item.mediaId, uri?.toString(), stored.sourceId, stored.filePath)) return null
        val path = stored.filePath
        if (!java.io.File(path).isFile) return null
        return item.buildUpon().setUri(Uri.fromFile(java.io.File(path))).build()
    }

    /** A track sourced straight from the device's own media store, keyed by its own content://
     * URI as mediaId (see [PlayerViewModel.toMediaItem]) - never eligible for an autoplay
     * continuation, since there's nothing "more like this" to fetch for it and every other item
     * in an on-device queue is, by construction, also on-device only. */
    private fun isLocalDeviceItem(item: MediaItem?): Boolean =
        item?.mediaId?.startsWith("content://") == true

    private fun recoverQueue(player: ExoPlayer): Boolean {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return false
        val wasRequestedToPlay = player.playWhenReady
        val online = isOnline(this)
        val index = nextRecoverableIndex(player.currentMediaItemIndex,
            nextIndex = { current -> timeline.getNextWindowIndex(current,
                if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode,
                player.shuffleModeEnabled) },
            playable = { candidate -> online || offlineItem(player.getMediaItemAt(candidate)) != null },
        ) ?: return false
        val original = player.getMediaItemAt(index)
        val local = offlineItem(original)
        if (local != null && local != original) player.replaceMediaItem(index, local)
        player.seekTo(index, 0L)
        player.prepare()
        if (wasRequestedToPlay) player.play()
        return true
    }

    /** Mirrors the user's preload preference; read on every track transition. */
    private var preloadEnabled = false

    /** Mirrors the user's "remember queue" preference. */
    private var persistentQueueEnabled = true

    /** Debounces queue writes - see [saveQueue]. */
    private var saveQueueJob: Job? = null

    /**
     * Persists the queue, debounced.
     *
     * Timeline and transition callbacks can fire several times in quick succession (adding items,
     * auto-advancing, a seek settling), and each save is a DataStore write plus JSON encoding.
     * A short delay collapses those bursts into one write.
     */
    private fun saveQueue(player: ExoPlayer) {
        if (!persistentQueueEnabled) return
        saveQueueJob?.cancel()
        saveQueueJob = serviceScope.launch {
            delay(1_000)
            val items = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                val metadata = item.mediaMetadata
                val uri = item.localConfiguration?.uri
                SavedQueueItem(
                    mediaId = item.mediaId,
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    album = metadata.albumTitle?.toString().orEmpty(),
                    artworkUrl = metadata.artworkUri?.toString(),
                    // Persist downloaded file addresses here. On-device content URIs are already
                    // kept as mediaId; online stream URLs must be freshly resolved after restart.
                    localFilePath = uri?.takeIf { it.scheme == "file" }?.toString(),
                    artistId = metadata.extras?.getString("artistId"),
                    albumId = metadata.extras?.getString("albumId"),
                    queueOrigin = metadata.extras?.getString("queueOrigin"),
                    durationMs = if (index == player.currentMediaItemIndex && player.duration > 0)
                        player.duration else metadata.extras?.getLong("durationMs") ?: 0L,
                )
            }
            if (items.isEmpty()) return@launch
            runCatching {
                QueueRepository.getInstance(this@PlaybackService).save(
                    items = items,
                    index = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    shuffle = player.shuffleModeEnabled,
                    repeat = player.repeatMode,
                    order = buildList {
                        val timeline = player.currentTimeline
                        var next = timeline.getFirstWindowIndex(true)
                        while (next != C.INDEX_UNSET && size < timeline.windowCount) {
                            add(next)
                            next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true)
                        }
                    },
                )
            }
        }
    }

    /**
     * Rebuilds the last queue on service creation.
     *
     * Deliberately restores **paused**: an app that starts playing the moment it launches - in the
     * car, on headphones, in a meeting - is a genuinely unpleasant surprise. The queue and position
     * are ready; the user decides when it resumes.
     */
    private suspend fun restoreQueueIfEnabled(player: ExoPlayer) {
        val restoreGeneration = autoplayGeneration
        check(BackupRepository.getInstance(this).recoverPendingSettings()) { "Finish settings recovery in MuseFlow before resuming playback" }
            val settings = runCatching {
                AppSettingsRepository(this@PlaybackService).state.first()
            }.getOrNull()
            if (settings?.persistentQueue != true) return
            if (player.mediaItemCount > 0) return // Something already queued; don't clobber.

            val saved = runCatching {
                QueueRepository.getInstance(this@PlaybackService).load()
            }.getOrNull() ?: return

            val savedDownloads = runCatching {
                DownloadRepository.getInstance(this@PlaybackService).completedDownloads.first()
            }.getOrNull().orEmpty().associateBy { it.key }
            val recentHistory = runCatching {
                PlaybackHistoryRepository.getInstance(this@PlaybackService).observeRecent(500).first()
            }.getOrNull().orEmpty().associateBy { it.key }

            val (savedWindow, savedIndex) = queueWindowAround(saved.items, saved.index)
            val items = savedWindow.map { item ->
                // Same reasoning as PlayerViewModel.toMediaItem(): item.artworkUrl is the original
                // remote thumbnail, not a local path - the locally-saved cover file
                // (DownloadRepository.localCoverFile) is the reliable source, since it works
                // regardless of the download's audio container (most are Opus/WebM, which the
                // embedded-tag fallback can't touch at all). Reuse the same source-recording key
                // as likes/downloads; titles alone cannot distinguish audio/video versions.
                val downloadKey = recordingStorageKey(item.title, item.artist, item.mediaId, null)
                val durationMs = item.durationMs.takeIf { it > 0L }
                    ?: (savedDownloads[downloadKey]?.duration ?: recentHistory[downloadKey]?.duration)
                        ?.split(':')
                        ?.mapNotNull(String::toLongOrNull)?.takeIf { it.size in 2..3 }
                        ?.fold(0L) { total, part -> total * 60 + part }?.times(1_000L)
                    ?: item.localFilePath?.let { path ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(this@PlaybackService, (path).toUri())
                                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                        ?.toLongOrNull()
                                } finally { retriever.release() }
                            }.getOrNull()
                        }
                    }
                // artworkUri (a real file:// URI), not artworkData - this app's own in-app state
                // only ever reads artworkUri, so bytes silently left in-app art blank. See
                // PlayerViewModel.toMediaItem()'s fuller comment on the same fix.
                val localCoverUri = item.localFilePath?.let {
                    DownloadRepository.localCoverFile(this@PlaybackService, downloadKey, item.localFilePath)?.let { file -> Uri.fromFile(file) }
                }
                val embeddedArtBytes = if (localCoverUri == null) {
                    item.localFilePath?.let { PlayerViewModel.readEmbeddedArtwork(it) }
                } else {
                    null
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .setExtras(android.os.Bundle().apply {
                        item.artistId?.let { putString("artistId", it) }
                        item.albumId?.let { putString("albumId", it) }
                        item.queueOrigin?.let { putString("queueOrigin", it) }
                        if (durationMs != null && durationMs > 0) putLong("durationMs", durationMs)
                    })
                    .apply {
                        when {
                            localCoverUri != null -> setArtworkUri(localCoverUri)
                            embeddedArtBytes != null -> setArtworkData(embeddedArtBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            item.localFilePath == null -> item.artworkUrl?.let { setArtworkUri(it.toUri()) }
                        }
                    }
                    .build()
                MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    // Keep On-device identities local, including queues saved by older builds.
                    // Only online recordings need a fresh stream resolution after restart.
                    .setUri(item.restoredPlaybackUri())
                    .setMediaMetadata(metadata)
                    .build()
            }

            // Disk/artwork reads can finish after an explicit new song was selected. A stale
            // paused restore must never replace that newer request or clear its play intent.
            if (restoreGeneration != autoplayGeneration || player.mediaItemCount > 0) return
            restoredPausedQueue = true
            player.playWhenReady = false
            player.setMediaItems(items, savedIndex, saved.positionMs)
            player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                restoredShuffleOrder(saved.order, saved.items.size, saved.index.coerceIn(saved.items.indices) - savedIndex, items.size),
                System.nanoTime(),
            ))
            player.repeatMode = saved.repeat
            player.shuffleModeEnabled = saved.shuffle
            // Metadata restoration stays idle. Only explicit Play may prepare audio or request focus.
    }

    /** Guards against stacking autoplay fetches if STATE_ENDED fires more than once. */
    private var autoplayInFlight = false

    /**
     * Appends tracks similar to whatever just finished, so a finished queue continues rather than
     * stopping dead.
     *
     * Tries YouTube Music's own "radio"/watch-next continuation for the specific track that just
     * finished first - a real recommendation signal (genre/mood/artist already folded into YouTube's
     * own ranking) keyed to that one song, not a generic text search for the artist's *name*. The
     * old approach (searching the artist name as a plain query) is what produced "searched for a
     * Bengali indie band, queue ends, autoplay drifts into an unrelated Hindi track" - a generic
     * name search returns whatever else matches that text, not more of the same vein, and for a
     * thin-catalog/less-common artist that's often noise.
     *
     * On top of that single-track seed, a second radio is blended in from one of the user's own
     * top-played tracks (weighted toward higher play counts, picked fresh each time so it's not the
     * same track every autoplay) - this is what keeps a long autoplay session drifting toward the
     * user's broader taste instead of only ever following wherever the last-played song's radio
     * goes. Needs at least a handful of history entries to kick in; a fresh install with little/no
     * history falls back to the single-seed behavior exactly as before - never regresses on day one.
     *
     * Falls back to the old artist-name search only if every radio attempt above returns nothing
     * (e.g. a non-YouTube source, or the requests failed) - some continuation is still better than
     * none.
     *
     * [autoAdvance] controls whether a successful fetch also jumps playback onto the new items
     * right away (the queue-genuinely-ended case, called from [onPlaybackStateChanged]/the
     * skip-on-last-item custom command) or only appends them behind whatever's still playing
     * (called proactively from [onMediaItemTransition] the moment we land on what is *currently*
     * the last item, so the continuation is already queued up by the time that track ends instead
     * of only starting this multi-second fetch at the exact moment it needs to play - previously a
     * real audible stall every time a queue ran out).
     *
     * Never runs for an on-device queue (see [isLocalDeviceItem]) - there's no "more like this" to
     * fetch for a local file, and every other item in that queue is on-device only by construction.
     */
    private fun autoplayRelated(player: ExoPlayer, autoAdvance: Boolean = true) {
        if (autoplayInFlight || autoplaySuppressed) return
        val finished = player.currentMediaItem ?: return
        if (finished.mediaMetadata.extras?.getString("queueOrigin") in
            setOf(QueueOrigin.OFFLINE_DOWNLOADS.name, QueueOrigin.ON_DEVICE.name)) return
        if (isLocalDeviceItem(finished)) return
        val mediaId = finished.mediaId
        val existingIds = (0 until player.mediaItemCount)
            .map { player.getMediaItemAt(it).mediaId }
            .toSet()
        val requestGeneration = autoplayGeneration

        autoplayInFlight = true
        autoplayJob = serviceScope.launch {
            try {
            val router = MusicSearchRouter(this@PlaybackService)
            val desired = if (player.mediaItemCount < MAX_QUEUE_ITEMS) {
                MAX_QUEUE_ITEMS - player.mediaItemCount
            } else 10
            val primaryResults = if (mediaId.looksLikeYouTubeVideoId()) {
                runCatching { router.radioPages(mediaId, desired) }.getOrNull().orEmpty()
            } else {
                emptyList()
            }

            // Taste-blend seed: a weighted-random pick from top-played history, excluding whatever
            // just finished so it can't just re-seed the same radio twice in a row.
            val topPlayed = runCatching {
                PlaybackHistoryRepository.getInstance(this@PlaybackService).observeTopPlayed(15).first()
            }.getOrNull().orEmpty()
            val tasteSeed = topPlayed.pickTasteSeed(exclude = setOf(mediaId))
            val tasteResults = tasteSeed?.sourceId?.let { seedId ->
                runCatching { router.radioPages(seedId, desired / 2 + 8) }.getOrNull().orEmpty()
            }.orEmpty()

            val candidates = if (primaryResults.isEmpty() && tasteResults.isEmpty()) {
                val artist = finished.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
                if (artist == null) emptyList() else runCatching { router.searchDiscoveryTracks(artist) }.getOrNull().orEmpty()
            } else {
                interleaveTwoToOne(primaryResults, tasteResults)
            }
            val listening = historyRepository.observeListeningSessions().first()
            val now = System.currentTimeMillis()
            val related = rankDiscoveryCandidates(
                candidates, desired, excludedIds = existingIds,
                familiarArtists = topPlayed.map { it.artist }.toSet(),
                recentIds = PlaybackHistoryRepository.getInstance(this@PlaybackService)
                    .observeRecent(30).first().mapNotNull { it.sourceId }.toSet(),
                learnedPreferences = learnedRecordingPreferences(listening, now),
                learnedArtists = learnedArtistPreferences(listening, now),
            )

            if (requestGeneration == autoplayGeneration &&
                !autoplaySuppressed && player.currentMediaItem?.mediaId == mediaId) {
                val originalItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                // Only an autoplay continuation at the end may retire old, already-played
                // entries to make room. Choosing a song in the middle never discards them.
                if (player.mediaItemCount >= MAX_QUEUE_ITEMS &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1) {
                    repeat(minOf(related.size, player.mediaItemCount - 1)) {
                        player.removeMediaItem(0)
                    }
                }
                val accepted = related.take((MAX_QUEUE_ITEMS - player.mediaItemCount).coerceAtLeast(0))
                if (accepted.isNotEmpty()) player.addMediaItems(
                    accepted.map { track ->
                        MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(youTubeResolvePlaceholderUri(track.id))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setAlbumTitle(track.source)
                                    .setExtras(android.os.Bundle().apply {
                                        track.artistId?.let { putString("artistId", it) }
                                        track.albumId?.let { putString("albumId", it) }
                                        putString("queueOrigin", finished.mediaMetadata.extras
                                            ?.getString("queueOrigin") ?: QueueOrigin.ONLINE.name)
                                        track.duration?.split(':')?.mapNotNull(String::toLongOrNull)
                                            ?.takeIf { it.size in 2..3 }?.let { parts ->
                                                val seconds = parts.fold(0L) { total, part -> total * 60 + part }
                                                if (seconds > 0) putLong("durationMs", seconds * 1000)
                                            }
                                        if (track.artistCredits.isNotEmpty()) {
                                            putStringArrayList("artistCreditNames", ArrayList(track.artistCredits.map { it.name }))
                                            putStringArrayList("artistCreditIds", ArrayList(track.artistCredits.map { it.id.orEmpty() }))
                                        }
                                    })
                                    .apply { track.imageUrl?.let { setArtworkUri(it.toUri()) } }
                                    .build()
                            )
                            .build()
                    }
                )
                // Collection queues may recycle eligible source songs as their final fallback.
                // Search radio keeps only genuine candidates, never 50 copies of its seed.
                if (player.mediaItemCount < MAX_QUEUE_ITEMS && originalItems.isNotEmpty() &&
                    canRecycleQueueToFill(finished.mediaMetadata.extras?.getString("queueOrigin"))) {
                    val source = originalItems.filter { it.mediaId != mediaId } + finished
                    val copies = List(MAX_QUEUE_ITEMS - player.mediaItemCount) { index ->
                        source[index % source.size]
                    }
                    player.addMediaItems(copies)
                }
                if (autoAdvance) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
            } finally {
                if (requestGeneration == autoplayGeneration) autoplayInFlight = false
            }
        }
    }

    /** Native playlist preloading prepares samples and follows Media3's real successor order. */
    private fun updatePlaylistPreloading(player: ExoPlayer) {
        val configuration = playlistPreloadConfiguration(preloadEnabled, player.playWhenReady, player.playbackState)
        if (player.preloadConfiguration.targetPreloadDurationUs != configuration.targetPreloadDurationUs)
            player.preloadConfiguration = configuration
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            // Our own DSP equalizer lives in ExoPlayer's audio pipeline rather than as a platform
            // AudioEffect, because many devices (Xiaomi among them) refuse to insert a system
            // effect at all - see EqualizerAudioProcessor's doc.
            .setRenderersFactory(
                object : DefaultRenderersFactory(this) {
                    override fun buildAudioSink(
                        context: android.content.Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean,
                    ): AudioSink = DefaultAudioSink.Builder(context)
                        .setAudioProcessorChain(
                            DefaultAudioSink.DefaultAudioProcessorChain(
                                EqualizerAudioProcessor.INSTANCE,
                                BassBoostAudioProcessor.INSTANCE,
                                NormalizerAudioProcessor.INSTANCE,
                                CrossfeedAudioProcessor.INSTANCE
                            )
                        )
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                        .build()
                }
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            // OFF, not ALL. REPEAT_MODE_ALL was a leftover from the mock-catalog era, and with
            // real queues it means a one-track queue loops that track forever - the user reaches
            // the end of a song and hears it start again. Repeat is now something the user turns
            // on from Now Playing; when a queue genuinely ends, autoplay takes over instead.
            .apply { repeatMode = Player.REPEAT_MODE_OFF }

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (!mediaSessionReleased) {
                    PlaybackControls.publish(this@PlaybackService, player)
                    publishCodecInfo(player as ExoPlayer)
                }
                if (events.containsAny(Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED))
                    saveQueue(player as ExoPlayer)
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) saveQueue(player as ExoPlayer)
                if (events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED))
                    updatePlaylistPreloading(player as ExoPlayer)
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED)) updatePlaybackFade(player)
            }

            // A track that failed to resolve/stream (dead CDN link, blank search results, no
            // network, ...) has no valid source and would otherwise freeze the queue; skip
            // straight past it instead - but only until every item in the queue has had a turn,
            // so a fully offline queue fails fast (surfacing a real error - see PlayerViewModel)
            // rather than spinning through all ten mock-catalog tracks forever.
            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
                player.currentMediaItem?.mediaId?.takeIf { it.looksLikeYouTubeVideoId() }
                    ?.let(StreamResolverRouter::invalidate)
                listeningTracker.finish(ListeningOutcome.ERROR)
                consecutiveErrorCount++
                if (consecutiveErrorCount >= player.mediaItemCount.coerceAtLeast(1) || !recoverQueue(player)) {
                    // Keep the queue visible for a later retry, but leave the player in a clear,
                    // non-buffering state instead of appearing permanently stuck on the bad item.
                    player.playWhenReady = false
                    player.stop()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) currentHistoryTrack(player)?.let { listeningTracker.ensureStarted(it, player.duration) }
                listeningTracker.playingChanged(isPlaying, player.duration)
                if (isPlaying) {
                    consecutiveErrorCount = 0
                    SleepTimer.onPlaybackResumed()
                }
                recordCurrentPlayback(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) saveQueue(player)
                if (playWhenReady && restoredPausedQueue) {
                    restoredPausedQueue = false
                    player.currentMediaItem?.let(::prefetchFullTrack)
                    if (!player.hasNextMediaItem() && isOnline(this@PlaybackService) && !autoplaySuppressed)
                        autoplayRelated(player, autoAdvance = false)
                }
            }

            // Queue exhausted: keep the music going with tracks related to what just finished,
            // rather than falling silent. Only fires at a real end - with repeat on, ExoPlayer
            // wraps before this is ever reached.
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listeningTracker.finish(ListeningOutcome.ENDED)
                recordCurrentPlayback(player)
                if (playbackState == Player.STATE_ENDED && !autoplaySuppressed && isOnline(this@PlaybackService)) {
                    autoplayRelated(player)
                }
            }

            // Warm the next track's stream URL while the current one is still playing, so a skip
            // (or a natural track end) doesn't stall on the several-second resolve pipeline.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Reset before the offline early return too: selecting an unavailable song
                // while paused must not inherit the previous song's already-recorded flag.
                historyRecordedForTransition = false
                listeningTracker.finish(when {
                    playbackFailed -> ListeningOutcome.ERROR
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> ListeningOutcome.ENDED
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> ListeningOutcome.SKIPPED
                    else -> ListeningOutcome.INTERRUPTED
                })
                playbackFailed = false
                if (restoredPausedQueue && !player.playWhenReady) {
                    saveQueue(player)
                    observeLikeStateForCurrentTrack(player)
                    return
                }
                currentHistoryTrack(player)?.let { listeningTracker.start(it, player.duration, player.isPlaying) }
                player.setWakeMode(if (playbackNeedsNetworkWake(mediaItem?.localConfiguration?.uri?.scheme)) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
                // Queried once and reused below - a live ConnectivityManager lookup, not a field
                // read, so there's no reason to pay for it twice in the same transition.
                val online = isOnline(this@PlaybackService)
                // Offline, and this item isn't actually sitting on disk (on-device, or a
                // completed download): a network placeholder can still have a *prefix* of bytes
                // in StreamCache left over from an earlier prefetch, so CacheDataSource would
                // serve those first and only fail once it runs out mid-track - starting to play a
                // song that's about to cut out partway through. Jump straight to the next
                // genuinely downloaded item instead, or stop cleanly if none remain, rather than
                // letting it start at all. Connectivity returning never issues Play: the listener
                // decides when sound resumes.
                if (mediaItem != null && !online && offlineItem(mediaItem) == null) {
                    if (!player.playWhenReady) {
                        saveQueue(player)
                        observeLikeStateForCurrentTrack(player)
                        return
                    }
                    if (!recoverQueue(player)) {
                        player.playWhenReady = false
                        player.stop()
                    }
                    return
                }
                recordCurrentPlayback(player)
                saveQueue(player)
                mediaItem?.let(::prefetchFullTrack)
                observeLikeStateForCurrentTrack(player)
                // We've just landed on what is, right now, the last item in the queue - top up a
                // taste-blended continuation in the background while it plays, rather than only
                // starting that fetch once it actually ends (see autoplayRelated's own doc). Covers
                // a queue that only ever had one/a few items to begin with (a fresh Home/Search
                // play) exactly the same way it covers one that's simply run down to its last track.
                if (!player.hasNextMediaItem() && online && !autoplaySuppressed) {
                    autoplayRelated(player, autoAdvance = false)
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                // The service is the final gate for additions from every controller and
                // autoplay, including two near-simultaneous add actions from the UI.
                while (player.mediaItemCount > MAX_QUEUE_ITEMS) {
                    player.removeMediaItem(queueOverflowRemovalIndex(
                        player.mediaItemCount, player.currentMediaItemIndex))
                }
                saveQueue(player)
            }
        })

        serviceScope.launch {
            DownloadRepository.getInstance(this@PlaybackService).completedDownloads.collect { downloads ->
                offlineFiles = downloads.flatMap { entry ->
                    listOfNotNull(entry.key to entry, entry.sourceId?.let { it to entry })
                }.toMap()
                // Queues assembled before a download finished must also prefer its local file.
                for (index in 0 until player.mediaItemCount) {
                    val original = player.getMediaItemAt(index)
                    val local = offlineItem(original)
                    if (local == null || local == original) continue
                    // Swapping the *currently playing* item's source (network placeholder -> a
                    // real local file:// URI) makes ExoPlayer treat it as different media and
                    // re-prepare from position 0 - so a download finishing while its own song was
                    // playing silently restarted it from the beginning. Capture position/playback
                    // state first and restore them right after the swap for that one item.
                    val isCurrent = index == player.currentMediaItemIndex
                    val resumePositionMs = if (isCurrent) player.currentPosition else 0L
                    val wasPlaying = isCurrent && player.isPlaying
                    player.replaceMediaItem(index, local)
                    if (isCurrent) {
                        player.seekTo(index, resumePositionMs)
                        if (wasPlaying) player.play()
                    }
                }
            }
        }

        // Which processors are *active* is decided once, by Media3's AudioProcessingPipeline, at
        // the moment it configures itself against the current track's audio format - not
        // re-checked on every buffer. So flipping `enabled`/intensity on an already-running
        // processor (Normalizer/BassBoost/Crossfeed here, the equalizer below) changes the value
        // the processor holds, but the pipeline already decided whether that processor is in the
        // chain at all and never revisits that decision until it configures again - which is why
        // none of these audibly did anything without a track change. A same-position seek is the
        // lightweight way to force that reconfigure: it flushes and re-negotiates the renderer's
        // audio pipeline against the *current* buffered media (no network refetch, no real skip),
        // which is exactly the "revisit isActive() now" trigger Media3 doesn't otherwise expose.
        var previousAudioFxSettings: Triple<Boolean, Pair<Boolean, Int>, Pair<Boolean, Int>>? = null
        serviceScope.launch {
            AppSettingsRepository(this@PlaybackService).state.collect { settings ->
                if (fadeEnabled != settings.crossfadeEnabled || fadeDurationMs != settings.crossfadeDurationMs.toLong()) {
                    fadeEnabled = settings.crossfadeEnabled
                    fadeDurationMs = settings.crossfadeDurationMs.toLong()
                    updatePlaybackFade(player)
                }

                player.skipSilenceEnabled = settings.skipSilence
                preloadEnabled = settings.preloadNextTrack
                updatePlaylistPreloading(player)
                persistentQueueEnabled = settings.persistentQueue
                NormalizerAudioProcessor.INSTANCE.setEnabled(settings.audioNormalizationEnabled)
                BassBoostAudioProcessor.INSTANCE.setIntensity(settings.bassBoostEnabled, settings.bassBoostIntensity)
                CrossfeedAudioProcessor.INSTANCE.setIntensity(settings.crossfeedEnabled, settings.crossfeedIntensity)

                val current = Triple(
                    settings.audioNormalizationEnabled,
                    settings.bassBoostEnabled to settings.bassBoostIntensity,
                    settings.crossfeedEnabled to settings.crossfeedIntensity,
                )
                // Skip the very first emission (startup's initial state, nothing to reconfigure
                // against yet) and skip entirely when nothing audio-relevant actually changed, so
                // unrelated settings changes (theme, density, ...) can't trigger a spurious seek.
                if (previousAudioFxSettings != null && previousAudioFxSettings != current && player.mediaItemCount > 0) {
                    player.seekTo(player.currentPosition)
                }
                previousAudioFxSettings = current
            }
        }

        ListenTogetherRepository.getInstance(this).attach(player)
        resumption = PlaybackResumptionCoordinator(serviceScope, { restoreQueueIfEnabled(player) },
            { autoplayGeneration.toLong() }, { currentResumeSnapshot(player) })
        resumption.start()

        // Equalizer must be attached to ExoPlayer's actual audio session id, and re-attached if
        // that id ever changes (it can, e.g. across some route changes) - an Equalizer instance
        // is bound to one specific session for its whole lifetime, it can't just be redirected.
        equalizerController.attach(player.audioSessionId)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                equalizerController.attach(audioSessionId)
                serviceScope.launch {
                    equalizerController.apply(EqualizerRepository.getInstance(this@PlaybackService).settings.first())
                }
            }

            // Drives the optional codec-info line under Now Playing's timeline - see
            // [CurrentCodecInfo]'s doc for why this is a standalone singleton rather than a real
            // NowPlayingState field.
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
            ) {
                publishCodecInfo(player)
            }
        })
        // Re-applies live whenever the user changes a setting on EqualizerSettingsScreen - this
        // service never needs a direct reference to that screen (or vice versa), same
        // Flow-driven decoupling the rest of this app's settings already use.
        var previousEqSettings: Pair<Boolean, List<Int>>? = null
        serviceScope.launch {
            EqualizerRepository.getInstance(this@PlaybackService).settings.collect { settings ->
                // Platform effect first (a silent no-op on devices that refuse it), then our own
                // DSP processor, which works everywhere.
                equalizerController.apply(settings)
                EqualizerAudioProcessor.INSTANCE.setGains(
                    enabled = settings.enabled,
                    gains = settings.bandLevelsMillibel.map(EqualizerAudioProcessor::millibelToDb),
                )

                // Same reconfigure-forcing seek as the Normalizer/BassBoost/Crossfeed block above
                // - the DSP processor's isActive() flip otherwise never reaches an already-running
                // pipeline. The platform effect (equalizerController.apply above) doesn't need
                // this: it's a real AudioFlinger effect, not a Media3 AudioProcessor baked into the
                // pipeline at configure time.
                val current = settings.enabled to settings.bandLevelsMillibel
                if (previousEqSettings != null && previousEqSettings != current && player.mediaItemCount > 0) {
                    player.seekTo(player.currentPosition)
                }
                previousEqSettings = current
            }
        }

        // Resolved via the package manager rather than a hard `MainActivity::class.java`
        // reference, so this service stays decoupled from whatever the UI layer's entry-point
        // Activity happens to be called - tapping the media notification opens the app's declared
        // launcher activity either way.
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackServiceCallback())
            .apply { openAppIntent?.let { setSessionActivity(it) } }
            .build()
        PlaybackControls.attach(this, mediaSession!!.token, player, serviceScope)
        serviceScope.launch {
            while (true) {
                delay(15_000)
                if (player.isPlaying) saveQueue(player)
            }
        }

        // Seeds the lock-screen/notification heart button's initial state - onMediaItemTransition
        // (which also drives this) only fires on an actual transition, not for whatever the
        // restored queue's starting track already was.
        observeLikeStateForCurrentTrack(player)
    }

    /** WebM often omits a declared bitrate. Use full encoded length/duration only as a marked
     * estimate for audio-only cached streams. A partial cache's byte count is never used. */
    private fun publishCodecInfo(player: ExoPlayer) {
        val format = player.audioFormat
        if (format == null || player.playbackState == Player.STATE_IDLE || player.playerError != null) {
            CurrentCodecInfo.clear()
            return
        }
        val uri = player.currentMediaItem?.localConfiguration?.uri
        val audioOnly = player.currentTracks.groups.none { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
        val length = if (uri != null && youTubeVideoIdFromResolvePlaceholder(uri) != null && audioOnly) {
            runCatching {
                androidx.media3.datasource.cache.ContentMetadata.getContentLength(
                    StreamCache.get(this).getContentMetadata(uri.toString()))
            }.getOrDefault(-1L)
        } else -1L
        CurrentCodecInfo.update(format.sampleMimeType, format.bitrate, averageEncodedBitrate(length, player.duration))
    }

    /**
     * A [DataSource.Factory] that intercepts every HTTP (re)open for a
     * [youTubeResolvePlaceholderUri] placeholder and swaps in a real stream URL via
     * [YouTubeStreamResolver] - which may itself serve a still-live cached resolution rather than
     * re-running the full pipeline (see its class doc), but always returns a URL that's actually
     * valid right now either way. This is deliberately NOT done in
     * [PlaybackServiceCallback.onAddMediaItems]/[resolveMediaItem]
     * (Media3's "resolve once when items are added to the queue" hook): that would resolve every
     * track in a whole queue up front, at add time - stale within minutes for the ones the user
     * hasn't reached yet by the time they do (skip-to-next/previous, or just leaving a track
     * paused for a while). [ResolvingDataSource] instead resolves at actual HTTP-open time, which
     * happens exactly when a track is about to play (or resume after being idle long enough for
     * the OS/CDN to drop the connection) - covering every case the freshness requirement names.
     *
     * Non-YouTube URLs (JioSaavn/NetEase search results, already fully resolved; local
     * downloaded files, handled separately by [DefaultDataSource]'s own file:// dispatch) pass
     * through completely unchanged.
     */
    private fun buildResolvingDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        val resolvingHttpFactory = ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
            val videoId = youTubeVideoIdFromResolvePlaceholder(dataSpec.uri)
                ?: return@Factory dataSpec
            if (!isOnline(this@PlaybackService)) {
                throw IOException("Device is offline and this track has not been downloaded")
            }
            // Blocks this load's IO thread (not the playback/main thread) for the cold-start
            // BotGuard+cipher round trip - the same runBlocking-at-an-IO-boundary pattern
            // DownloadRepository already uses elsewhere in this app.
            val resolved = runBlocking { StreamResolverRouter.resolve(this@PlaybackService, videoId) }
                ?: throw IOException("Could not resolve a playable stream for YouTube video $videoId")

            // The User-Agent must match the client that resolved this URL, or YouTube's CDN
            // answers 403. Set per-request via the DataSpec (not on the factory) because the
            // winning client - and therefore the User-Agent - varies per track.
            val withUri = dataSpec.withUri(resolved.url.toUri())
            resolved.userAgent
                ?.let { withUri.withRequestHeaders(mapOf("User-Agent" to it)) }
                ?: withUri
        }
        return DefaultDataSource.Factory(this, resolvingHttpFactory)
    }

    /**
     * Wraps [buildResolvingDataSourceFactory] in [StreamCache]'s disk cache - a seek within
     * already-downloaded bytes (behind *or* ahead of playback, once [prefetchFullTrack] has run)
     * reads from disk instead of re-requesting over the network. `CacheDataSource` sees the
     * pre-resolve placeholder URI (it wraps the resolving factory as its upstream, so the upstream
     * only runs on a genuine cache miss), which is what makes this cache-friendly at all - the
     * resolved CDN URL itself carries a short-lived signed token that would make every resolve a
     * different, never-reused cache key.
     */
    private fun buildCachedDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(StreamCache.get(this))
            .setUpstreamDataSourceFactory(buildResolvingDataSourceFactory())
            // A failed cache write (disk full, etc.) shouldn't take playback down with it -
            // fall through to the uncached upstream read instead.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Downloads the rest of the current track into [StreamCache] in the background while it
     * plays, so a seek far ahead of the natural playback position - not just behind it - is
     * already on disk by the time the user gets there. Cancelled and restarted on every track
     * change; an abandoned prefetch for a track nobody's listening to anymore isn't worth the
     * bandwidth or disk space.
     *
     * Reuses [cachedDataSourceFactory] rather than re-resolving or re-deriving a cache key by
     * hand: opening it with the placeholder URI runs the exact same cache-check -> resolve-on-miss
     * -> write-under-the-stable-key path normal playback already takes, just triggered eagerly
     * from a background thread instead of by ExoPlayer's own read-ahead.
     */
    private fun prefetchFullTrack(mediaItem: MediaItem) {
        val generation = prefetchCancellation.replace()
        prefetchJob?.cancel()
        val uri = mediaItem.localConfiguration?.uri ?: return
        if (youTubeVideoIdFromResolvePlaceholder(uri) == null) return
        prefetchJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val dataSource = cachedDataSourceFactory.createDataSource() as CacheDataSource
                val writer = CacheWriter(dataSource, DataSpec(uri), null, null)
                try {
                    if (!prefetchCancellation.register(generation, writer::cancel)) return@runCatching
                    writer.cache()
                } finally {
                    prefetchCancellation.finished(generation)
                    dataSource.close()
                }
            }
        }
    }

    private inner class PlaybackServiceCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (!controller.isTrusted) return Futures.immediateFailedFuture(SecurityException("Untrusted resumption controller"))
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val job = serviceScope.launch {
                try {
                    val generation = autoplayGeneration
                    val snapshot = withTimeout(3_000) {
                        val restored = resumption.snapshot()
                            ?: throw IllegalStateException("No remembered queue. Open MuseFlow and choose a song.")
                        if (!isForPlayback) restored else validatedResumeSnapshot(restored) { uri ->
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    when (uri.scheme) {
                                        "file" -> java.io.File(uri.path.orEmpty()).let { it.isFile && it.canRead() && it.length() > 0 }
                                        "content" -> contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                                        else -> false
                                    }
                                }.getOrDefault(false)
                            }
                        }
                    }
                    check(generation == autoplayGeneration && !mediaSessionReleased) { "Playback request was replaced" }
                    future.set(MediaSession.MediaItemsWithStartPosition(snapshot.items, snapshot.index, snapshot.positionMs))
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    if (!mediaSession.player.playWhenReady) stopSelf()
                    throw cancelled
                } catch (error: Exception) {
                    future.setException(error)
                    PlaybackControls.showError(this@PlaybackService, error.message.orEmpty())
                    if (!mediaSession.player.playWhenReady) stopSelf()
                }
            }
            future.addListener({ if (future.isCancelled) job.cancel() }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Replacing a search/list is a new queue session even when its first mediaId is
            // unchanged. An older radio request must not append its results into this session.
            autoplayGeneration++
            restoredPausedQueue = false
            autoplayJob?.cancel()
            autoplayInFlight = false
            return Futures.transform(
                onAddMediaItems(mediaSession, controller, mediaItems),
                { resolved -> MediaSession.MediaItemsWithStartPosition(resolved.orEmpty(), startIndex, startPositionMs) },
                com.google.common.util.concurrent.MoreExecutors.directExecutor(),
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // A fresh setMediaItems call is a new playback attempt - don't carry over the
            // failure count from whatever was playing (or failing) before.
            consecutiveErrorCount = 0
            autoplaySuppressed = false
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                future.set(mediaItems.map { resolveMediaItem(it) })
            }
            return future
        }

        // Declares the "toggle like" custom command available to every controller (the system's
        // notification/lock-screen controller included) - without this, MediaSession.setCustomLayout
        // below would have nothing to attach the button to.
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            // Media3's deprecated onConnect default is now an empty sentinel. Copying its
            // commands into accept() removes PLAY, PREPARE and CHANGE_MEDIA_ITEMS entirely.
            val base = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            if (!controller.isTrusted) return Futures.immediateFuture(base)
            return Futures.immediateFuture(MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(base.availableSessionCommands.buildUpon()
                    .add(SessionCommand(ACTION_TOGGLE_LIKE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_AUTOPLAY_AND_ADVANCE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_FILL_QUEUE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_CLEAR_QUEUE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_RESET_PLAYBACK_ERRORS, android.os.Bundle.EMPTY))
                    .build())
                .build())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                val player = mediaSession?.player ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                val track = currentTrackFromPlayer(player)
                if (track != null) {
                    serviceScope.launch {
                        val repository = LikedSongsRepository.getInstance(this@PlaybackService)
                        val isLiked = repository.observeIsLiked(track).first()
                        if (isLiked) repository.unlike(track) else repository.like(track)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_FILL_QUEUE) {
                val player = mediaSession?.player as? ExoPlayer
                if (player != null && player.mediaItemCount in 1 until MAX_QUEUE_ITEMS &&
                    isOnline(this@PlaybackService)) autoplayRelated(player, autoAdvance = false)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_AUTOPLAY_AND_ADVANCE) {
                val player = mediaSession?.player as? ExoPlayer
                // A queue can gain a next item between the controller's check and this command
                // landing (e.g. autoplay already fired from STATE_ENDED); just advance normally
                // rather than fetching a second batch on top.
                if (player != null) {
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.play()
                    } else if (isOnline(this@PlaybackService)) {
                        autoplaySuppressed = false // explicit Next requests a new continuation
                        autoplayRelated(player)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_CLEAR_QUEUE) {
                val player = mediaSession?.player as? ExoPlayer
                if (player != null && player.currentMediaItemIndex in 0 until player.mediaItemCount) {
                    autoplaySuppressed = true
                    autoplayGeneration++
                    autoplayJob?.cancel()
                    autoplayInFlight = false
                    indicesExceptCurrent(player.mediaItemCount, player.currentMediaItemIndex)
                        .forEach(player::removeMediaItem)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_RESET_PLAYBACK_ERRORS) {
                // A tap on Retry begins a fresh attempt; failures from the previous offline
                // attempt must not make the next error exhaust the queue prematurely.
                consecutiveErrorCount = 0
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /** Builds enough of a [Track] from the player's own current metadata to like/unlike it - the
     * lock-screen/notification button has no [TrackResult] to work from, only whatever
     * [MediaMetadata] the now-playing item already carries. */
    private fun currentTrackFromPlayer(player: Player): Track? {
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString() ?: return null
        val artist = metadata.artist?.toString().orEmpty()
        val mediaId = player.currentMediaItem?.mediaId
        val isRealVideoId = mediaId?.looksLikeYouTubeVideoId() == true
        return Track(
            title = title,
            artist = artist,
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = "",
            plays = "",
            gradientIndex = 0,
            imageUrl = metadata.artworkUri?.toString(),
            sourceType = when {
                mediaId?.startsWith("content://") == true || mediaId?.startsWith("file://") == true -> MusicSource.LOCAL_DEVICE
                isRealVideoId -> MusicSource.YOUTUBE_MUSIC
                else -> null
            },
            sourceId = mediaId,
            artistId = metadata.extras?.getString("artistId"),
        )
    }

    /** Re-subscribes to the current track's liked state on every track change, keeping the
     * lock-screen/notification heart button's filled-vs-outline icon in sync - including when the
     * like itself happens from elsewhere in the app (Now Playing's own heart button), not just
     * from this custom command. */
    private fun observeLikeStateForCurrentTrack(player: ExoPlayer) {
        likeObserverJob?.cancel()
        val track = currentTrackFromPlayer(player)
        if (track == null) {
            updateLikeCustomLayout(isLiked = false)
            return
        }
        likeObserverJob = serviceScope.launch {
            LikedSongsRepository.getInstance(this@PlaybackService).observeIsLiked(track).collect { isLiked ->
                updateLikeCustomLayout(isLiked)
            }
        }
    }

    private fun updateLikeCustomLayout(isLiked: Boolean) {
        if (mediaSessionReleased) return
        // The built-in ICON_HEART_FILLED/ICON_HEART_UNFILLED constants let surfaces that know how
        // to render them (system media notification, Android Auto, ...) draw a properly themed/
        // tinted heart; setCustomIconResId is the fallback for anything that doesn't.
        val button = CommandButton.Builder(if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName(if (isLiked) "Unlike" else "Like")
            .setCustomIconResId(if (isLiked) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart_outline)
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_LIKE, android.os.Bundle.EMPTY))
            .build()
        mediaSession?.setCustomLayout(listOf(button))
    }

    /**
     * Looks up the placeholder's mediaId in the catalog and fills in a real, playable URI.
     *
     * `onAddMediaItems` fires for every `setMediaItems` call, not just URI-less placeholders - so
     * an item that already has a real URI (a search result or a downloaded track, both of which
     * reuse mediaId as a plain queue-position index, e.g. "3") must be left alone here. Without
     * this check, a search result whose position happens to coincide with a valid
     * [MusicData.tracks] index would have its correct stream URL silently overwritten with
     * whatever that unrelated mock-catalog track resolves to.
     *
     * Every item returned from here MUST end up with a URI: ExoPlayer crashes internally (a
     * `NullPointerException` inside `DefaultMediaSourceFactory.createMediaSource`) if handed a
     * "resolved" item that still has none - which is exactly what a naive lazy-resolution
     * failure (dead search results, or simply no network) would otherwise produce. Falling back
     * to an `.invalid` URI (an IANA-reserved TLD, RFC 2606, guaranteed to fail DNS resolution)
     * turns that into an ordinary [PlaybackException] through ExoPlayer's normal network error
     * handling instead - which [onPlayerError] above already knows how to skip past.
     */
    private suspend fun resolveMediaItem(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val index = item.mediaId.toIntOrNull()
        val track = index?.let { MusicData.tracks.getOrNull(it) }
        val resolved = track?.let { resolver.resolve(it) }
        val streamUrl = resolved?.directStreamUrl

        if (track == null || streamUrl == null) {
            return item.buildUpon().setUri("https://unresolved.invalid/${item.mediaId}").build()
        }

        val metadata = item.mediaMetadata.buildUpon()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply { resolved.imageUrl?.let { setArtworkUri(it.toUri()) } }
            .build()

        return item.buildUpon()
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession.takeUnless { mediaSessionReleased }

    private fun currentResumeSnapshot(player: Player): PlaybackResumeSnapshot? {
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        if (items.isEmpty()) return null
        return PlaybackResumeSnapshot(items, player.currentMediaItemIndex.coerceIn(items.indices), player.currentPosition.coerceAtLeast(0))
    }

    // Swiping the app away from Recents stops playback entirely, by explicit product choice here
    // (not every media app's default - some keep playing in the background) - so there's never a
    // stray notification/audio still running with no app UI behind it.
    override fun onTaskRemoved(rootIntent: Intent?) {
        CurrentCodecInfo.clear()
        autoplayGeneration++
        autoplayJob?.cancel()
        if (::resumption.isInitialized) resumption.invalidate()
        mediaSession?.player?.stop()
        mediaSessionReleased = true
        PlaybackControls.detach(this)
        mediaSession?.release()
        stopSelf()
    }

    override fun onDestroy() {
        CurrentCodecInfo.clear()
        PlaybackControls.detach(this, preserveMessage = true)
        if (::resumption.isInitialized) resumption.invalidate()
        prefetchCancellation.replace()
        listeningTracker.finish(ListeningOutcome.INTERRUPTED)
        ListenTogetherRepository.getInstance(this).detach()
        equalizerController.release()
        mediaSession?.let { session ->
            session.player.release()
            if (!mediaSessionReleased) session.release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
