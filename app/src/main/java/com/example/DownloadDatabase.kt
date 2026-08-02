package com.example

import android.content.Context
import androidx.compose.runtime.Immutable
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
    val sourceType: String? = null,
    /** [Track.albumId]/[Track.artistId], persisted (added in [MIGRATION_11_12]) so "View
     * album"/"View artist" still works on a downloaded track, not just a fresh search result. */
    val albumId: String? = null,
    val artistId: String? = null,
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

    /** For Storage settings' "Clear downloads" - the row side of the wipe, the actual files are
     * deleted separately by [DownloadRepository.deleteAllDownloads]. */
    @Query("DELETE FROM downloaded_tracks")
    suspend fun clearAll()
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
    val playCount: Int = 1,
    /** [Track.albumId]/[Track.artistId], persisted (added in [MIGRATION_11_12]) so "View
     * album"/"View artist" still works from History, not just a fresh search result. */
    val albumId: String? = null,
    val artistId: String? = null,
)

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playCount DESC, playedAt DESC LIMIT :limit")
    fun observeTopPlayed(limit: Int): Flow<List<PlaybackHistoryEntity>>

    /** Unbounded, for the History screen - the limited queries above back Home's shelf and
     * Library's tiles, which deliberately show only a slice. */
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()
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
    val sourceType: String? = null,
    /** [Track.albumId]/[Track.artistId], persisted (added in [MIGRATION_11_12]) so "View
     * album"/"View artist" still works on a liked track, not just a fresh search result. */
    val albumId: String? = null,
    val artistId: String? = null,
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

    /** For [LikedSongsRepository]'s one-time artistId/albumId backfill - a like made before that
     * plumbing was wired correctly has these permanently null otherwise, since nothing else ever
     * rewrites an existing row's these two fields. */
    @Query("UPDATE liked_songs SET artistId = :artistId, albumId = :albumId WHERE key = :key")
    suspend fun updateArtistAndAlbumId(key: String, artistId: String?, albumId: String?)
}

/** A playlist the user has created (or imported from an online source), for Library's real
 * "Playlists" tab. [coverImageUrl] is only set for playlists imported from an online source (see
 * [PlaylistRepository.importOnlinePlaylist]) - a user-created playlist has no cover of its own and
 * instead renders a collage from its tracks' art (see `PlaylistCoverArt`). [customCoverUri] is a
 * separate field, not a repurposing of [coverImageUrl]: a user explicitly picking a cover (see
 * [PlaylistRepository.setCustomCover]) should win over the auto mosaic, where an *imported*
 * playlist's own incidental cover deliberately doesn't (see `PlaylistGridCover`'s fallback order). */
@Immutable
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val coverImageUrl: String? = null,
    /** Added in [MIGRATION_10_11]. Pinned playlists sort first in Library regardless of the
     * chosen sort option - the one ordering a sort option can't express. */
    val isPinned: Boolean = false,
    /** Added in [MIGRATION_13_14]. A `content://` URI from the system picker, persisted with a
     * read permission grant (see `PlaylistDetailScreen`'s cover picker) so it survives reboot. */
    val customCoverUri: String? = null,
)

@Dao
interface PlaylistDao {
    // Pinned first, then whatever the table's own creation order already gave: pin is an override
    // on top of sort, not a sort option of its own, so it applies before every other ordering this
    // query feeds into.
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE playlists SET customCoverUri = :uri WHERE id = :id")
    suspend fun setCustomCoverUri(id: Long, uri: String?)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
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
    val addedAt: Long,
    /** [Track.albumId]/[Track.artistId], persisted (added in [MIGRATION_11_12]) so "View
     * album"/"View artist" still works on a playlist track, not just a fresh search result. */
    val albumId: String? = null,
    val artistId: String? = null,
)

@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun observeForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND key = :key")
    suspend fun delete(playlistId: Long, key: String)

    /** Companion to [PlaylistDao.delete] - a playlist row disappearing does not cascade to its
     * tracks (no foreign key is declared), so deleting a playlist without this leaves its tracks
     * orphaned in the table forever. */
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllForPlaylist(playlistId: Long)

    /** One-shot (not observed), across every playlist - only [PlaylistRepository]'s one-time
     * artistId/albumId backfill needs the whole table at once. */
    @Query("SELECT * FROM playlist_tracks")
    suspend fun getAll(): List<PlaylistTrackEntity>

    /** By key alone (not scoped to one playlistId) - the same track can be duplicated across
     * several playlists sharing the same key, and a backfilled artistId/albumId is correct for
     * every one of them at once. */
    @Query("UPDATE playlist_tracks SET artistId = :artistId, albumId = :albumId WHERE key = :key")
    suspend fun updateArtistAndAlbumId(key: String, artistId: String?, albumId: String?)
}

