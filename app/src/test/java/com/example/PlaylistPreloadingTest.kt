package com.example

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaylistPreloadingTest {
    @Test fun enabledPlaybackPreparesFiveSeconds() {
        assertEquals(5_000_000L, playlistPreloadConfiguration(true, true, Player.STATE_READY).targetPreloadDurationUs)
    }
    @Test fun pendingPlaybackCanPrepareNextOnceCurrentLoadingYields() {
        assertEquals(5_000_000L, playlistPreloadConfiguration(true, true, Player.STATE_BUFFERING).targetPreloadDurationUs)
    }
    @Test fun disabledSettingDisablesPlaylistPreloading() {
        assertEquals(C.TIME_UNSET, playlistPreloadConfiguration(false, true, Player.STATE_READY).targetPreloadDurationUs)
    }
    @Test fun restoredOrManuallyPausedQueueDoesNotPreload() {
        assertEquals(C.TIME_UNSET, playlistPreloadConfiguration(true, false, Player.STATE_READY).targetPreloadDurationUs)
    }
    @Test fun stoppedAndFinishedPlaybackDoNotPreload() {
        for (state in listOf(Player.STATE_IDLE, Player.STATE_ENDED))
            assertEquals(C.TIME_UNSET, playlistPreloadConfiguration(true, true, state).targetPreloadDurationUs)
    }
    @Test fun installedMedia3PlayerAcceptsEnableAndDisableConfiguration() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<android.app.Application>()).build()
        try {
            player.preloadConfiguration = playlistPreloadConfiguration(true, true, Player.STATE_READY)
            assertEquals(5_000_000L, player.preloadConfiguration.targetPreloadDurationUs)
            player.preloadConfiguration = playlistPreloadConfiguration(false, true, Player.STATE_READY)
            assertEquals(C.TIME_UNSET, player.preloadConfiguration.targetPreloadDurationUs)
        } finally { player.release() }
    }
}
