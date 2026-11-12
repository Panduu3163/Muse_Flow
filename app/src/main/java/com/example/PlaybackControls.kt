package com.example

import coil3.request.allowHardware

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import coil3.toBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal data class OutsidePlaybackState(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val hasQueue: Boolean = false,
    val previous: Boolean = false,
    val next: Boolean = false,
    val artwork: android.graphics.Bitmap? = null,
)

/** Main-thread bridge to the existing session. Never constructs or starts a player/service. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object PlaybackControls {
    private var artworkScope: CoroutineScope? = null
    private var artworkSource: Any? = null
    private var artworkJob: Job? = null
    private var artworkGeneration = 0L
    var token: SessionToken? = null
        private set
    var state = OutsidePlaybackState()
        private set

    fun attach(context: Context, sessionToken: SessionToken, player: Player, scope: CoroutineScope) {
        artworkScope = scope
        token = sessionToken
        publish(context, player)
        requestArtwork(context)
    }

    fun publish(context: Context, player: Player) {
        val source = player.mediaMetadata.artworkData ?: player.mediaMetadata.artworkUri
        val changedArtwork = source != artworkSource
        if (changedArtwork) { artworkJob?.cancel(); artworkGeneration++; artworkSource = source }
        val updated = OutsidePlaybackState(
            player.mediaMetadata.title?.toString().orEmpty(),
            player.mediaMetadata.artist?.toString().orEmpty(),
            player.playWhenReady && player.playbackState in setOf(Player.STATE_BUFFERING, Player.STATE_READY) && player.playerError == null,
            player.mediaItemCount > 0,
            player.hasPreviousMediaItem() || player.currentPosition > 3_000,
            player.hasNextMediaItem(),
            if (changedArtwork) null else state.artwork,
        )
        if (updated != state) { state = updated; refresh(context) }
        if (changedArtwork) requestArtwork(context)
    }

    fun detach(context: Context, preserveMessage: Boolean = false) {
        cancelArtwork()
        artworkScope = null
        artworkSource = null
        token = null
        if (!preserveMessage || state.hasQueue || state.playing) state = OutsidePlaybackState()
        refresh(context)
    }

    fun cancelArtwork() {
        artworkGeneration++
        artworkJob?.cancel()
        artworkJob = null
        state = state.copy(artwork = null)
    }

    /** Reuses the app's Coil cache; one thumbnail per metadata change, only with a live widget. */
    fun requestArtwork(context: Context) {
        val source = artworkSource ?: return
        val scope = artworkScope ?: return
        if (artworkJob?.isActive == true || state.artwork != null || !state.hasQueue) return
        if (AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PlaybackWidget::class.java)).isEmpty()) return
        val generation = artworkGeneration
        val app = context.applicationContext
        artworkJob = scope.launch {
            try {
                val result = app.imageLoader.execute(ImageRequest.Builder(app).data(source).size(512).allowHardware(false).build())
                if (generation == artworkGeneration && state.hasQueue && result is SuccessResult) {
                    state = state.copy(artwork = result.image.toBitmap(512, 512))
                    refresh(app)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The decorative placeholder remains available. */ }
        }
    }

    fun showError(context: Context, message: String) {
        state = OutsidePlaybackState(artist = message)
        refresh(context)
    }

    private fun refresh(context: Context) {
        PlaybackWidget.updateAll(context)
        TileService.requestListeningState(context, ComponentName(context, PlaybackTileService::class.java))
    }

    /** A short-lived controller can only attach to the live token, never cold-start a service. */
    fun command(context: Context, action: String, finished: () -> Unit) {
        val captured = token ?: return finished()
        val future = MediaController.Builder(context.applicationContext, captured).buildAsync()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val completed = java.util.concurrent.atomic.AtomicBoolean()
        val deadline = android.os.SystemClock.elapsedRealtime() + 2_000
        fun finishOnce() { if (completed.compareAndSet(false, true)) finished() }
        val timeout = Runnable { future.cancel(false); finishOnce() }
        main.postDelayed(timeout, 2_000)
        future.addListener({
            main.removeCallbacks(timeout)
            try {
                if (!future.isCancelled) {
                    val controller = future.get()
                    try {
                        if (!completed.get() && android.os.SystemClock.elapsedRealtime() <= deadline && token == captured) when (action) {
                            "pause" -> controller.pause()
                            "previous" -> controller.seekToPrevious()
                            "next" -> if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                                else controller.sendCustomCommand(SessionCommand(ACTION_AUTOPLAY_AND_ADVANCE, Bundle.EMPTY), Bundle.EMPTY)
                        }
                    } finally { controller.release() }
                }
            } catch (_: Exception) {
                // A released session is expected when task removal races a button press.
            } finally { finishOnce() }
        }, ContextCompat.getMainExecutor(context))
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 8_100, Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
