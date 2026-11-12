package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration15To16Test {
    @Test fun upgradesRealSchemaWithoutMovingAudioAndKeepsLibraryAndHistoryLinks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "recording-migration-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MuseFlowDatabase::class.java, name)
            .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19).allowMainThreadQueries().build()
        val oldKey = "same title::same artist"
        val id = "audio000001"
        val key = "yt:$id"
        val dir = DownloadRepository.downloadsDir(context).apply { mkdirs() }
        val audio = File(dir, "legacy.audio").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val cover = File(dir, "legacy.cover").apply { writeBytes(byteArrayOf(5, 6)) }
        val originalBytes = audio.readBytes()
        val before = open()
        try {
            val db = before
            db.downloadedTrackDao().upsert(DownloadedTrackEntity(oldKey, "Same title", "Same artist", "Album", "3:00", 0,
                null, audio.absolutePath, "COMPLETED", 100, id, "YOUTUBE_MUSIC"))
            db.likedSongDao().like(LikedSongEntity(oldKey, "Same title", "Same artist", "Album", "3:00", 0, null, null, 100, id, "YOUTUBE_MUSIC"))
            db.playbackHistoryDao().upsert(PlaybackHistoryEntity(oldKey, "Same title", "Same artist", "Album", "3:00", 0, null, null, 100,
                id, "YOUTUBE_MUSIC", playCount = 3))
            db.playbackHistoryDao().upsert(PlaybackHistoryEntity(key, "Same title updated", "Same artist", "Album", "3:00", 0, null, null, 200,
                id, "YOUTUBE_MUSIC", playCount = 2))
            db.playbackHistoryDao().insertEvent(PlaybackEventEntity(trackKey = oldKey, playedAt = 100))
            val playlistId = db.playlistDao().insert(PlaylistEntity(name = "Keep me", createdAt = 100))
            db.playlistTrackDao().insertAll(listOf(PlaylistTrackEntity(playlistId, oldKey, "Same title", "Same artist", "Album", "3:00", 0,
                null, null, id, "YOUTUBE_MUSIC", 100)))
        } finally { before.close() }
        // v15/v16 have identical columns: use the complete real schema with v15's user_version,
        // then let Room run and validate the actual upgrade transaction on reopening.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 15 }
        val after = open()
        try {
            val db = after
            val saved = db.downloadedTrackDao().getByKey(key)!!
            assertEquals(audio.absolutePath, saved.filePath)
            assertNull(db.downloadedTrackDao().getByKey(oldKey))
            assertArrayEquals(originalBytes, File(saved.filePath).readBytes())
            assertEquals(cover, DownloadRepository.localCoverFile(context, key, saved.filePath))
            assertEquals(key, db.likedSongDao().observeAll().first().single().key)
            assertEquals(key, db.playbackHistoryDao().observeEvents().first().single().trackKey)
            assertEquals(5, db.playbackHistoryDao().getByKey(key)!!.playCount)
            assertEquals(key, db.playlistTrackDao().getAll().single().key)
            assertEquals("Keep me", db.playlistDao().observeAll().first().single().name)
            val video = LikedSongEntity("yt:video000001", "Same title", "Same artist", "Album", "3:00", 0, null, null, 200, "video000001", "YOUTUBE_MUSIC")
            db.likedSongDao().like(video)
            assertEquals(2, db.likedSongDao().observeAll().first().size)
            db.likedSongDao().unlike(video.key)
            assertEquals(key, db.likedSongDao().observeAll().first().single().key)
        } finally { after.close() }
        context.deleteDatabase(name)
        audio.delete(); cover.delete()
    }
}
