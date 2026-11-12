package com.example

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Media3 prepares samples for the actual playlist successor, only after current loading yields. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun playlistPreloadConfiguration(enabled: Boolean, playWhenReady: Boolean, state: Int): ExoPlayer.PreloadConfiguration =
    if (enabled && playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED)
        ExoPlayer.PreloadConfiguration(5_000_000L)
    else ExoPlayer.PreloadConfiguration.DEFAULT
