package com.example

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private val resolver = TrackStreamResolver(serviceScope)

    // Repeat mode is ALL, so auto-skipping past a failed track (see onPlayerError below) would
    // spin through the whole queue forever if NOTHING can play - overwhelmingly the "no network"
    // case, since every mock-catalog track needs a live JioSaavn search to resolve. This counts
    // consecutive failures so playback gives up after one full lap instead of looping silently.
    private var consecutiveErrorCount = 0

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
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
            .apply { repeatMode = Player.REPEAT_MODE_ALL }

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
        })

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackServiceCallback())
            .setSessionActivity(openAppIntent)
            .build()
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

    // Standard media-app pattern: if the user swipes the app away while nothing is actively
    // playing, there's no reason to keep the foreground service (and its notification) alive.
    // If something IS playing, leave it running - that's the whole point of background playback -
    // but ONLY if we can actually show the MediaStyle notification that's the user's one
    // remaining way to see/stop/control it once the app's UI is gone. If POST_NOTIFICATIONS was
    // never granted (or got revoked - some OEMs, e.g. Nothing OS, are also known to suppress
    // notifications more aggressively than stock Android), Media3's automatic notification
    // silently fails to post while the foreground service happily keeps playing regardless -
    // audio running with literally no way to stop it short of force-closing the app. Stopping
    // playback outright in that case is strictly better than leaving it invisible.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        val isActivelyPlaying = player.playWhenReady && player.mediaItemCount > 0
        val canShowNotification = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!isActivelyPlaying || !canShowNotification) {
            player.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
