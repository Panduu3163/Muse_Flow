package com.example

import android.app.PendingIntent
import android.content.Intent
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
            // A track that failed to resolve (dead CDN link, blank search results, ...) has no
            // valid source and would otherwise freeze the queue; skip straight past it instead.
            override fun onPlayerError(error: PlaybackException) {
                player.seekToNextMediaItem()
                player.prepare()
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
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch {
                future.set(mediaItems.map { resolveMediaItem(it) })
            }
            return future
        }
    }

    /** Looks up the placeholder's mediaId in the catalog and fills in a real, playable URI. */
    private suspend fun resolveMediaItem(item: MediaItem): MediaItem {
        val index = item.mediaId.toIntOrNull()
        val track = index?.let { MusicData.tracks.getOrNull(it) } ?: return item
        val resolved = resolver.resolve(track)
        val streamUrl = resolved?.directStreamUrl ?: return item

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
    // If something IS playing, leave it running - that's the whole point of background playback.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
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
