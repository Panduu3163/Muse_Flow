package com.example

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_SCHEMA_VERSION = 1

/** `optString(name).takeIf { it.isNotBlank() }` looks right for an "absent or blank means null"
 * field, but is wrong for a field explicitly written as [JSONObject.NULL] (as every nullable field
 * in this file's `toBackupJson()` functions is): [JSONObject.NULL]'s `toString()` is the literal
 * string `"null"`, which is non-blank, so that pattern silently restores the string `"null"`
 * instead of an actual null. [JSONObject.isNull] correctly treats "absent" and "explicitly null"
 * as the same case, matching how these fields are meant to round-trip. */
private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private val Context.backupDataStore by preferencesDataStore(name = "backup_prefs")

private object BackupKeys {
    val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
}

sealed class RestoreResult {
    data class Success(val likedSongsRestored: Int, val playlistsRestored: Int) : RestoreResult()
    data class Failure(val message: String) : RestoreResult()
}

/**
 * Exports/imports the user's actual library data - liked songs and playlists (with their
 * tracks) - as a single portable JSON document. Deliberately excludes downloaded tracks (their
 * local file paths won't be valid on another device or after a reinstall - they'd need
 * re-downloading either way) and playback history/caches (transient, re-derived from normal use,
 * not something losing would actually hurt). Restoring liked songs REPLACEs by key (safe to
 * import the same backup twice); restoring playlists always ADDS new playlists rather than
 * merging into same-named existing ones, since matching playlists by name alone risks silently
 * combining two unrelated ones.
 */
class BackupRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val db by lazy { MuseFlowDatabase.getInstance(appContext) }

    val autoBackupEnabled: Flow<Boolean> =
        appContext.backupDataStore.data.map { it[BackupKeys.AUTO_BACKUP_ENABLED] ?: false }
    val lastBackupAt: Flow<Long?> = appContext.backupDataStore.data.map { it[BackupKeys.LAST_BACKUP_AT] }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        appContext.backupDataStore.edit { it[BackupKeys.AUTO_BACKUP_ENABLED] = enabled }
        if (enabled) AutoBackupWorker.schedule(appContext) else AutoBackupWorker.cancel(appContext)
    }

    suspend fun buildBackupJson(): String {
        val likedSongs = db.likedSongDao().observeAll().first()
        val playlists = db.playlistDao().observeAll().first()

        val root = JSONObject()
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val likedArray = JSONArray()
        likedSongs.forEach { likedArray.put(it.toBackupJson()) }
        root.put("likedSongs", likedArray)

        val playlistsArray = JSONArray()
        for (playlist in playlists) {
            val tracks = db.playlistTrackDao().observeForPlaylist(playlist.id).first()
            val tracksArray = JSONArray()
            tracks.forEach { tracksArray.put(it.toBackupJson()) }

            playlistsArray.put(
                JSONObject().apply {
                    put("name", playlist.name)
                    put("createdAt", playlist.createdAt)
                    put("coverImageUrl", playlist.coverImageUrl ?: JSONObject.NULL)
                    put("tracks", tracksArray)
                }
            )
        }
        root.put("playlists", playlistsArray)

        return root.toString()
    }

    suspend fun writeAutoBackup(): Boolean = runCatching {
        val json = buildBackupJson()
        val dir = java.io.File(appContext.filesDir, "backups").apply { mkdirs() }
        java.io.File(dir, "auto_backup.json").writeText(json)
        appContext.backupDataStore.edit { it[BackupKeys.LAST_BACKUP_AT] = System.currentTimeMillis() }
        true
    }.getOrDefault(false)

    suspend fun restoreFromJson(json: String): RestoreResult {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return RestoreResult.Failure("Not a valid backup file")
        if (root.optInt("schemaVersion", -1) != BACKUP_SCHEMA_VERSION) {
            return RestoreResult.Failure("This backup was made by an incompatible app version")
        }

        var likedCount = 0
        root.optJSONArray("likedSongs")?.let { array ->
            for (i in 0 until array.length()) {
                val entity = array.optJSONObject(i)?.toLikedSongEntity() ?: continue
                db.likedSongDao().like(entity)
                likedCount++
            }
        }

        var playlistCount = 0
        root.optJSONArray("playlists")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val newPlaylistId = db.playlistDao().insert(
                    PlaylistEntity(
                        name = name,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        coverImageUrl = obj.optNullableString("coverImageUrl")
                    )
                )
                val trackEntities = obj.optJSONArray("tracks")?.let { tracksArray ->
                    (0 until tracksArray.length()).mapNotNull { j ->
                        tracksArray.optJSONObject(j)?.toPlaylistTrackEntity(newPlaylistId)
                    }
                } ?: emptyList()
                if (trackEntities.isNotEmpty()) db.playlistTrackDao().insertAll(trackEntities)
                playlistCount++
            }
        }

        return RestoreResult.Success(likedCount, playlistCount)
    }

    companion object {
        @Volatile private var instance: BackupRepository? = null

        fun getInstance(context: Context): BackupRepository =
            instance ?: synchronized(this) {
                instance ?: BackupRepository(context.applicationContext).also { instance = it }
            }
    }
}

// internal (rather than private) so the JSON round-trip contract is directly unit-testable.
internal fun LikedSongEntity.toBackupJson(): JSONObject = JSONObject().apply {
    put("key", key)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("duration", duration)
    put("gradientIndex", gradientIndex)
    put("imageUrl", imageUrl ?: JSONObject.NULL)
    put("streamUrl", streamUrl ?: JSONObject.NULL)
    put("likedAt", likedAt)
    put("sourceId", sourceId ?: JSONObject.NULL)
    put("sourceType", sourceType ?: JSONObject.NULL)
}

internal fun JSONObject.toLikedSongEntity(): LikedSongEntity? {
    val key = optString("key").takeIf { it.isNotBlank() } ?: return null
    return LikedSongEntity(
        key = key,
        title = optString("title"),
        artist = optString("artist"),
        album = optString("album"),
        duration = optString("duration"),
        gradientIndex = optInt("gradientIndex"),
        imageUrl = optNullableString("imageUrl"),
        streamUrl = optNullableString("streamUrl"),
        likedAt = optLong("likedAt", System.currentTimeMillis()),
        sourceId = optNullableString("sourceId"),
        sourceType = optNullableString("sourceType")
    )
}

internal fun PlaylistTrackEntity.toBackupJson(): JSONObject = JSONObject().apply {
    put("key", key)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("duration", duration)
    put("gradientIndex", gradientIndex)
    put("imageUrl", imageUrl ?: JSONObject.NULL)
    put("streamUrl", streamUrl ?: JSONObject.NULL)
    put("sourceId", sourceId ?: JSONObject.NULL)
    put("sourceType", sourceType ?: JSONObject.NULL)
    put("addedAt", addedAt)
}

internal fun JSONObject.toPlaylistTrackEntity(playlistId: Long): PlaylistTrackEntity? {
    val key = optString("key").takeIf { it.isNotBlank() } ?: return null
    return PlaylistTrackEntity(
        playlistId = playlistId,
        key = key,
        title = optString("title"),
        artist = optString("artist"),
        album = optString("album"),
        duration = optString("duration"),
        gradientIndex = optInt("gradientIndex"),
        imageUrl = optNullableString("imageUrl"),
        streamUrl = optNullableString("streamUrl"),
        sourceId = optNullableString("sourceId"),
        sourceType = optNullableString("sourceType"),
        addedAt = optLong("addedAt", System.currentTimeMillis())
    )
}
