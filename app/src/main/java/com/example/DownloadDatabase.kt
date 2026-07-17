package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class DownloadStatus { DOWNLOADING, COMPLETED, FAILED }

/**
 * A track downloaded for offline playback. Keyed by [Track.downloadKey] (title/artist) rather
 * than any provider-specific id, since the mock catalog, JioSaavn search results, and this table
 * itself all need to agree on the same identity for a track without sharing a common id field.
 */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val filePath: String,
    val status: String,
    val updatedAt: Long
)

@Dao
interface DownloadedTrackDao {
    @Query("SELECT * FROM downloaded_tracks WHERE status = 'COMPLETED'")
    fun observeCompleted(): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE key = :key")
    suspend fun deleteByKey(key: String)
}

/**
 * One row per distinct track that's actually been played, for Home's real "Recently Played"
 * shelf. Keyed the same way as [DownloadedTrackEntity] (title/artist), so replaying a track
 * updates its [playedAt] via REPLACE rather than growing a duplicate row per play.
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val streamUrl: String?,
    val playedAt: Long
)

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: PlaybackHistoryEntity)
}

@Database(
    entities = [DownloadedTrackEntity::class, PlaybackHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MuseFlowDatabase : RoomDatabase() {
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        @Volatile private var instance: MuseFlowDatabase? = null

        fun getInstance(context: Context): MuseFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuseFlowDatabase::class.java,
                    "museflow.db"
                )
                    // No migration path exists yet for this pre-release app, and history/download
                    // rows are just a local cache - safe to drop and recreate on schema changes.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}
