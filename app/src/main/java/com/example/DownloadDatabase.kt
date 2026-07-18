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
    val sourceType: String? = null,
    /** How many times this track has actually started playing (added in [MIGRATION_6_7]), for
     * Library's real "My Top 50" tile - ranked by this, not just recency. */
    val playCount: Int = 1
)

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playCount DESC, playedAt DESC LIMIT :limit")
    fun observeTopPlayed(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)
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
    val likedAt: Long,
    /** [Track.sourceId]/[Track.sourceType], persisted (added in [MIGRATION_8_9]) so liking a
     * YouTube-sourced track preserves its video id - without these, [toTrack] had no way to
     * rebuild a playable YouTube track (it has no [streamUrl] of its own), and playback silently
     * fell through to a title/artist search on another provider instead, playing a different
     * recording than the one actually liked. Null for sources that never needed either field. */
    val sourceId: String? = null,
    val sourceType: String? = null
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

/** A playlist the user has created (or imported from an online source), for Library's real
 * "Playlists" tab. [coverImageUrl] is only set for playlists imported from an online source (see
 * [PlaylistRepository.importOnlinePlaylist]) - a user-created playlist has no cover of its own and
 * instead renders a collage from its tracks' art (see `PlaylistCoverArt`). */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val coverImageUrl: String? = null
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlaylistEntity): Long
}

/** One track that's been added to a user's playlist (see [PlaylistEntity]), added in
 * [MIGRATION_7_8]. Keyed by (playlistId, key) rather than an autogenerated id - adding the same
 * track to the same playlist twice just replaces the row (bumping [addedAt]) instead of growing a
 * duplicate. The track's own fields are denormalized here (same pattern as
 * [DownloadedTrackEntity]/[LikedSongEntity]/[PlaybackHistoryEntity]) so a playlist's tracks can be
 * fully reconstructed without a join back to any provider. */
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "key"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val key: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val gradientIndex: Int,
    val imageUrl: String?,
    val streamUrl: String?,
    val sourceId: String? = null,
    val sourceType: String? = null,
    val addedAt: Long
)

@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun observeForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND key = :key")
    suspend fun delete(playlistId: Long, key: String)
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

/** v4 -> v5: adds [HomeShelfCacheEntity]'s table, for Home's offline shelf cache - a brand new
 * table, so a plain `CREATE TABLE` is enough (nothing to backfill; it starts empty and fills in
 * as shelves load normally). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `home_shelf_cache` (" +
                "`shelfTitle` TEXT NOT NULL, `tracksJson` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`shelfTitle`))"
        )
    }
}

/** v5 -> v6: adds [SearchHistoryEntity]'s table, for Search's recent-queries chips - a brand new
 * table, so a plain `CREATE TABLE` is enough. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `search_history` (" +
                "`query` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`query`))"
        )
    }
}

/** v6 -> v7: adds [PlaybackHistoryEntity.playCount], for Library's real "My Top 50" tile - a
 * plain `ADD COLUMN` with a default of 1, so existing rows (each already representing at least
 * one play) start counted correctly rather than at zero. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playback_history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1")
    }
}

/** v7 -> v8: adds [PlaylistEntity.coverImageUrl] (plain `ADD COLUMN`, nullable - existing
 * playlists simply have no cover, same as any newly-created one without an online source) and
 * [PlaylistTrackEntity]'s table (brand new, so a plain `CREATE TABLE` is enough). Together these
 * back the first real "add tracks to a playlist" feature - previously every playlist started (and
 * stayed) permanently empty. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN coverImageUrl TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_tracks` (" +
                "`playlistId` INTEGER NOT NULL, `key` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, `album` TEXT NOT NULL, `duration` TEXT NOT NULL, " +
                "`gradientIndex` INTEGER NOT NULL, `imageUrl` TEXT, `streamUrl` TEXT, " +
                "`sourceId` TEXT, `sourceType` TEXT, `addedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `key`))"
        )
    }
}

/** v8 -> v9: adds [LikedSongEntity.sourceId]/[LikedSongEntity.sourceType] - both nullable, plain
 * `ADD COLUMN`s, same shape as [MIGRATION_3_4] added for [DownloadedTrackEntity]/
 * [PlaybackHistoryEntity]. Fixes a real bug: liking a YouTube-sourced track (which has no
 * [LikedSongEntity.streamUrl] of its own) lost the one thing needed to replay the exact same
 * video - without it, playback fell back to a title/artist search on another provider and played
 * a different recording under the liked track's name/art. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE liked_songs ADD COLUMN sourceId TEXT")
        db.execSQL("ALTER TABLE liked_songs ADD COLUMN sourceType TEXT")
    }
}

@Database(
    entities = [
        DownloadedTrackEntity::class,
        PlaybackHistoryEntity::class,
        LikedSongEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        HomeShelfCacheEntity::class,
        SearchHistoryEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class MuseFlowDatabase : RoomDatabase() {
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun homeShelfCacheDao(): HomeShelfCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var instance: MuseFlowDatabase? = null

        fun getInstance(context: Context): MuseFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuseFlowDatabase::class.java,
                    "museflow.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build().also { instance = it }
            }
    }
}
