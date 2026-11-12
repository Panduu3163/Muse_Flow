package com.example

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestoredPlaybackUriTest {
    private fun saved(id: String, path: String? = null) = SavedQueueItem(id, "Song", "Artist", "Album", null, path)

    @Test fun olderDeviceQueueWithoutLocalPathRestoresItsContentUri() {
        val id = "content://media/external/audio/media/17"
        assertEquals(Uri.parse(id), saved(id).restoredPlaybackUri())
    }

    @Test fun bareDownloadedPathIsEncodedAsAFileUri() {
        val path = "/storage/emulated/0/Music/Artist: song 100%.opus"
        val uri = saved("dQw4w9WgXcQ", path).restoredPlaybackUri()
        assertEquals("file", uri.scheme)
        assertEquals(path, uri.path)
    }

    @Test fun savedFileIdentityAlsoBypassesOnlineResolution() {
        val id = "file:///storage/emulated/0/Music/song%20one.wav"
        assertEquals(Uri.parse(id), saved(id).restoredPlaybackUri())
    }

    @Test fun downloadedFileTakesPriorityOverOnlineRecordingIdentity() {
        val file = "file:///storage/emulated/0/Music/download.opus"
        assertEquals(Uri.parse(file), saved("dQw4w9WgXcQ", file).restoredPlaybackUri())
    }

    @Test fun onlineRecordingIsFreshlyResolvedInsteadOfReusingExpiredRemoteUrl() {
        val id = "dQw4w9WgXcQ"
        assertEquals(youTubeResolvePlaceholderUri(id),
            saved(id, "https://example.invalid/expired-stream").restoredPlaybackUri())
    }
}
