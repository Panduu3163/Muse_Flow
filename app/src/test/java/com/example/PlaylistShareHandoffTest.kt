package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistShareHandoffTest {
    @Test fun failedAndroidShareHandoffShowsRecoveryInsteadOfCrashing() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startActivity(intent: Intent) { throw IllegalStateException("Simulated failed Android handoff") }
        }
        context.sharePlaylist("Mix", listOf(Track("Song", "Artist", "", "3:00", "", 0,
            sourceType = MusicSource.YOUTUBE_MUSIC, sourceId = "abcdefghijk")))
        assertEquals("Couldn't open sharing. Try a smaller playlist or export a backup.", ShadowToast.getTextOfLatestToast())
    }
}
