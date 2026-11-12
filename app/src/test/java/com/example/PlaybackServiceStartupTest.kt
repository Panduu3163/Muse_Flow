package com.example

import androidx.media3.session.MediaSession
import androidx.media3.common.Player
import android.os.Bundle
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackServiceStartupTest {
    @Test fun taskRemovalReleasesTheSessionAndInvalidatesOutsideControls() {
        val lifecycle = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val service = lifecycle.get()
            val session = PlaybackService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }.get(service) as MediaSession
            session.player.setMediaItem(androidx.media3.common.MediaItem.Builder().setMediaId("local-test")
                .setUri("file:///music/test.wav").build())
            service.onTaskRemoved(android.content.Intent())
            assertTrue(PlaybackControls.token == null)
            assertFalse(PlaybackControls.state.playing)
            val controller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(service.packageName, 123, 123, 1, 1, true, Bundle.EMPTY, true)
            assertTrue(service.onGetSession(controller) == null)
        } finally { lifecycle.destroy() }
    }
    @Test fun createsTheActualPlaybackServiceAndSession() {
        val lifecycle = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val field = PlaybackService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }
            assertNotNull(field.get(lifecycle.get()) as? MediaSession)
        } finally { lifecycle.destroy() }
    }

    @Test fun actualCallbackGrantsTrustedPlaybackCommandsUsingTheModernConnectionApi() {
        val lifecycle = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val service = lifecycle.get()
            val session = PlaybackService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }.get(service) as MediaSession
            val type = PlaybackService::class.java.declaredClasses.single { it.simpleName == "PlaybackServiceCallback" }
            assertFalse("Deprecated override takes precedence over onConnectAsync", type.declaredMethods.any { it.name == "onConnect" })
            val callback = type.getDeclaredConstructor(PlaybackService::class.java).apply { isAccessible = true }.newInstance(service) as MediaSession.Callback
            fun info(trusted: Boolean) = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                service.packageName, 123, 123, 1, 1, trusted, Bundle.EMPTY, true)
            val trusted = callback.onConnectAsync(session, info(true)).get()
            for (command in listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
                assertTrue("Required playback command $command missing", trusted.availablePlayerCommands.contains(command))
            }
            assertTrue(trusted.availableSessionCommands.contains(androidx.media3.session.SessionCommand(ACTION_FILL_QUEUE, Bundle.EMPTY)))
            val untrusted = callback.onConnectAsync(session, info(false)).get()
            assertFalse(untrusted.availablePlayerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        } finally { lifecycle.destroy() }
    }
}
