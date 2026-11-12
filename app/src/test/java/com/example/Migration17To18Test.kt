package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration17To18Test {
    @Test fun addsRecoveryJournalWithoutChangingExistingLibraryOrHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "settings-journal-migration.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MuseFlowDatabase::class.java, name)
            .addMigrations(MIGRATION_17_18, MIGRATION_18_19).allowMainThreadQueries().build()
        val liked = LikedSongEntity("local:a", "Kept", "Artist", "", "", 0, null, null, 10)
        val history = PlaybackHistoryEntity("local:a", "Kept", "Artist", "", "", 0, null, null, 20, playCount = 7)
        val before = open()
        try { before.likedSongDao().like(liked); before.playbackHistoryDao().upsert(history) }
        finally { before.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE settings_restore_journal"); it.version = 17
        }
        val db = open()
        try {
            assertEquals(listOf(liked), db.likedSongDao().backupSnapshot())
            assertEquals(listOf(history), db.playbackHistoryDao().backupHistory())
            assertNull(db.settingsRestoreJournalDao().pending())
            db.settingsRestoreJournalDao().insert(SettingsRestoreJournal(token = "check", payload = "{}", createdAt = 100))
            assertEquals("check", db.settingsRestoreJournalDao().pending()!!.token)
        } finally { db.close() }
        context.deleteDatabase(name)
        Unit
    }
}
