package com.example

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

enum class DownloadRequestState {
    QUEUED, RESOLVING, DOWNLOADING, VERIFYING, COMPLETED, FAILED, CANCELLED, INTERRUPTED, MISSING;
    val active: Boolean get() = this in setOf(QUEUED, RESOLVING, DOWNLOADING, VERIFYING)
    val retryable: Boolean get() = this in setOf(FAILED, CANCELLED, INTERRUPTED, MISSING)
}

/** Intent and transfer outcomes, independent of the completed-file index. Never store stream URLs. */
@Entity(tableName = "download_requests")
data class DownloadRequestEntity(
    @PrimaryKey val key: String,
    val title: String, val artist: String, val album: String, val duration: String, val gradientIndex: Int,
    val imageUrl: String?, val sourceId: String?, val sourceType: String?, val albumId: String?, val artistId: String?,
    val state: String, val attempt: Int, val token: String, val downloadedBytes: Long = 0,
    val totalBytes: Long? = null, val sha256: String? = null, val error: String? = null, val updatedAt: Long,
) {
    fun toTrack() = Track(title, artist, album, duration, "", gradientIndex, imageUrl,
        sourceId = sourceId, sourceType = MusicSource.entries.firstOrNull { it.name == sourceType }, albumId = albumId, artistId = artistId)
    val requestState: DownloadRequestState get() = DownloadRequestState.entries.firstOrNull { it.name == state } ?: DownloadRequestState.INTERRUPTED
}

