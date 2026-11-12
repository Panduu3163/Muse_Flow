package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val BACKUP_SCHEMA_VERSION = 4

data class BackupRestorePreview(
    val newLikedSongs: Int,
    val existingLikedSongs: Int,
    val newPlaylists: Int,
    val skippedPlaylists: Int,
    val newArtists: Int,
    val skippedArtists: Int,
    val includesArtists: Boolean,
    val fileName: String = "",
    val includesHistory: Boolean = false,
    val newHistoryTracks: Int = 0,
    val existingHistoryTracks: Int = 0,
    val newHistoryEvents: Int = 0,
    val skippedHistoryEvents: Int = 0,
    val newListeningSessions: Int = 0,
    val updatedListeningSessions: Int = 0,
    val skippedListeningSessions: Int = 0,
    val includesSettings: Boolean = false,
    val settingsCount: Int = 0,
)

internal data class PreparedBackupRestore(val document: BackupDocument, val preview: BackupRestorePreview)

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
    val INCLUDE_HISTORY = booleanPreferencesKey("include_history")
    val INCLUDE_SETTINGS = booleanPreferencesKey("include_settings")
    val DESTINATION_URI = stringPreferencesKey("destination_uri")
    val DESTINATION_LABEL = stringPreferencesKey("destination_label")
    val LAST_EXTERNAL_BACKUP_AT = longPreferencesKey("last_external_backup_at")
    val EXTERNAL_ERROR = stringPreferencesKey("external_error")
}

sealed class RestoreResult {
    data class Success(
        val likedSongsRestored: Int,
        val playlistsRestored: Int,
        /** Playlists already present with identical contents, so not created again. */
        val playlistsSkipped: Int = 0,
        val artistsRestored: Int = 0,
        val artistsSkipped: Int = 0,
        val historyTracksMerged: Int = 0,
        val historyEventsAdded: Int = 0,
        val listeningSessionsMerged: Int = 0,
        val settingsRestored: Boolean = false,
        val settingsPending: Boolean = false,
    ) : RestoreResult()

    data class Failure(val message: String) : RestoreResult()
}

/**
 * Exports/imports the user's actual library data - liked songs and playlists (with their
 * tracks), pins and followed artists as a single portable JSON document. Deliberately excludes downloaded tracks (their
 * local file paths won't be valid on another device or after a reinstall - they'd need
 * re-downloading either way) and custom cover files. History and portable settings are explicit opt-ins.
 * Restoring liked songs
 * REPLACEs by key (safe to import the same backup twice); restoring playlists ADDS new playlists rather than
 * merging into same-named existing ones, since matching playlists by name alone risks silently
 * combining two unrelated ones.
 */
