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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val updatedAt: Long,
    /** [Track.sourceId]/[Track.sourceType], persisted (added in [MIGRATION_3_4]) so a
     * YouTube-sourced download's [Track] can be fully reconstructed - e.g. if its local file is
     * ever missing, it re-resolves via [YouTubeStreamResolver] instead of falling through to
     * mock-catalog resolution. Null for sources that never needed either field. */
    val sourceId: String? = null,
    val sourceType: String? = null
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
    val playedAt: Long,
    /** [Track.sourceId]/[Track.sourceType], persisted (added in [MIGRATION_3_4]) so replaying a
     * YouTube-sourced track from history re-resolves via [YouTubeStreamResolver] instead of
     * falling through to mock-catalog resolution (it has no [streamUrl] of its own). Null for
     * sources that never needed either field. */
    val sourceId: String? = null,
    val sourceType: String? = null
)

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: PlaybackHistoryEntity)
}

/**
 * A track the user has explicitly liked, for Library's real "Liked Songs" tab. Keyed the same way
 * as [DownloadedTrackEntity]/[PlaybackHistoryEntity] (title/artist), so liking is idempotent and
 * agrees on identity with the rest of the app.
 */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val streamUrl: String?,
    val likedAt: Long
)

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY likedAt DESC")
    fun observeAll(): Flow<List<LikedSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE key = :key)")
    fun observeIsLiked(key: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun like(entity: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE key = :key")
    suspend fun unlike(key: String)
}

/** A playlist the user has created, for Library's real "Playlists" tab. There's no "add track to
 * playlist" feature yet, so every playlist starts (and stays) empty. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlaylistEntity): Long
}

/** v3 -> v4: adds [DownloadedTrackEntity.sourceId]/[DownloadedTrackEntity.sourceType] and
 * [PlaybackHistoryEntity.sourceId]/[PlaybackHistoryEntity.sourceType] - both nullable with no
 * default needed beyond SQLite's implicit NULL, so a plain `ADD COLUMN` is enough; existing rows
 * simply get NULL for both (read paths already treat that as "not a YouTube-sourced track"). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN sourceId TEXT")
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN sourceType TEXT")
        db.execSQL("ALTER TABLE playback_history ADD COLUMN sourceId TEXT")
        db.execSQL("ALTER TABLE playback_history ADD COLUMN sourceType TEXT")
    }
}

@Database(
    entities = [
        DownloadedTrackEntity::class,
        PlaybackHistoryEntity::class,
        LikedSongEntity::class,
        PlaylistEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MuseFlowDatabase : RoomDatabase() {
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var instance: MuseFlowDatabase? = null

        fun getInstance(context: Context): MuseFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuseFlowDatabase::class.java,
                    "museflow.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}
