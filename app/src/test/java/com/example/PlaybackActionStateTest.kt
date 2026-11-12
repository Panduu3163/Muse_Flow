package com.example

import org.junit.Assert.*
import org.junit.Test

class PlaybackActionStateTest {
    @Test fun activePlaybackShowsPause() = assertTrue(NowPlayingState(isPlaying = true).showPauseAction)
    @Test fun requestedPlaybackKeepsPauseDuringBuffering() =
        assertTrue(NowPlayingState(isBuffering = true, playWhenReady = true).showPauseAction)
    @Test fun pausedBufferingShowsPlay() =
        assertFalse(NowPlayingState(isBuffering = true, playWhenReady = false).showPauseAction)
    @Test fun idleWithoutAudioDoesNotPretendToBePlaying() =
        assertFalse(NowPlayingState(isBuffering = false, playWhenReady = true).showPauseAction)
}
