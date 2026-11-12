package com.example

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Pauses a live session; otherwise opens the foreground player for explicit Play. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val current = PlaybackControls.state
        qsTile?.apply {
            state = if (current.playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            contentDescription = getString(if (current.playing) R.string.tile_pause else R.string.tile_open)
            if (Build.VERSION.SDK_INT >= 29) subtitle = getString(if (current.playing) R.string.control_pause else R.string.widget_open)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (PlaybackControls.state.playing && PlaybackControls.token != null) {
            PlaybackControls.command(this, "pause") { onStartListening() }
        } else {
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PlaybackControls.openApp(this))
            else openOnLegacyAndroid()
        }
    }

    // The PendingIntent overload was added in API 34. Only API 24–33 reach this helper.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openOnLegacyAndroid() {
        startActivityAndCollapse(android.content.Intent(this, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
