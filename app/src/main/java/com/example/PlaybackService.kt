package com.example

import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val resolver by lazy { TrackStreamResolver(serviceScope, applicationContext) }
    private val equalizerController = EqualizerController()
    private val cachedDataSourceFactory by lazy { buildCachedDataSourceFactory() }
    private var prefetchJob: Job? = null

    // Repeat mode is ALL, so auto-skipping past a failed track (see onPlayerError below) would
    // spin through the whole queue forever if NOTHING can play - overwhelmingly the "no network"
    // case, since every mock-catalog track needs a live JioSaavn search to resolve. This counts
    // consecutive failures so playback gives up after one full lap instead of looping silently.
    private var consecutiveErrorCount = 0

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
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .apply { item.artworkUrl?.let { setArtworkUri(it.toUri()) } }
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

    /** Guards against stacking autoplay fetches if STATE_ENDED fires more than once. */
    private var autoplayInFlight = false

    /**
     * Appends tracks similar to whatever just finished, so a finished queue continues rather than
     * stopping dead.
     *
     * Similarity is "more from this artist", found through the normal search path. That's a
     * deliberately modest definition - it needs no new API surface, and it's honest about being a
     * heuristic rather than pretending to be a recommendation engine.
     */
    private fun autoplayRelated(player: ExoPlayer) {
        if (autoplayInFlight) return
        val finished = player.currentMediaItem ?: return
        val artist = finished.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: return
        val existingIds = (0 until player.mediaItemCount)
            .map { player.getMediaItemAt(it).mediaId }
            .toSet()

        autoplayInFlight = true
        serviceScope.launch {
            val related = runCatching { MusicSearchRouter(this@PlaybackService).searchTracks(artist) }
                .getOrNull()
                .orEmpty()
                .filter { it.id !in existingIds }
                .take(10)

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
                                    .apply { track.imageUrl?.let { setArtworkUri(it.toUri()) } }
                                    .build()
                            )
                            .build()
                    }
                )
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
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

        preloadingVideoId = videoId
        serviceScope.launch(Dispatchers.IO) {
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
                if (consecutiveErrorCount < player.mediaItemCount.coerceAtLeast(1)) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) consecutiveErrorCount = 0
            }

            // Queue exhausted: keep the music going with tracks related to what just finished,
            // rather than falling silent. Only fires at a real end - with repeat on, ExoPlayer
            // wraps before this is ever reached.
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) autoplayRelated(player)
            }

            // Warm the next track's stream URL while the current one is still playing, so a skip
            // (or a natural track end) doesn't stall on the several-second resolve pipeline.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                preloadNextTrack(player)
                saveQueue(player)
                mediaItem?.let(::prefetchFullTrack)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                saveQueue(player)
            }
        })

        serviceScope.launch {
            AppSettingsRepository(this@PlaybackService).state.collect { settings ->
                player.skipSilenceEnabled = settings.skipSilence
                preloadEnabled = settings.preloadNextTrack
                persistentQueueEnabled = settings.persistentQueue
                NormalizerAudioProcessor.INSTANCE.setEnabled(settings.audioNormalizationEnabled)
                BassBoostAudioProcessor.INSTANCE.setIntensity(settings.bassBoostEnabled, settings.bassBoostIntensity)
                CrossfeedAudioProcessor.INSTANCE.setIntensity(settings.crossfeedEnabled, settings.crossfeedIntensity)
            }
        }

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
        })
        // Re-applies live whenever the user changes a setting on EqualizerSettingsScreen - this
        // service never needs a direct reference to that screen (or vice versa), same
        // Flow-driven decoupling the rest of this app's settings already use.
        serviceScope.launch {
            EqualizerRepository.getInstance(this@PlaybackService).settings.collect { settings ->
                // Platform effect first (a silent no-op on devices that refuse it), then our own
                // DSP processor, which works everywhere.
                equalizerController.apply(settings)
                EqualizerAudioProcessor.INSTANCE.setGains(
                    enabled = settings.enabled,
                    gains = settings.bandLevelsMillibel.map(EqualizerAudioProcessor::millibelToDb),
                )
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
