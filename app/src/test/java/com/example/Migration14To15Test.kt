package com.example

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration14To15Test {
    @Test fun addsEventLogAndRemotePlaylistIdentityWithoutDroppingLibrary() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
        db.execSQL("INSERT INTO playlists VALUES (1, 'Existing playlist')")
        val support = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") { db.execSQL(args!![0] as String); Unit } else null
        } as SupportSQLiteDatabase
        MIGRATION_14_15.migrate(support)
        db.rawQuery("SELECT name, remoteId FROM playlists WHERE id = 1", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("Existing playlist", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.execSQL("INSERT INTO playback_events (trackKey, playedAt) VALUES ('song', 123)")
        db.rawQuery("SELECT trackKey, playedAt FROM playback_events", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("song", it.getString(0))
            assertEquals(123L, it.getLong(1))
        }
        db.close()
    }
}