class BackupRepository internal constructor(
    context: Context,
    private val db: MuseFlowDatabase = MuseFlowDatabase.getInstance(context.applicationContext),
    private val portableSettings: PortableSettingsAccess = DataStorePortableSettings(context),
    private val externalDestination: ExternalBackupDestination = SafBackupDestination(context),
) {
    private val appContext = context.applicationContext
    private val autoBackupMutex = Mutex()
    private val settingsRestoreMutex = Mutex()
    private val _settingsRestoreWorking = MutableStateFlow(false)
    val settingsRestoreWorking = _settingsRestoreWorking.asStateFlow()

    val autoBackupEnabled: Flow<Boolean> =
        appContext.backupDataStore.data.map { it[BackupKeys.AUTO_BACKUP_ENABLED] ?: false }
    val lastBackupAt: Flow<Long?> = appContext.backupDataStore.data.map { it[BackupKeys.LAST_BACKUP_AT] }
    val includeHistoryEnabled: Flow<Boolean> = appContext.backupDataStore.data.map { it[BackupKeys.INCLUDE_HISTORY] ?: false }
    val includeSettingsEnabled: Flow<Boolean> = appContext.backupDataStore.data.map { it[BackupKeys.INCLUDE_SETTINGS] ?: false }
    val pendingSettingsRestore: Flow<Boolean> = db.settingsRestoreJournalDao().observePending().catch { emit(true) }
    val automaticDestination: Flow<AutomaticBackupDestinationState> = appContext.backupDataStore.data.map { prefs ->
        AutomaticBackupDestinationState(prefs[BackupKeys.DESTINATION_URI], prefs[BackupKeys.DESTINATION_LABEL],
            prefs[BackupKeys.LAST_EXTERNAL_BACKUP_AT], prefs[BackupKeys.EXTERNAL_ERROR])
    }
    private fun internalBackupFile() = java.io.File(appContext.filesDir, "backups/auto_backup.json")
    private val _hasInternalBackup = MutableStateFlow(internalBackupFile().let { it.isFile || java.io.File(it.path + ".bak").isFile })
    val hasInternalBackup = _hasInternalBackup.asStateFlow()
    private val _hasPreviousInternalBackup = MutableStateFlow(java.io.File(appContext.filesDir, "backups/previous_backup.json").isFile)
    val hasPreviousInternalBackup = _hasPreviousInternalBackup.asStateFlow()

    suspend fun setAutomaticDestination(tree: Uri?) = autoBackupMutex.withLock {
        val previous = automaticDestination.first().treeUri?.let(Uri::parse)
        try {
            val label = tree?.let { externalDestination.retain(it) }
            appContext.backupDataStore.edit { prefs ->
                if (tree == null) { prefs.remove(BackupKeys.DESTINATION_URI); prefs.remove(BackupKeys.DESTINATION_LABEL) }
                else { prefs[BackupKeys.DESTINATION_URI] = tree.toString(); prefs[BackupKeys.DESTINATION_LABEL] = label!! }
                if (tree != previous) prefs.remove(BackupKeys.LAST_EXTERNAL_BACKUP_AT)
                prefs.remove(BackupKeys.EXTERNAL_ERROR)
            }
        } catch (error: Exception) {
            if (tree != null && tree != previous) runCatching { externalDestination.release(tree) }
            throw error
        }
        if (previous != null && previous != tree) runCatching { externalDestination.release(previous) }
    }

    suspend fun readInternalBackup(previous: Boolean = false): String = autoBackupMutex.withLock {
        withContext(Dispatchers.IO) {
            val source = if (previous) java.io.File(appContext.filesDir, "backups/previous_backup.json") else internalBackupFile()
            readAtomicFile(source).use { readBackupDocument(it) }.also { parseBackupDocument(it) }
        }
    }

    suspend fun setIncludeSettingsEnabled(enabled: Boolean) {
        appContext.backupDataStore.edit { it[BackupKeys.INCLUDE_SETTINGS] = enabled }
    }

    /** Replays only settings, never the already-committed library import. */
    suspend fun recoverPendingSettings(): Boolean = settingsRestoreMutex.withLock { applyPendingSettingsLocked() }

    private suspend fun applyPendingSettingsLocked(): Boolean = PortableSettingsLocks.writes.withLock {
        _settingsRestoreWorking.value = true
        try {
        val dao = db.settingsRestoreJournalDao()
        val journal = dao.pending()
        if (journal != null) {
            val settings = PortableSettingsFormat.parse(JSONObject(journal.payload))
            val completed = journal.completedStores.split(',').filter { it.isNotBlank() }.toMutableSet()
            require(completed.all { it in settings.stores })
            settings.stores.forEach { (store, values) ->
                if (store !in completed) {
                    portableSettings.apply(store, values)
                    completed.add(store)
                    check(dao.checkpoint(journal.token, completed.joinToString(",")) == 1)
                }
            }
            check(dao.complete(journal.token) == 1)
        }
        true
        } catch (cancelled: CancellationException) {
        throw cancelled
        } catch (error: Exception) {
        Log.w("BackupRepository", "Settings restore remains pending", error)
            false
        } finally { _settingsRestoreWorking.value = false }
    }

    /** Explicit user action only: retain library and any settings already applied. */
    suspend fun keepCurrentSettings(): Boolean = settingsRestoreMutex.withLock {
        try {
            val dao = db.settingsRestoreJournalDao()
            dao.pending()?.let { check(dao.complete(it.token) == 1) }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    suspend fun setIncludeHistoryEnabled(enabled: Boolean) {
        appContext.backupDataStore.edit { it[BackupKeys.INCLUDE_HISTORY] = enabled }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        appContext.backupDataStore.edit { it[BackupKeys.AUTO_BACKUP_ENABLED] = enabled }
        if (enabled) AutoBackupWorker.schedule(appContext) else AutoBackupWorker.cancel(appContext)
    }

    suspend fun buildBackupJson(includeHistory: Boolean? = null, includeSettings: Boolean? = null): String = settingsRestoreMutex.withLock {
        check(applyPendingSettingsLocked()) { "Finish the pending settings restore before making another backup" }
        val historyIncluded = includeHistory ?: includeHistoryEnabled.first()
        val settings = if (includeSettings ?: includeSettingsEnabled.first()) portableSettings.snapshot() else null
        db.withTransaction {
            val likedSongs = db.likedSongDao().backupSnapshot()
            val playlists = db.playlistDao().backupSnapshot()

            val root = JSONObject()
            root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("historyIncluded", historyIncluded)
            root.put("settingsIncluded", settings != null)
            settings?.let { root.put("settings", it.toJson()) }
            if (historyIncluded) {
                val history = db.playbackHistoryDao()
                root.putBackupHistory(BackupHistory(history.backupHistory(), history.backupEvents(), history.backupSessions()))
            }

            val likedArray = JSONArray()
            likedSongs.forEach { likedArray.put(it.toBackupJson()) }
            root.put("likedSongs", likedArray)

            val playlistsArray = JSONArray()
            for (playlist in playlists) {
                val tracks = db.playlistTrackDao().backupSnapshot(playlist.id)
                val tracksArray = JSONArray()
                tracks.forEach { tracksArray.put(it.toBackupJson()) }

                playlistsArray.put(
                    JSONObject().apply {
                        put("name", playlist.name)
                        put("createdAt", playlist.createdAt)
                        put("coverImageUrl", playlist.coverImageUrl ?: JSONObject.NULL)
                        put("remoteId", playlist.remoteId ?: JSONObject.NULL)
                        put("isPinned", playlist.isPinned)
                        put("tracks", tracksArray)
                    }
                )
            }
            root.put("playlists", playlistsArray)
            root.put("followedArtists", JSONArray().apply {
                db.followedArtistDao().getAll().sortedBy { it.artistId }.forEach { artist ->
                    put(JSONObject().apply {
                        put("artistId", artist.artistId)
                        put("name", artist.name)
                        put("imageUrl", artist.imageUrl ?: JSONObject.NULL)
                        put("sourceType", artist.sourceType)
                        put("followedAt", artist.followedAt)
                    })
                }
            })

            // A successful export must also satisfy the import limits and field contract.
            root.toString().also { parseBackupDocument(it) }
        }
    }

    suspend fun writeAutoBackup(): Boolean = autoBackupMutex.withLock { writeAutoBackupLocked() }

    private suspend fun writeAutoBackupLocked(): Boolean = try {
        val json = buildBackupJson()
        withContext(Dispatchers.IO) {
            val dir = java.io.File(appContext.filesDir, "backups").apply { mkdirs() }
            val file = internalBackupFile()
            // Keep one verified older internal generation. Failure leaves the current copy intact.
            if (file.exists() || java.io.File(file.path + ".bak").exists()) {
                val previous = readAtomicFile(file).use { readBackupDocument(it) }
                if (runCatching { parseBackupDocument(previous) }.isSuccess) {
                    writeAtomicBackup(java.io.File(dir, "previous_backup.json"), previous)
                    _hasPreviousInternalBackup.value = true
                }
            }
            writeAtomicBackup(file, json)
        }
        _hasInternalBackup.value = true
        appContext.backupDataStore.edit { it[BackupKeys.LAST_BACKUP_AT] = System.currentTimeMillis() }
        val tree = automaticDestination.first().treeUri?.let(Uri::parse)
        if (tree != null) {
            try {
                externalDestination.writeVerified(tree, json)
                appContext.backupDataStore.edit {
                    it[BackupKeys.LAST_EXTERNAL_BACKUP_AT] = System.currentTimeMillis()
                    it.remove(BackupKeys.EXTERNAL_ERROR)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                appContext.backupDataStore.edit {
                    it[BackupKeys.EXTERNAL_ERROR] = "The external copy could not be verified. Your internal copy is saved. Reconnect storage or select the folder again, then retry."
                }
                return false
            }
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun writeAtomicBackup(file: java.io.File, json: String) {
        writeAtomicFile(file, json.toByteArray(Charsets.UTF_8))
        check(readAtomicFile(file).use { readBackupDocument(it) } == json) { "The internal backup could not be verified" }
    }

    suspend fun restoreFromJson(json: String): RestoreResult {
        val document = try {
            withContext(Dispatchers.Default) { parseBackupDocument(json) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return RestoreResult.Failure("Invalid or unsupported backup. Your library was not changed.")
        }
        return restorePrepared(document)
    }

    internal suspend fun prepareRestore(json: String): PreparedBackupRestore {
        val document = withContext(Dispatchers.Default) { parseBackupDocument(json) }
        val preview = db.withTransaction {
            check(db.settingsRestoreJournalDao().pending() == null) { "A settings restore is still pending" }
            val likedKeys = db.likedSongDao().backupSnapshot().map { it.key }.toSet()
            val playlistKeys = playlistIdentities()
            val newPlaylists = document.playlists.count { playlistKeys.add(it.playlist.name to it.tracks.map { track -> track.key }) }
            val artistKeys = db.followedArtistDao().getAll().map { it.artistId }.toSet()
            val newLikes = document.likes.count { it.key !in likedKeys }
            val newArtists = document.artists.count { it.artistId !in artistKeys }
            val history = historyPlan(document)
            BackupRestorePreview(newLikes, document.likes.size - newLikes, newPlaylists,
                document.playlists.size - newPlaylists, newArtists, document.artists.size - newArtists, document.includesArtists)
                .copy(includesHistory = history != null,
                    newHistoryTracks = history?.newTracks ?: 0, existingHistoryTracks = history?.existingTracks ?: 0,
                    newHistoryEvents = history?.events?.size ?: 0, skippedHistoryEvents = history?.skippedEvents ?: 0,
                    newListeningSessions = history?.newSessions ?: 0, updatedListeningSessions = history?.updatedSessions ?: 0,
                    skippedListeningSessions = history?.skippedSessions ?: 0,
                    includesSettings = document.settings != null,
                    settingsCount = document.settings?.stores?.values?.sumOf { it.size } ?: 0)
        }
        return PreparedBackupRestore(document, preview)
    }

    private suspend fun playlistIdentities() = db.playlistDao().backupSnapshot().map { playlist ->
        playlist.name to db.playlistTrackDao().backupSnapshot(playlist.id).map { it.key }
    }.toMutableSet()

    private suspend fun historyPlan(document: BackupDocument): BackupHistoryPlan? = document.history?.let { incoming ->
        val dao = db.playbackHistoryDao()
        planBackupHistory(incoming, dao.backupHistory(), dao.backupEvents(), dao.backupSessions())
    }

    internal suspend fun restorePrepared(document: BackupDocument): RestoreResult = settingsRestoreMutex.withLock {
        _settingsRestoreWorking.value = document.settings != null
        try {
        val result = try {
            db.withTransaction {
                check(db.settingsRestoreJournalDao().pending() == null) { "A settings restore is still pending" }
                // Transaction ownership serializes the snapshot, duplicate checks and writes,
                // including two concurrent restore requests. Include new entries in this set.
                val existing = playlistIdentities()
                val history = historyPlan(document)
                document.likes.forEach { db.likedSongDao().like(it) }
                var restored = 0
                var skipped = 0
                document.playlists.forEach { backup ->
                    val identity = backup.playlist.name to backup.tracks.map { it.key }
                    if (existing.add(identity)) {
                        val id = db.playlistDao().insert(backup.playlist)
                        db.playlistTrackDao().insertAll(backup.tracks.map { it.copy(playlistId = id) })
                        restored++
                    } else {
                        skipped++
                    }
                }
                val artistKeys = db.followedArtistDao().getAll().map { it.artistId }.toMutableSet()
                var restoredArtists = 0
                document.artists.forEach { artist ->
                    if (artistKeys.add(artist.artistId)) {
                        db.followedArtistDao().upsert(artist)
                        restoredArtists++
                    }
                }
                if (history != null) {
                    val dao = db.playbackHistoryDao()
                    history.tracks.forEach { dao.upsert(it) }
                    history.events.forEach { dao.insertEvent(it) }
                    history.sessions.forEach { dao.saveListeningSession(it) }
                    dao.trimListeningSessions()
                }
                document.settings?.let { settings ->
                    db.settingsRestoreJournalDao().insert(SettingsRestoreJournal(
                        token = UUID.randomUUID().toString(), payload = settings.toJson().toString(), createdAt = System.currentTimeMillis()))
                }
                RestoreResult.Success(document.likes.size, restored, skipped, restoredArtists,
                    document.artists.size - restoredArtists, history?.tracks?.size ?: 0,
                    history?.events?.size ?: 0, history?.sessions?.size ?: 0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RestoreResult.Failure("Restore failed. Your library was not changed.")
        }
        if (result is RestoreResult.Success && result.artistsRestored > 0) {
            // Scheduling is outside the DB transaction. Startup also re-arms this worker, so a
            // scheduler failure must not misreport a committed library restore as rolled back.
            try {
                ArtistReleaseCheckWorker.schedule(appContext)
            } catch (error: Exception) {
                Log.w("BackupRepository", "Artist checks will be scheduled on the next launch", error)
            }
        }
        if (result is RestoreResult.Success && document.settings != null) {
            val restored = applyPendingSettingsLocked()
            result.copy(settingsRestored = restored, settingsPending = !restored)
        } else result
        } finally { _settingsRestoreWorking.value = false }
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
    // Playback addresses can expire or contain signed credentials. Resolve again from identity.
    put("streamUrl", JSONObject.NULL)
    put("likedAt", likedAt)
    put("sourceId", sourceId ?: JSONObject.NULL)
    put("sourceType", sourceType ?: JSONObject.NULL)
    put("albumId", albumId ?: JSONObject.NULL)
    put("artistId", artistId ?: JSONObject.NULL)
}

internal fun JSONObject.toLikedSongEntity(): LikedSongEntity? {
    val legacyKey = optString("key").takeIf { it.isNotBlank() } ?: return null
    val key = if (!optNullableString("sourceId").isNullOrBlank()) {
        recordingStorageKey(optString("title"), optString("artist"), optNullableString("sourceId"), optNullableString("sourceType"))
    } else legacyKey
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
        sourceType = optNullableString("sourceType"),
        albumId = optNullableString("albumId"),
        artistId = optNullableString("artistId"),
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
    put("streamUrl", JSONObject.NULL)
    put("sourceId", sourceId ?: JSONObject.NULL)
    put("sourceType", sourceType ?: JSONObject.NULL)
    put("addedAt", addedAt)
    put("albumId", albumId ?: JSONObject.NULL)
    put("artistId", artistId ?: JSONObject.NULL)
}

internal fun JSONObject.toPlaylistTrackEntity(playlistId: Long): PlaylistTrackEntity? {
    val legacyKey = optString("key").takeIf { it.isNotBlank() } ?: return null
    val key = if (!optNullableString("sourceId").isNullOrBlank()) {
        recordingStorageKey(optString("title"), optString("artist"), optNullableString("sourceId"), optNullableString("sourceType"))
    } else legacyKey
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
        addedAt = optLong("addedAt", System.currentTimeMillis()),
        albumId = optNullableString("albumId"),
        artistId = optNullableString("artistId"),
    )
}
