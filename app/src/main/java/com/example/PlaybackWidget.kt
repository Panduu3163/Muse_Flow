package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.core.graphics.get
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

/** No polling, progress timer, network or separate playback owner. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        PlaybackControls.requestArtwork(context)
    }
    override fun onDisabled(context: Context) { PlaybackControls.cancelArtwork() }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateAll(context)
        PlaybackControls.requestArtwork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            PLAY -> {
                // Explicit widget interaction uses Media3's bounded shared resumption callback.
                MediaButtonReceiver().onReceive(context, Intent(Intent.ACTION_MEDIA_BUTTON)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)))
            }
            PAUSE, PREVIOUS, NEXT -> {
                if (PlaybackControls.token == null) {
                    updateAll(context)
                    return
                }
                val result = goAsync()
                PlaybackControls.command(context, when (intent.action) {
                    PAUSE -> "pause"; PREVIOUS -> "previous"; else -> "next"
                }) { result.finish() }
            }
        }
    }

    companion object {
        internal const val PLAY = "com.example.widget.PLAY"
        internal const val PAUSE = "com.example.widget.PAUSE"
        internal const val PREVIOUS = "com.example.widget.PREVIOUS"
        internal const val NEXT = "com.example.widget.NEXT"

        internal fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidget::class.java))
            if (ids.isEmpty()) return
            val state = PlaybackControls.state
            ids.forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val width = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
                val height = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
                val availableSide = minOf(width, height).coerceAtLeast(160)
                val modern = android.os.Build.VERSION.SDK_INT >= 31
                val side = if (modern) availableSide else when {
                    availableSide >= 288 -> 288
                    availableSide >= 224 -> 224
                    else -> 160
                }
                val layout = when {
                    modern || side == 224 -> R.layout.playback_widget
                    side == 288 -> R.layout.playback_widget_square_288
                    else -> R.layout.playback_widget_square_160
                }
                val views = RemoteViews(context.packageName, layout).apply {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        setViewLayoutWidth(R.id.widget_metadata, side.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                        setViewLayoutHeight(R.id.widget_metadata, side.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                    }
                    setImageViewBitmap(R.id.widget_artwork, WidgetArtwork.card(context, state.artwork, side, side))
                    setTextViewText(R.id.widget_title, state.title.ifBlank { context.getString(R.string.widget_open) })
                    setTextViewText(R.id.widget_artist, state.artist.ifBlank { context.getString(R.string.widget_choose) })
                    setOnClickPendingIntent(R.id.widget_metadata, PlaybackControls.openApp(context))
                    setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
                    setContentDescription(R.id.widget_play, context.getString(if (state.playing) R.string.control_pause else R.string.control_play))
                    setOnClickPendingIntent(R.id.widget_play, if (state.hasQueue)
                        action(context, if (state.playing) PAUSE else PLAY) else PlaybackControls.openApp(context))
                    setOnClickPendingIntent(R.id.widget_previous, if (state.hasQueue) action(context, PREVIOUS) else PlaybackControls.openApp(context))
                    setOnClickPendingIntent(R.id.widget_next, if (state.hasQueue) action(context, NEXT) else PlaybackControls.openApp(context))
                }
                manager.updateAppWidget(id, views)
            }
        }

        /** A dark cover tint keeps white text readable, including on bright artwork. */
        internal fun surfaceColor(artwork: android.graphics.Bitmap?): Int {
            if (artwork == null) return Color.rgb(32, 29, 46)
            var red = 0; var green = 0; var blue = 0
            for (y in 0..3) for (x in 0..3) {
                val pixel = artwork[(artwork.width - 1) * x / 3, (artwork.height - 1) * y / 3]
                red += Color.red(pixel); green += Color.green(pixel); blue += Color.blue(pixel)
            }
            return Color.rgb(18 + red / 64, 18 + green / 64, 24 + blue / 64)
        }

        private fun action(context: Context, action: String) = PendingIntent.getBroadcast(
            context, 0, Intent(context, PlaybackWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
