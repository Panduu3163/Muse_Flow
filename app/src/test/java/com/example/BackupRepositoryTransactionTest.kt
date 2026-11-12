package com.example

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRepositoryTransactionTest {
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private lateinit var repository: BackupRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        repository = BackupRepository(context, db)
    }

    @After fun tearDown() { db.close() }

    @Test fun databaseFailureRollsBackLikesPlaylistAndTracksTogether() = runBlocking {
        val original = LikedSongEntity("local:song", "Original", "Artist", "", "", 0, null, null, 1)
        db.likedSongDao().like(original)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_test_track BEFORE INSERT ON playlist_tracks " +
                "WHEN NEW.title = 'Fail insertion' BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
        val backup = document(likes = listOf(song("local:song").put("title", "Changed")), playlists = listOf(
            playlist("Imported", song("local:a"), song("local:b").put("title", "Fail insertion"))
        ))
        assertTrue(repository.restoreFromJson(backup.toString()) is RestoreResult.Failure)
        assertEquals(listOf(original), db.likedSongDao().backupSnapshot())
        assertTrue(db.playlistDao().backupSnapshot().isEmpty())
        assertTrue(db.playlistTrackDao().getAll().isEmpty())
    }

    @Test fun invalidLastRecordRejectsTheWholeDocumentBeforeAnyWrite() = runBlocking {
        val backup = document(likes = listOf(song("local:a")), playlists = listOf(
            playlist("Invalid", song("local:b"), JSONObject().put("title", "Missing key"))
        ))
        assertTrue(repository.restoreFromJson(backup.toString()) is RestoreResult.Failure)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertTrue(db.playlistDao().backupSnapshot().isEmpty())
    }

    @Test fun repeatedPlaylistsInsideOneDocumentAndRepeatedImportsAreIdempotent() = runBlocking {
        val entry = playlist("Mix", song("local:a"), song("local:b"))
        val json = document(playlists = listOf(entry, entry)).toString()
        assertEquals(RestoreResult.Success(0, 1, 1), repository.restoreFromJson(json))
        assertEquals(RestoreResult.Success(0, 0, 2), repository.restoreFromJson(json))
        assertEquals(1, db.playlistDao().backupSnapshot().size)
        assertEquals(2, db.playlistTrackDao().getAll().size)
    }

    @Test fun sameNamedPlaylistsWithDifferentTrackOrderRemainSeparate() = runBlocking {
        val first = playlist("Mix", song("local:a"), song("local:b"))
        val second = playlist("Mix", song("local:b"), song("local:a"))
        val json = document(playlists = listOf(first, second)).toString()
        assertEquals(RestoreResult.Success(0, 2), repository.restoreFromJson(json))
        assertEquals(RestoreResult.Success(0, 0, 2), repository.restoreFromJson(json))
        val orders = db.playlistDao().backupSnapshot().map { db.playlistTrackDao().backupSnapshot(it.id).map { t -> t.key } }
        assertEquals(setOf(listOf("local:a", "local:b"), listOf("local:b", "local:a")), orders.toSet())
    }

    @Test fun concurrentRestoresCannotCreateDuplicatePlaylists() = runBlocking {
        val json = document(playlists = listOf(playlist("Mix", song("local:a")))).toString()
        val results = coroutineScope {
            val first = async { repository.restoreFromJson(json) }
            val second = async { repository.restoreFromJson(json) }
            listOf(first.await(), second.await()).map { it as RestoreResult.Success }
        }
        assertEquals(1, results.sumOf { it.playlistsRestored })
        assertEquals(1, results.sumOf { it.playlistsSkipped })
        assertEquals(1, db.playlistDao().backupSnapshot().size)
    }

    @Test fun exportAndRestorePreservePinsMetadataAndTrackOrder() = runBlocking {
        val liked = LikedSongEntity("local:liked", "Liked", "Artist", "Album", "3:00", 2, null, null, 10)
        db.likedSongDao().like(liked)
        val id = db.playlistDao().insert(PlaylistEntity(name = "Pinned", createdAt = 4, isPinned = true, remoteId = "remote"))
        db.playlistTrackDao().insertAll(listOf(
            track(id, "local:b", 20), track(id, "local:a", 10),
        ))
        val backup = repository.buildBackupJson()
        val target = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            assertEquals(RestoreResult.Success(1, 1), BackupRepository(context, target).restoreFromJson(backup))
            val restored = target.playlistDao().backupSnapshot().single()
            assertTrue(restored.isPinned)
            assertEquals("remote", restored.remoteId)
            assertEquals(4L, restored.createdAt)
            assertEquals(listOf(liked), target.likedSongDao().backupSnapshot())
            assertEquals(listOf("local:a", "local:b"), target.playlistTrackDao().backupSnapshot(restored.id).map { it.key })
        } finally { target.close() }
    }

    @Test fun originalSchemaOneWithoutPinsStillRestores() = runBlocking {
        val legacy = """{"schemaVersion":1,"likedSongs":[],"playlists":[{"name":"Old","tracks":[{"key":"legacy::artist","title":"Old song"}]}]}"""
        assertEquals(RestoreResult.Success(0, 1), repository.restoreFromJson(legacy))
        val restored = db.playlistDao().backupSnapshot().single()
        assertFalse(restored.isPinned)
        assertEquals("legacy::artist", db.playlistTrackDao().backupSnapshot(restored.id).single().key)
    }

    @Test fun malformedStructuresTypesDuplicateRecordingsAndFutureVersionsDoNotMutateLibrary() = runBlocking {
        val invalid = listOf(
            """{"schemaVersion":1}""",
            document().put("likedSongs", JSONObject()).toString(),
            document().put("schemaVersion", 5).toString(),
            document(likes = listOf(song("local:a").put("title", 42))).toString(),
            document(playlists = listOf(playlist("Bad", song("local:a"), song("local:a")))).toString(),
            document().toString() + "trailing data",
        )
        invalid.forEach { assertTrue(it, repository.restoreFromJson(it) is RestoreResult.Failure) }
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertTrue(db.playlistDao().backupSnapshot().isEmpty())
    }

    @Test fun unknownFieldsAreIgnoredAndRecordingVersionsRemainDistinct() = runBlocking {
        val audio = song("old-key").put("sourceId", "audio000001").put("sourceType", "YOUTUBE_MUSIC")
        val video = song("old-key").put("sourceId", "video000001").put("sourceType", "YOUTUBE_MUSIC")
        val backup = document(likes = listOf(audio, video)).put("futureOptionalField", JSONObject().put("value", 1))
        assertEquals(RestoreResult.Success(2, 0), repository.restoreFromJson(backup.toString()))
        assertEquals(setOf("yt:audio000001", "yt:video000001"), db.likedSongDao().backupSnapshot().map { it.key }.toSet())
    }

    @Test fun automaticBackupKeepsPreviousCopyIfTheNextSnapshotCannotBeExported() = runBlocking {
        db.likedSongDao().like(LikedSongEntity("local:a", "Kept", "Artist", "", "", 0, null, null, 1))
        assertTrue(repository.writeAutoBackup())
        val file = AtomicFile(File(context.filesDir, "backups/auto_backup.json"))
        val original = file.readFully()
        assertEquals(1, parseBackupDocument(original.decodeToString()).likes.size)
        assertNotNull(repository.lastBackupAt.first())
        // A document this reader cannot restore must never replace the last verified snapshot.
        db.playlistDao().insert(PlaylistEntity(name = "", createdAt = 1))
        assertFalse(repository.writeAutoBackup())
        assertArrayEquals(original, file.readFully())
    }

    @Test fun boundedReaderAcceptsUtf8BomAndRejectsOversizeAndBrokenUtf8() {
        val json = document().toString()
        assertEquals(json, readBackupDocument(ByteArrayInputStream(("\uFEFF" + json).toByteArray())))
        assertThrows(IllegalArgumentException::class.java) {
            readBackupDocument(ByteArrayInputStream("too many bytes".toByteArray()), maxBytes = 4)
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            readBackupDocument(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
    }

    @Test fun followedArtistsRoundTripWithoutOverwritingExistingFollowsOrCopyingReleaseBaselines() = runBlocking {
        val artist = FollowedArtistEntity("artist-1", "Artist", "https://example.com/art.jpg", "YOUTUBE_MUSIC", 123, "old-track")
        db.followedArtistDao().upsert(artist)
        val backup = repository.buildBackupJson()
        val json = JSONObject(backup)
        assertEquals(4, json.getInt("schemaVersion"))
        assertFalse(json.getJSONArray("followedArtists").getJSONObject(0).has("knownTrackIds"))
        val target = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val restore = BackupRepository(context, target)
            assertEquals(RestoreResult.Success(0, 0, artistsRestored = 1), restore.restoreFromJson(backup))
            assertEquals(artist.copy(knownTrackIds = ""), target.followedArtistDao().getAll().single())
            val newer = artist.copy(name = "Existing local name", knownTrackIds = "newer-baseline")
            target.followedArtistDao().upsert(newer)
            assertEquals(RestoreResult.Success(0, 0, artistsSkipped = 1), restore.restoreFromJson(backup))
            assertEquals(newer, target.followedArtistDao().getAll().single())
        } finally { target.close() }
    }

    @Test fun previewCountsDuplicatesWithoutWritingAndRechecksChangesAtConfirmation() = runBlocking {
        db.likedSongDao().like(LikedSongEntity("local:a", "Existing", "Artist", "", "", 0, null, null, 1))
        val existing = document(playlists = listOf(playlist("Mix", song("local:a"))))
        repository.restoreFromJson(existing.toString())
        val backup = document(likes = listOf(song("local:a"), song("local:b")),
            playlists = listOf(playlist("Mix", song("local:a")), playlist("New", song("local:b")), playlist("New", song("local:b"))))
            .put("schemaVersion", 2).put("followedArtists", JSONArray().put(artistJson("artist-1")))
        val prepared = repository.prepareRestore(backup.toString())
        assertEquals(BackupRestorePreview(1, 1, 1, 2, 1, 0, true), prepared.preview)
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
        assertEquals(1, db.playlistDao().backupSnapshot().size)
        assertTrue(db.followedArtistDao().getAll().isEmpty())
        // Another action imports the new entries after the preview. Confirmation must recheck.
        assertTrue(repository.restoreFromJson(backup.toString()) is RestoreResult.Success)
        assertEquals(RestoreResult.Success(2, 0, 3, 0, 1), repository.restorePrepared(prepared.document))
        assertEquals(2, db.playlistDao().backupSnapshot().size)
        assertEquals(1, db.followedArtistDao().getAll().size)
    }

    @Test fun artistInsertFailureRollsBackTheEntireLibraryRestore() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_artist BEFORE INSERT ON followed_artists " +
                "BEGIN SELECT RAISE(ABORT, 'injected artist failure'); END"
        )
        val backup = document(likes = listOf(song("local:a")), playlists = listOf(playlist("Mix", song("local:a"))))
            .put("schemaVersion", 2).put("followedArtists", JSONArray().put(artistJson("artist-1")))
        assertTrue(repository.restoreFromJson(backup.toString()) is RestoreResult.Failure)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertTrue(db.playlistDao().backupSnapshot().isEmpty())
        assertTrue(db.playlistTrackDao().getAll().isEmpty())
        assertTrue(db.followedArtistDao().getAll().isEmpty())
    }

    @Test fun oldBackupsDeclareMissingArtistCoverageAndNeverClearFollows() = runBlocking {
        db.followedArtistDao().upsert(FollowedArtistEntity("kept", "Kept", null, "YOUTUBE_MUSIC", 1))
        val preview = repository.prepareRestore(document().toString())
        assertFalse(preview.preview.includesArtists)
        assertEquals(RestoreResult.Success(0, 0), repository.restorePrepared(preview.document))
        assertEquals("kept", db.followedArtistDao().getAll().single().artistId)
        assertTrue(repository.restoreFromJson(document().put("schemaVersion", 2).toString()) is RestoreResult.Failure)
    }

    private fun artistJson(id: String) = JSONObject().put("artistId", id).put("name", "Artist")
        .put("sourceType", "YOUTUBE_MUSIC").put("followedAt", 1)

    private fun document(likes: List<JSONObject> = emptyList(), playlists: List<JSONObject> = emptyList()) = JSONObject()
        .put("schemaVersion", 1).put("likedSongs", JSONArray(likes)).put("playlists", JSONArray(playlists))

    private fun song(key: String) = JSONObject().put("key", key).put("title", key)

    private fun playlist(name: String, vararg songs: JSONObject) = JSONObject().put("name", name)
        .put("tracks", JSONArray(songs.toList()))

    private fun track(playlistId: Long, key: String, time: Long) = PlaylistTrackEntity(
        playlistId, key, key, "Artist", "", "", 0, null, null, addedAt = time,
    )
}
