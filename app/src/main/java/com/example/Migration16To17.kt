package com.example

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: all existing recordings, downloads, paths and historical start counts stay. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS listening_sessions (id TEXT NOT NULL, trackKey TEXT NOT NULL, sourceId TEXT, artist TEXT NOT NULL, startedAt INTEGER NOT NULL, listenedMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, outcome TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_sessions_startedAt ON listening_sessions(startedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_sessions_trackKey ON listening_sessions(trackKey)")
    }
}
