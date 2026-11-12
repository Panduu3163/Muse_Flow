package com.example

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistImportBoundsTest {
    private fun code(json: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(json.toByteArray()) }
        return "museflow-playlist:v1:" + Base64.encodeToString(output.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    @Test fun malformedOrUnboundedCsvFailsBeforeNetworkMatching() {
        listOf("Title,Artist\n\"unfinished,Someone", "Title,Artist\n${"a".repeat(16_385)},Someone",
            (0..256).joinToString(","), "Title,Artist\n" + "Song,Artist\n".repeat(MAX_PLAYLIST_IMPORT_TRACKS + 1))
            .forEach { assertTrue(runCatching { parsePlaylistCsv(it) }.isFailure) }
    }
    @Test fun m3uRejectsTooManyEntriesAndHugeMetadata() {
        assertTrue(runCatching { parsePlaylistM3u("/music/song.mp3\n".repeat(MAX_PLAYLIST_IMPORT_TRACKS + 1)) }.isFailure)
        assertTrue(runCatching { parsePlaylistM3u("#EXTINF:1," + "x".repeat(16_385) + "\n/song.mp3") }.isFailure)
    }
    @Test fun pastedCodesBoundCompressedAndDecompressedDataAndNesting() {
        assertNull(PlaylistShareCodec.decode("x".repeat(MAX_PLAYLIST_SHARE_CODE_CHARS + 1)))
        assertNull(PlaylistShareCodec.decode(code(" ".repeat(MAX_PLAYLIST_IMPORT_BYTES + 1))))
        assertNull(PlaylistShareCodec.decode(code("[".repeat(33) + "0" + "]".repeat(33))))
    }
    @Test fun expiredPlaybackUrlsNeverTravelInNewOrLegacyShareCodes() {
        val track = Track("Song", "Artist", "Album", "3:00", "", 0, sourceId = "abcdefghijk",
            sourceType = MusicSource.YOUTUBE_MUSIC, streamUrl = "https://example.invalid/expired-token")
        val restored = PlaylistShareCodec.decode(PlaylistShareCodec.encode("Mix", listOf(track)))!!
        assertNull(restored.tracks.single().streamUrl)
        val legacy = code("""{"schemaVersion":1,"name":"Mix","tracks":[{"title":"Song","artist":"Artist","streamUrl":"https://example.invalid/old"}]}""")
        assertNull(PlaylistShareCodec.decode(legacy)!!.tracks.single().streamUrl)
    }
}
