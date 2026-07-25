package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [BackupRepository.buildBackupJson]/[BackupRepository.restoreFromJson] round-trip a user's whole
 * library through JSON - a silent field-mapping bug here means data quietly doesn't come back on
 * restore. These tests exercise the actual serialize/deserialize functions directly, without a
 * real database.
 *
 * Runs under Robolectric (rather than plain JUnit) solely because it exercises real
 * `org.json.JSONObject` parsing - the plain android.jar on the unit-test classpath stubs that out.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryRoundTripTest {

    @Test
    fun `a fully-populated liked song survives a json round trip`() {
        val original = LikedSongEntity(
            key = "yt:abc123",
            title = "Song Title",
            artist = "Some Artist",
            album = "Some Album",
            duration = "3:45",
            gradientIndex = 2,
            imageUrl = "https://example.com/art.jpg",
            streamUrl = "https://example.com/stream.m4a",
            likedAt = 1_700_000_000_000L,
            sourceId = "abc123",
            sourceType = "youtube"
        )

        val restored = original.toBackupJson().toLikedSongEntity()

        assertEquals(original, restored)
    }

    @Test
    fun `null-able liked song fields round trip as null, not the string 'null'`() {
        val original = LikedSongEntity(
            key = "local:xyz",
            title = "Title",
            artist = "Artist",
            album = "Album",
            duration = "2:00",
            gradientIndex = 0,
            imageUrl = null,
            streamUrl = null,
            likedAt = 0L,
            sourceId = null,
            sourceType = null
        )

        val restored = original.toBackupJson().toLikedSongEntity()

        assertEquals(original, restored)
        assertNull(restored?.imageUrl)
        assertNull(restored?.sourceId)
    }

    @Test
    fun `a liked song entry with a blank key is rejected on restore`() {
        // A key is the primary key downstream (LikedSongDao.like upserts by it) - silently
        // accepting a blank one would corrupt the liked-songs table.
        val json = org.json.JSONObject().apply {
            put("key", "")
            put("title", "Title")
        }
        assertNull(json.toLikedSongEntity())
    }

    @Test
    fun `a fully-populated playlist track survives a json round trip`() {
        val original = PlaylistTrackEntity(
            playlistId = 42L,
            key = "yt:def456",
            title = "Track",
            artist = "Artist",
            album = "Album",
            duration = "4:10",
            gradientIndex = 5,
            imageUrl = "https://example.com/cover.jpg",
            streamUrl = "https://example.com/stream2.m4a",
            sourceId = "def456",
            sourceType = "youtube",
            addedAt = 1_700_000_500_000L
        )

        // Restore always assigns tracks to whichever new playlist id was just created - the json
        // itself carries no playlistId, so this mirrors BackupRepository.restoreFromJson's actual
        // call (a fresh id, not the original one).
        val restored = original.toBackupJson().toPlaylistTrackEntity(playlistId = 99L)

        assertEquals(original.copy(playlistId = 99L), restored)
    }

    @Test
    fun `a playlist track entry with a blank key is rejected on restore`() {
        val json = org.json.JSONObject().apply {
            put("key", "")
            put("title", "Title")
        }
        assertNull(json.toPlaylistTrackEntity(playlistId = 1L))
    }
}
