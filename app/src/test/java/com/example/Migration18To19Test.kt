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
class Migration18To19Test {
    @Test fun addsDurableRequestsWithoutReplacingDownloadsOrSettingsRecovery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "downloads-journal-migration.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, MuseFlowDatabase::class.java, name)
            .addMigrations(MIGRATION_18_19).allowMainThreadQueries().build()
        val before = open()
        val download = DownloadedTrackEntity("local:kept", "Kept", "Artist", "", "", 0, null, "/kept/path", "COMPLETED", 10)
        val settings = SettingsRestoreJournal(token = "settings-still-pending", payload = "{}", createdAt = 10)
        try { before.downloadedTrackDao().upsert(download); before.settingsRestoreJournalDao().insert(settings) }
        finally { before.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE download_requests"); it.version = 18
        }
        val after = open()
        try {
            assertEquals(download, after.downloadedTrackDao().getByKey(download.key))
            assertEquals(settings, after.settingsRestoreJournalDao().pending())
            assertTrue(after.downloadRequestDao().snapshot().isEmpty())
        } finally { after.close(); context.deleteDatabase(name) }
        Unit
    }
}
