package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration16To17Test {
    @Test fun additiveUpgradeKeepsHistoryAndStartsWithNoInventedListening() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "listening-migration.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MuseFlowDatabase::class.java, name)
            .addMigrations(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19).allowMainThreadQueries().build()
        val before = open()
        try {
            val db = before
            db.playbackHistoryDao().upsert(PlaybackHistoryEntity("yt:audio000001", "Song", "Artist", "", "3:00", 0,
                null, null, 100, playCount = 50))
            db.playbackHistoryDao().insertEvent(PlaybackEventEntity(trackKey = "yt:audio000001", playedAt = 100))
        } finally { before.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE listening_sessions")
            it.version = 16
        }
        val after = open()
        try {
            val db = after
            val dao = db.playbackHistoryDao()
            assertEquals(50, dao.getByKey("yt:audio000001")!!.playCount)
            assertEquals(1, dao.observeEvents().first().size)
            assertTrue(dao.observeListeningSessions().first().isEmpty())
            val session = ListeningSessionEntity("session", "yt:audio000001", "audio000001", "Artist", 100, 30_000, 180_000, "OPEN")
            dao.saveListeningSession(session)
            dao.saveListeningSession(session.copy(listenedMs = 60_000, outcome = "ENDED"))
            assertEquals(60_000, dao.observeListeningSessions().first().single().listenedMs)
            dao.deleteListeningSessions(session.trackKey)
            assertTrue(dao.observeListeningSessions().first().isEmpty())
        } finally { after.close() }
        context.deleteDatabase(name)
    }
}