@Dao
interface DownloadRequestDao {
    @Query("SELECT * FROM download_requests ORDER BY updatedAt DESC, `key`")
    fun observeAll(): Flow<List<DownloadRequestEntity>>
    @Query("SELECT * FROM download_requests") suspend fun snapshot(): List<DownloadRequestEntity>
    @Query("SELECT * FROM download_requests WHERE `key` = :key") suspend fun get(key: String): DownloadRequestEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(request: DownloadRequestEntity)
    @Query("""UPDATE download_requests SET state=:state, downloadedBytes=:bytes, totalBytes=:total,
        sha256=:sha, error=:error, attempt=:attempt, updatedAt=:at WHERE `key`=:key AND token=:token""")
    suspend fun checkpoint(key: String, token: String, state: String, bytes: Long, total: Long?, sha: String?, error: String?, attempt: Int, at: Long): Int
    @Query("DELETE FROM download_requests WHERE `key`=:key") suspend fun delete(key: String)
    @Query("DELETE FROM download_requests") suspend fun clear()
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS download_requests (`key` TEXT NOT NULL, title TEXT NOT NULL,
            artist TEXT NOT NULL, album TEXT NOT NULL, duration TEXT NOT NULL, gradientIndex INTEGER NOT NULL,
            imageUrl TEXT, sourceId TEXT, sourceType TEXT, albumId TEXT, artistId TEXT, state TEXT NOT NULL,
            attempt INTEGER NOT NULL, token TEXT NOT NULL, downloadedBytes INTEGER NOT NULL, totalBytes INTEGER,
            sha256 TEXT, error TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(`key`))""")
    }
}

internal class DownloadFiles(context: Context) {
    val directory = File(context.applicationContext.filesDir, "downloads")
    fun file(key: String, extension: String): File {
        require(extension in setOf("part", "audio", "cover"))
        return File(directory, "${MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).hex()}.$extension")
    }
    fun owns(file: File): Boolean = file.canonicalPath.startsWith(directory.canonicalPath + File.separator)
    fun delete(file: File) { check(owns(file)); check(!file.exists() || file.delete()) { "Could not remove a download file" } }
    fun deletePartial(key: String) {
        val partial = file(key, "part")
        listOf(partial, File(partial.path + ".mp3"), File(partial.path + ".m4a"),
            File(partial.path + ".bak"), File(partial.path + ".new")).forEach(::delete)
    }
    suspend fun hash(file: File): String {
        check(owns(file) && file.isFile)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}

/** All recovery is local. Reconciliation never schedules a service or resolves/downloads a URL. */
internal class DownloadJournal(private val db: MuseFlowDatabase, private val files: DownloadFiles) {
    val requests = db.downloadRequestDao()

    suspend fun enqueue(track: Track): DownloadRequestEntity = db.withTransaction {
        val prior = requests.get(track.downloadKey())
        check(prior?.requestState?.active != true) { "This download is already active" }
        DownloadRequestEntity(track.downloadKey(), track.title, track.artist, track.album, track.duration, track.gradientIndex,
            track.imageUrl, track.sourceId, track.sourceType?.name, track.albumId, track.artistId,
            DownloadRequestState.QUEUED.name, ((prior?.attempt ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis()).also { requests.put(it) }
    }

    suspend fun save(request: DownloadRequestEntity) {
        check(requests.checkpoint(request.key, request.token, request.state, request.downloadedBytes, request.totalBytes,
            request.sha256, request.error, request.attempt, System.currentTimeMillis()) == 1) { "Download attempt no longer owns this request" }
    }

    suspend fun keepExistingCompleted(completed: DownloadedTrackEntity) {
        val file = File(completed.filePath)
        check(completed.status == DownloadStatus.COMPLETED.name && files.owns(file) && file.isFile && file.length() > 0)
        val previous = requests.get(completed.key)
        if (previous?.requestState == DownloadRequestState.COMPLETED && previous.totalBytes == file.length() && previous.error == null) return
        requests.put(DownloadRequestEntity(completed.key, completed.title, completed.artist, completed.album, completed.duration,
            completed.gradientIndex, completed.imageUrl, completed.sourceId, completed.sourceType, completed.albumId, completed.artistId,
            DownloadRequestState.COMPLETED.name, previous?.attempt ?: 0, previous?.token ?: UUID.randomUUID().toString(),
            file.length(), file.length(), updatedAt = completed.updatedAt))
    }

    suspend fun prepareVerification(request: DownloadRequestEntity): DownloadRequestEntity {
        val partial = files.file(request.key, "part")
        check(partial.isFile && partial.length() > 0) { "The downloaded file is empty" }
        return request.copy(state = DownloadRequestState.VERIFYING.name, downloadedBytes = partial.length(),
            totalBytes = partial.length(), sha256 = files.hash(partial), error = null).also { save(it) }
    }

    suspend fun commit(request: DownloadRequestEntity) {
        val final = files.file(request.key, "audio")
        check(matches(final, request)) { "The saved file could not be verified" }
        db.withTransaction {
            val current = requests.get(request.key)
            check(current?.token == request.token && current.state == DownloadRequestState.VERIFYING.name)
            db.downloadedTrackDao().upsert(DownloadedTrackEntity(request.key, request.title, request.artist, request.album, request.duration,
                request.gradientIndex, request.imageUrl, final.absolutePath, DownloadStatus.COMPLETED.name, System.currentTimeMillis(),
                request.sourceId, request.sourceType, request.albumId, request.artistId))
            save(request.copy(state = DownloadRequestState.COMPLETED.name, error = null))
        }
    }

    private suspend fun matches(file: File, request: DownloadRequestEntity) = files.owns(file) && file.isFile &&
        request.totalBytes != null && request.totalBytes > 0 && file.length() == request.totalBytes &&
        request.sha256 != null && files.hash(file) == request.sha256

    suspend fun finishVerification(request: DownloadRequestEntity): Boolean {
        check(request.requestState == DownloadRequestState.VERIFYING)
        val final = files.file(request.key, "audio")
        if (!matches(final, request)) {
            val partial = files.file(request.key, "part")
            if (final.exists() || !matches(partial, request)) return false
            check(partial.renameTo(final)) { "Could not save the verified download" }
        }
        commit(request)
        return true
    }

    suspend fun reconcileAtStartup() {
        // Preserve older completed-file rows and their original locations. Do not rename/delete them.
        db.downloadedTrackDao().snapshot().forEach { completed ->
            if (requests.get(completed.key) == null) {
                val file = File(completed.filePath)
                val available = files.owns(file) && file.isFile && file.length() > 0 && completed.status == DownloadStatus.COMPLETED.name
                requests.put(DownloadRequestEntity(completed.key, completed.title, completed.artist, completed.album, completed.duration,
                    completed.gradientIndex, completed.imageUrl, completed.sourceId, completed.sourceType, completed.albumId, completed.artistId,
                    if (available) DownloadRequestState.COMPLETED.name else DownloadRequestState.MISSING.name,
                    0, UUID.randomUUID().toString(), if (available) file.length() else 0, if (available) file.length() else null,
                    error = if (available) null else "The saved audio is missing. Retry to download it again.", updatedAt = completed.updatedAt))
            }
        }
        requests.snapshot().forEach { request ->
            currentCoroutineContext().ensureActive()
            when {
                request.requestState == DownloadRequestState.VERIFYING && finishVerification(request) -> Unit
                request.requestState.active -> {
                    files.deletePartial(request.key)
                    save(request.copy(state = DownloadRequestState.INTERRUPTED.name, downloadedBytes = 0, totalBytes = null, sha256 = null,
                        error = "The download was interrupted. Retry to start a fresh transfer."))
                }
                request.requestState == DownloadRequestState.COMPLETED -> {
                    val completed = db.downloadedTrackDao().getByKey(request.key)
                    val file = completed?.let { File(it.filePath) }
                    if (file == null || !files.owns(file) || !file.isFile || file.length() <= 0 ||
                        (request.totalBytes != null && file.length() != request.totalBytes)) {
                        save(request.copy(state = DownloadRequestState.MISSING.name, error = "The saved audio is missing or incomplete. Retry to download it again."))
                        completed?.let { db.downloadedTrackDao().upsert(it.copy(status = DownloadRequestState.MISSING.name)) }
                    }
                }
            }
        }
    }
}
