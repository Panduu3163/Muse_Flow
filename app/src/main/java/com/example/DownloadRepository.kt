package com.example

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a track's audio to app-private storage (`filesDir/downloads`) for offline playback,
 * and tracks the outcome in Room via [DownloadedTrackDao] - that's the source of truth for
 * "is this downloaded" everywhere in the app (Library's Downloads tab, the Offline mode filter,
 * and [PlayerViewModel]'s decision to play from a local file instead of streaming). Per-byte
 * progress while a download is in flight is kept in memory only ([inProgress]); persisting that
 * to Room on every chunk would be pure churn for a number nothing needs after the download ends.
 *
 * A process-wide singleton (see [getInstance]) rather than a ViewModel-owned instance, since
 * downloads must keep running (and stay visible as "downloading") across whichever screen the
 * user navigates to next.
 */
class DownloadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = MuseFlowDatabase.getInstance(appContext).downloadedTrackDao()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _inProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Key -> percent complete (0..100), or -1 if the server didn't report a content length. */
    val inProgress: StateFlow<Map<String, Int>> = _inProgress

    val completedDownloads: Flow<List<DownloadedTrackEntity>> = dao.observeCompleted()

    fun isDownloading(track: Track): Boolean = activeJobs.containsKey(track.downloadKey())

    fun startDownload(track: Track) {
        val key = track.downloadKey()
        if (activeJobs.containsKey(key)) return
        activeJobs[key] = repositoryScope.launch {
            _inProgress.update { it + (key to 0) }
            try {
                val streamUrl = track.streamUrl ?: resolveStreamUrl(track)
                ?: error("No playable stream found for \"${track.title}\"")
                val targetFile = File(downloadsDir(appContext), "$key.audio")
                downloadToFile(streamUrl, targetFile) { percent ->
                    _inProgress.update { it + (key to percent) }
                }
                dao.upsert(
                    DownloadedTrackEntity(
                        key = key,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        duration = track.duration,
                        gradientIndex = track.gradientIndex,
                        imageUrl = track.imageUrl,
                        filePath = targetFile.absolutePath,
                        status = DownloadStatus.COMPLETED.name,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                // Don't leave a stale/broken row around - a partial file is useless, and the user
                // can just tap download again.
                File(downloadsDir(appContext), "$key.audio").delete()
            } finally {
                _inProgress.update { it - key }
                activeJobs.remove(key)
            }
        }
    }

    fun cancelDownload(track: Track) {
        activeJobs.remove(track.downloadKey())?.cancel()
    }

    suspend fun deleteDownload(track: Track) {
        val key = track.downloadKey()
        cancelDownload(track)
        dao.getByKey(key)?.let { File(it.filePath).delete() }
        dao.deleteByKey(key)
    }

    private fun resolveStreamUrl(track: Track): String? {
        // Blocking call is fine here - already running on repositoryScope's IO dispatcher.
        return runBlocking {
            JioSaavnProvider().search("${track.title} ${track.artist}")
                .firstOrNull { it.directStreamUrl != null }?.directStreamUrl
        }
    }

    private fun downloadToFile(url: String, targetFile: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("Empty download response")
            val contentLength = body.contentLength()
            targetFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var totalRead = 0L
                    var lastReportedPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun downloadsDir(context: Context): File = File(context.filesDir, "downloads")

        @Volatile private var instance: DownloadRepository? = null

        fun getInstance(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}
