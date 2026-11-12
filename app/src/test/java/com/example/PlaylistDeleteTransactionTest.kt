package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class PlaylistDeleteTransactionTest {
    @Test fun metadataDeleteFailurePreservesThePlaylistsSongsUntilSuccessfulRetry() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = PlaylistRepository(db)
            val track = Track("Kept song", "Artist", "", "3:00", "", 0, sourceId = "abcdefghijk", sourceType = MusicSource.YOUTUBE_MUSIC)
            val id = repository.createWithTracks("Kept playlist", listOf(track))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER deny_delete BEFORE DELETE ON playlists BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END")
            assertTrue(runCatching { repository.delete(id) }.isFailure)
            assertEquals(1, db.playlistDao().backupSnapshot().size)
            assertEquals(1, db.playlistTrackDao().getAll().size)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER deny_delete")
            repository.delete(id)
            assertTrue(db.playlistDao().backupSnapshot().isEmpty())
            assertTrue(db.playlistTrackDao().getAll().isEmpty())
        } finally { db.close() }
    }
}
