package com.example

import android.app.PendingIntent
import android.content.Intent
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val resolver by lazy { TrackStreamResolver(serviceScope, applicationContext) }
    private val equalizerController = EqualizerController()
    private val cachedDataSourceFactory by lazy { buildCachedDataSourceFactory() }
    private var prefetchJob: Job? = null

    /** Re-subscribed on every track change - drives the lock-screen/notification heart button's
     * filled-vs-outline state (see [updateLikeCustomLayout]). */
    private var likeObserverJob: Job? = null

    // Repeat mode is ALL, so auto-skipping past a failed track (see onPlayerError below) would
    // spin through the whole queue forever if NOTHING can play - overwhelmingly the "no network"
    // case, since every mock-catalog track needs a live JioSaavn search to resolve. This counts
    // consecutive failures so playback gives up after one full lap instead of looping silently.
    private var consecutiveErrorCount = 0
    private var offlineFiles: Map<String, String> = emptyMap()
    private var historyRecordedForTransition = false

    /** Set when a natural queue advance was blocked because we're offline and nothing downloaded
     * remained ahead (see the offline guard in [onMediaItemTransition]) - cleared, and playback
     * resumed from where it stopped, the moment connectivity returns (see the [observeOnline]
     * collector in [onCreate]). */
    private var stoppedForOffline = false

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
        val key = "${title.trim().lowercase()}::${artist.trim().lowercase()}"
        val path = offlineFiles[item.mediaId] ?: offlineFiles[key] ?: return null
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
        player.play()
        return true
    }

    /** Mirrors the user's preload preference; read on every track transition. */
    private var preloadEnabled = true

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
                    // Only a real file survives a restart; an online URL would be expired by then.
                    localFilePath = uri?.takeIf { it.scheme == "file" }?.toString(),
                    artistId = metadata.extras?.getString("artistId"),
                )
            }
            if (items.isEmpty()) return@launch
            runCatching {
                QueueRepository.getInstance(this@PlaybackService).save(
                    items = items,
                    index = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
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
    private fun restoreQueueIfEnabled(player: ExoPlayer) {
        serviceScope.launch {
            val settings = runCatching {
                AppSettingsRepository(this@PlaybackService).state.first()
            }.getOrNull()
            if (settings?.persistentQueue != true) return@launch
            if (player.mediaItemCount > 0) return@launch // Something already queued; don't clobber.

            val saved = runCatching {
                QueueRepository.getInstance(this@PlaybackService).load()
            }.getOrNull() ?: return@launch

            val items = saved.items.map { item ->
                // Same reasoning as PlayerViewModel.toMediaItem(): item.artworkUrl is the original
                // remote thumbnail, not a local path - the locally-saved cover file
                // (DownloadRepository.localCoverFile) is the reliable source, since it works
                // regardless of the download's audio container (most are Opus/WebM, which the
                // embedded-tag fallback can't touch at all). Same "title::artist" key
                // Track.downloadKey()/TrackResult.downloadKey() use - this queue-item shape has no
                // Track of its own to call that extension on.
                val downloadKey = "${item.title.trim().lowercase()}::${item.artist.trim().lowercase()}"
                // artworkUri (a real file:// URI), not artworkData - this app's own in-app state
                // only ever reads artworkUri, so bytes silently left in-app art blank. See
                // PlayerViewModel.toMediaItem()'s fuller comment on the same fix.
                val localCoverUri = item.localFilePath?.let {
                    DownloadRepository.localCoverFile(this@PlaybackService, downloadKey)?.let { file -> Uri.fromFile(file) }
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
                    .setExtras(android.os.Bundle().apply { item.artistId?.let { putString("artistId", it) } })
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
                    // A downloaded file plays directly; anything else goes back through the
                    // resolve placeholder, because a stored stream URL would have expired.
                    .setUri(item.localFilePath?.toUri() ?: youTubeResolvePlaceholderUri(item.mediaId))
                    .setMediaMetadata(metadata)
                    .build()
            }

            player.setMediaItems(items, saved.index, saved.positionMs)
            player.prepare()
        }
    }

    /** The videoId currently being warmed, so overlapping transitions don't resolve it twice. */
    private var preloadingVideoId: String? = null

    /** The in-flight preload resolve, if any - cancelled on every new transition so a rapid run
     * of skips can't leave a growing pile of stale resolves competing with the one the player
     * actually needs next (the same shared cipher/InnerTube resolution pipeline the real,
     * currently-needed resolve also goes through - an abandoned preload doesn't just waste
     * bandwidth, it queues up behind everything else still running and delays the real one). */
    private var preloadJob: Job? = null

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
        if (autoplayInFlight) return
        val finished = player.currentMediaItem ?: return
        if (isLocalDeviceItem(finished)) return
        val mediaId = finished.mediaId
        val existingIds = (0 until player.mediaItemCount)
            .map { player.getMediaItemAt(it).mediaId }
            .toSet()

        autoplayInFlight = true
        serviceScope.launch {
            val router = MusicSearchRouter(this@PlaybackService)
            val primaryResults = if (mediaId.looksLikeYouTubeVideoId()) {
                runCatching { router.getRadioTracks(mediaId) }.getOrNull().orEmpty()
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
                runCatching { router.getRadioTracks(seedId) }.getOrNull().orEmpty()
            }.orEmpty()

            val candidates = if (primaryResults.isEmpty() && tasteResults.isEmpty()) {
                val artist = finished.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
                if (artist == null) emptyList() else runCatching { router.searchTracks(artist) }.getOrNull().orEmpty()
            } else {
                interleaveTwoToOne(primaryResults, tasteResults)
            }
            val related = candidates.distinctBy { it.id }.filter { it.id !in existingIds }.take(10)

            if (related.isNotEmpty()) {
                player.addMediaItems(
                    related.map { track ->
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
                if (autoAdvance) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
            autoplayInFlight = false
        }
    }

    /**
     * Resolves the *next* queue item's stream URL while the current track still plays.
     *
     * Resolution takes several seconds (InnerTube round trip plus NewPipe deobfuscation), which is
     * otherwise paid as a stall at the exact moment a track ends or the user hits skip. The result
     * lands in [ResolvedUrlCache] via the normal resolver path, so when playback actually reaches
     * the track it's served from cache instead of re-running the pipeline.
     *
     * Deliberately best-effort: any failure is swallowed, because a failed *preload* must never
     * surface as a playback error - the real attempt will run again and report properly.
     */
    private fun preloadNextTrack(player: ExoPlayer) {
        if (!preloadEnabled) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return

        // With REPEAT_MODE_ALL, "next" wraps - so on a single-item queue (or the last track of
        // any queue) it points back at the track already playing. Preloading that re-resolves
        // what's currently streaming: pure waste, and the two overlapping resolves race so
        // neither populates the cache for the other.
        if (nextIndex == player.currentMediaItemIndex) return

        val nextUri = runCatching { player.getMediaItemAt(nextIndex) }
            .getOrNull()?.localConfiguration?.uri ?: return
        val videoId = youTubeVideoIdFromResolvePlaceholder(nextUri) ?: return
        if (videoId == preloadingVideoId) return

        preloadJob?.cancel()
        preloadingVideoId = videoId
        preloadJob = serviceScope.launch(Dispatchers.IO) {
            runCatching { StreamResolverRouter.resolve(this@PlaybackService, videoId) }
            preloadingVideoId = null
        }
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
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
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
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            // OFF, not ALL. REPEAT_MODE_ALL was a leftover from the mock-catalog era, and with
            // real queues it means a one-track queue loops that track forever - the user reaches
            // the end of a song and hears it start again. Repeat is now something the user turns
            // on from Now Playing; when a queue genuinely ends, autoplay takes over instead.
            .apply { repeatMode = Player.REPEAT_MODE_OFF }

        player.addListener(object : Player.Listener {
            // A track that failed to resolve/stream (dead CDN link, blank search results, no
            // network, ...) has no valid source and would otherwise freeze the queue; skip
            // straight past it instead - but only until every item in the queue has had a turn,
            // so a fully offline queue fails fast (surfacing a real error - see PlayerViewModel)
            // rather than spinning through all ten mock-catalog tracks forever.
            override fun onPlayerError(error: PlaybackException) {
                consecutiveErrorCount++
                if (consecutiveErrorCount >= player.mediaItemCount.coerceAtLeast(1) || !recoverQueue(player)) {
                    // Keep the queue visible for a later retry, but leave the player in a clear,
                    // non-buffering state instead of appearing permanently stuck on the bad item.
                    player.playWhenReady = false
                    player.stop()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrorCount = 0
                recordCurrentPlayback(player)
            }

            // Queue exhausted: keep the music going with tracks related to what just finished,
            // rather than falling silent. Only fires at a real end - with repeat on, ExoPlayer
            // wraps before this is ever reached.
            override fun onPlaybackStateChanged(playbackState: Int) {
                recordCurrentPlayback(player)
                if (playbackState == Player.STATE_ENDED && isOnline(this@PlaybackService)) {
                    autoplayRelated(player)
                }
            }

            // Warm the next track's stream URL while the current one is still playing, so a skip
            // (or a natural track end) doesn't stall on the several-second resolve pipeline.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Queried once and reused below - a live ConnectivityManager lookup, not a field
                // read, so there's no reason to pay for it twice in the same transition.
                val online = isOnline(this@PlaybackService)
                // Offline, and this item isn't actually sitting on disk (on-device, or a
                // completed download): a network placeholder can still have a *prefix* of bytes
                // in StreamCache left over from an earlier prefetch, so CacheDataSource would
                // serve those first and only fail once it runs out mid-track - starting to play a
                // song that's about to cut out partway through. Jump straight to the next
                // genuinely downloaded item instead, or stop cleanly (resuming automatically once
                // back online - see the observeOnline collector below) if none remain, rather than
                // letting it start at all.
                if (mediaItem != null && !online && offlineItem(mediaItem) == null) {
                    if (!recoverQueue(player)) {
                        player.playWhenReady = false
                        player.stop()
                        stoppedForOffline = true
                    }
                    return
                }
                historyRecordedForTransition = false
                recordCurrentPlayback(player)
                preloadNextTrack(player)
                saveQueue(player)
                mediaItem?.let(::prefetchFullTrack)
                observeLikeStateForCurrentTrack(player)
                // We've just landed on what is, right now, the last item in the queue - top up a
                // taste-blended continuation in the background while it plays, rather than only
                // starting that fetch once it actually ends (see autoplayRelated's own doc). Covers
                // a queue that only ever had one/a few items to begin with (a fresh Home/Search
                // play) exactly the same way it covers one that's simply run down to its last track.
                if (!player.hasNextMediaItem() && online) {
                    autoplayRelated(player, autoAdvance = false)
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                saveQueue(player)
            }
        })

        // Resumes automatically once connectivity returns, from wherever the offline guard above
        // left off (the first item in the queue that wasn't actually downloaded) - the user
        // shouldn't have to go back and manually press play again just because they went offline.
        serviceScope.launch {
            observeOnline(this@PlaybackService).collect { online ->
                if (online && stoppedForOffline) {
                    stoppedForOffline = false
                    player.prepare()
                    player.play()
                }
            }
        }

        serviceScope.launch {
            DownloadRepository.getInstance(this@PlaybackService).completedDownloads.collect { downloads ->
                offlineFiles = downloads.flatMap { entry ->
                    listOfNotNull(entry.key to entry.filePath, entry.sourceId?.let { it to entry.filePath })
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
                player.skipSilenceEnabled = settings.skipSilence
                preloadEnabled = settings.preloadNextTrack
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
        restoreQueueIfEnabled(player)

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
                CurrentCodecInfo.update(format.sampleMimeType, format.bitrate)
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

        // Seeds the lock-screen/notification heart button's initial state - onMediaItemTransition
        // (which also drives this) only fires on an actual transition, not for whatever the
        // restored queue's starting track already was.
        observeLikeStateForCurrentTrack(player)
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
        prefetchJob?.cancel()
        val uri = mediaItem.localConfiguration?.uri ?: return
        if (youTubeVideoIdFromResolvePlaceholder(uri) == null) return
        prefetchJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val dataSource = cachedDataSourceFactory.createDataSource() as CacheDataSource
                CacheWriter(dataSource, DataSpec(uri), null, null).cache()
            }
        }
    }

    private inner class PlaybackServiceCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // A fresh setMediaItems call is a new playback attempt - don't carry over the
            // failure count from whatever was playing (or failing) before.
            consecutiveErrorCount = 0
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                future.set(mediaItems.map { resolveMediaItem(it) })
            }
            return future
        }

        // Declares the "toggle like" custom command available to every controller (the system's
        // notification/lock-screen controller included) - without this, MediaSession.setCustomLayout
        // below would have nothing to attach the button to.
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                base.availableSessionCommands.buildUpon()
                    .add(SessionCommand(ACTION_TOGGLE_LIKE, android.os.Bundle.EMPTY))
                    .add(SessionCommand(ACTION_AUTOPLAY_AND_ADVANCE, android.os.Bundle.EMPTY))
                    .build(),
                base.availablePlayerCommands,
            )
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
                        autoplayRelated(player)
                    }
                }
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
            sourceType = if (isRealVideoId) MusicSource.YOUTUBE_MUSIC else null,
            sourceId = if (isRealVideoId) mediaId else null,
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    // Swiping the app away from Recents stops playback entirely, by explicit product choice here
    // (not every media app's default - some keep playing in the background) - so there's never a
    // stray notification/audio still running with no app UI behind it.
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.stop()
        stopSelf()
    }

    override fun onDestroy() {
        ListenTogetherRepository.getInstance(this).detach()
        equalizerController.release()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
