package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [PlaylistShareCodec.encode]/[PlaylistShareCodec.decode] round-trip a single playlist through a
 * compact text code - the same class of bug [BackupRepositoryRoundTripTest] guards against for the
 * whole-library backup format, just for the one-playlist share path.
 *
 * Runs under Robolectric for the same reason that test does: real `org.json.JSONObject` parsing,
 * plus `android.util.Base64` here, both stubbed out on the plain unit-test classpath.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistShareCodecTest {

    @Test
    fun `a fully-populated youtube track survives an encode-decode round trip`() {
        val track = Track(
            title = "Song Title",
            artist = "Some Artist",
            album = "Some Album",
            duration = "3:45",
            plays = "",
            gradientIndex = 0,
            imageUrl = "https://example.com/art.jpg",
            streamUrl = null,
            sourceType = MusicSource.YOUTUBE_MUSIC,
            sourceId = "abc123",
            albumId = "album123",
            artistId = "artist123",
        )

        val code = PlaylistShareCodec.encode("My Playlist", listOf(track))
        val decoded = PlaylistShareCodec.decode(code)

        assertEquals("My Playlist", decoded?.name)
        assertEquals(listOf(track), decoded?.tracks)
    }

    @Test
    fun `on-device tracks are dropped before encoding, never round-tripped`() {
        val onDevice = Track(
            title = "Local Song", artist = "Local Artist", album = "", duration = "2:30",
            plays = "", gradientIndex = 0, sourceType = MusicSource.LOCAL_DEVICE,
            streamUrl = "content://media/external/audio/1",
        )
        val online = Track(
            title = "Online Song", artist = "Online Artist", album = "", duration = "3:00",
            plays = "", gradientIndex = 0, sourceType = MusicSource.YOUTUBE_MUSIC, sourceId = "xyz",
        )

        val decoded = PlaylistShareCodec.decode(PlaylistShareCodec.encode("Mixed", listOf(onDevice, online)))

        assertEquals(1, decoded?.tracks?.size)
        assertEquals("Online Song", decoded?.tracks?.first()?.title)
    }

    @Test
    fun `decoding unrelated or garbage text returns null, never throws`() {
        assertNull(PlaylistShareCodec.decode("not a museflow code at all"))
        assertNull(PlaylistShareCodec.decode(""))
        assertNull(PlaylistShareCodec.decode("museflow-playlist:v1:not-valid-base64-gzip!!!"))
    }

    @Test
    fun `the prefix may appear anywhere in the pasted text, not only at the very start`() {
        val code = PlaylistShareCodec.encode("Road Trip", emptyList())
        val pastedWholeMessage = "Check out my playlist!\n$code\nEnjoy!"

        assertEquals("Road Trip", PlaylistShareCodec.decode(pastedWholeMessage)?.name)
    }

    @Test
    fun `an empty playlist still round trips with zero tracks`() {
        val decoded = PlaylistShareCodec.decode(PlaylistShareCodec.encode("Empty", emptyList()))

        assertEquals("Empty", decoded?.name)
        assertTrue(decoded?.tracks?.isEmpty() == true)
    }
}