/** A followed artist (see [FollowedArtistsRepository]) - [knownTrackIds] is the comma-joined
 * baseline [ArtistReleaseCheckWorker] diffs a fresh tracklist fetch against (see
 * [newReleaseTrackIds]) to detect a genuinely new release, empty until the first successful
 * check. */
@Immutable
@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val artistId: String,
    val name: String,
    val imageUrl: String?,
    val sourceType: String,
    val followedAt: Long,
    val knownTrackIds: String = ""
)

@Dao
interface FollowedArtistDao {
    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun observeAll(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT * FROM followed_artists")
    suspend fun getAll(): List<FollowedArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE artistId = :artistId")
    suspend fun unfollow(artistId: String)

    @Query("UPDATE followed_artists SET knownTrackIds = :knownTrackIds WHERE artistId = :artistId")
    suspend fun updateKnownTrackIds(artistId: String, knownTrackIds: String)
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

/** v9 -> v10: adds [FollowedArtistEntity]'s table, for follow-artist new-release notifications - a
 * brand new table, so a plain `CREATE TABLE` is enough. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `followed_artists` (" +
                "`artistId` TEXT NOT NULL, `name` TEXT NOT NULL, `imageUrl` TEXT, " +
                "`sourceType` TEXT NOT NULL, `followedAt` INTEGER NOT NULL, " +
                "`knownTrackIds` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`artistId`))"
        )
    }
}

/** v10 -> v11: adds [PlaylistEntity.isPinned] (plain `ADD COLUMN`, defaulted so every existing
 * playlist starts unpinned) - backs the playlist actions sheet's "Pin playlist". */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

/** v11 -> v12: adds `albumId`/`artistId` (plain nullable `ADD COLUMN`s, same shape as every prior
 * source-id migration) to every table that stores a track outside a fresh search result -
 * [PlaylistTrackEntity], [LikedSongEntity], [DownloadedTrackEntity], [PlaybackHistoryEntity].
 * Backs "View artist"/"View album" on the track actions sheet: without this, only a track fetched
 * moments ago from search/an album/artist/playlist browse carried a browseId to navigate with - a
 * liked, downloaded, playlisted, or history track lost it the moment it was saved, since none of
 * these tables had anywhere to put it. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN albumId TEXT")
        db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN artistId TEXT")
        db.execSQL("ALTER TABLE liked_songs ADD COLUMN albumId TEXT")
        db.execSQL("ALTER TABLE liked_songs ADD COLUMN artistId TEXT")
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN albumId TEXT")
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN artistId TEXT")
        db.execSQL("ALTER TABLE playback_history ADD COLUMN albumId TEXT")
        db.execSQL("ALTER TABLE playback_history ADD COLUMN artistId TEXT")
    }
}

/** v12 -> v13: adds [ArtistPageCacheEntity]'s and [AlbumPageCacheEntity]'s tables, for the
 * Artist/Album screens' stale-while-revalidate cache - both brand new tables, so plain
 * `CREATE TABLE`s are enough (nothing to backfill; they start empty and fill in as pages load). */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `artist_page_cache` (" +
                "`artistId` TEXT NOT NULL, `tracklistJson` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`artistId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_page_cache` (" +
                "`albumId` TEXT NOT NULL, `detailsJson` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`albumId`))"
        )
    }
}

/** v13 -> v14: adds [PlaylistEntity.customCoverUri] for Library's custom playlist thumbnail
 * picker - a fresh nullable column, so a plain `ADD COLUMN` needs no backfill. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN customCoverUri TEXT")
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
        SearchHistoryEntity::class,
        FollowedArtistEntity::class,
        ArtistPageCacheEntity::class,
        AlbumPageCacheEntity::class
    ],
    version = 14,
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
    abstract fun followedArtistDao(): FollowedArtistDao
    abstract fun artistPageCacheDao(): ArtistPageCacheDao
    abstract fun albumPageCacheDao(): AlbumPageCacheDao

    companion object {
        @Volatile private var instance: MuseFlowDatabase? = null

        fun getInstance(context: Context): MuseFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuseFlowDatabase::class.java,
                    "museflow.db"
                )
                    .addMigrations(
                        MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_12_13, MIGRATION_13_14
                    )
                    .build().also { instance = it }
            }
    }
}
