package com.example

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/** Data-only migration. Audio paths/files are deliberately untouched: rekeying must never download,
 * rename or discard an existing audio file. Room runs migrations in a transaction. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for ((table, time) in listOf("liked_songs" to "likedAt", "downloaded_tracks" to "updatedAt",
            "playlist_tracks" to "addedAt", "playback_history" to "playedAt")) {
            val records = buildList {
                db.query("SELECT * FROM $table ORDER BY $time ASC").use { rows ->
                    while (rows.moveToNext()) {
                        fun value(column: String): String? = rows.getColumnIndex(column).takeIf { it >= 0 }
                            ?.let { if (rows.isNull(it)) null else rows.getString(it) }
                        val id = value("sourceId")?.takeIf { it.isNotBlank() } ?: continue
                        val key = value("key") ?: continue
                        val target = recordingStorageKey(value("title").orEmpty(), value("artist").orEmpty(), id, value("sourceType"))
                        if (key != target) add(Triple(key, target, value("playlistId")))
                    }
                }
            }
            for ((old, target, playlistId) in records) {
                if (table == "playback_history") {
                    // Merge duplicate metadata rows of one recording, retaining counts and event links.
                    db.execSQL("UPDATE playback_events SET trackKey = ? WHERE trackKey = ?", arrayOf(target, old))
                    val exists = db.query("SELECT 1 FROM playback_history WHERE `key` = ?", arrayOf(target)).use { it.moveToFirst() }
                    if (exists) {
                        db.execSQL("UPDATE playback_history SET playCount = playCount + COALESCE((SELECT playCount FROM playback_history WHERE `key` = ?), 0), playedAt = MAX(playedAt, COALESCE((SELECT playedAt FROM playback_history WHERE `key` = ?), 0)) WHERE `key` = ?", arrayOf(old, old, target))
                        db.execSQL("DELETE FROM playback_history WHERE `key` = ?", arrayOf(old))
                        continue
                    }
                }
                if (table == "downloaded_tracks") {
                    val usableExisting = db.query("SELECT filePath FROM downloaded_tracks WHERE `key` = ? AND status = 'COMPLETED'", arrayOf(target)).use {
                        it.moveToFirst() && File(it.getString(0)).let { file -> file.isFile && file.length() > 0 }
                    }
                    if (usableExisting) {
                        // Duplicate DB identity, not a duplicate recording. Preserve both physical files.
                        db.execSQL("DELETE FROM downloaded_tracks WHERE `key` = ?", arrayOf(old))
                        continue
                    }
                }
                if (table == "playlist_tracks") {
                    db.execSQL("UPDATE OR REPLACE playlist_tracks SET `key` = ? WHERE `key` = ? AND playlistId = ?", arrayOf(target, old, playlistId))
                } else {
                    db.execSQL("UPDATE OR REPLACE $table SET `key` = ? WHERE `key` = ?", arrayOf(target, old))
                }
            }
        }
    }
}
