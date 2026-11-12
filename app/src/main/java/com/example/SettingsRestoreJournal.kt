package com.example

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "settings_restore_journal")
data class SettingsRestoreJournal(
    @PrimaryKey val id: Int = 1,
    val token: String,
    val payload: String,
    val completedStores: String = "",
    val createdAt: Long,
)

@Dao
interface SettingsRestoreJournalDao {
    @Query("SELECT * FROM settings_restore_journal WHERE id = 1")
    suspend fun pending(): SettingsRestoreJournal?

    @Query("SELECT EXISTS(SELECT 1 FROM settings_restore_journal WHERE id = 1)")
    fun observePending(): Flow<Boolean>

    @Insert
    suspend fun insert(journal: SettingsRestoreJournal)

    @Query("UPDATE settings_restore_journal SET completedStores = :completed WHERE id = 1 AND token = :token")
    suspend fun checkpoint(token: String, completed: String): Int

    @Query("DELETE FROM settings_restore_journal WHERE id = 1 AND token = :token")
    suspend fun complete(token: String): Int
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS settings_restore_journal (id INTEGER NOT NULL, token TEXT NOT NULL, payload TEXT NOT NULL, completedStores TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}
