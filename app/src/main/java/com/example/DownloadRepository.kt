package com.example

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Key -> error message for the most recent failed download attempt, so the UI can tell the
     * user *why* their download vanished instead of it just silently reverting to "not
     * downloaded". Cleared as soon as that track's download is retried. */
    val failures: StateFlow<Map<String, String>> = _failures

    val completedDownloads: Flow<List<DownloadedTrackEntity>> = dao.observeCompleted()

    fun isDownloading(track: Track): Boolean = activeJobs.containsKey(track.downloadKey())

    fun startDownload(track: Track) {
        val key = track.downloadKey()
        if (activeJobs.containsKey(key)) return
        _failures.update { it - key }
        // Real foreground-service priority for as long as anything's downloading, so the OS
        // doesn't kill this coroutine mid-transfer once the screen turns off - see DownloadService.
        DownloadService.start(appContext)
        activeJobs[key] = repositoryScope.launch {
            // -1: no percent known yet (server hasn't reported a size, or the request hasn't
            // opened yet) - both the in-app button and the system notification show an
            // indeterminate spinner for this, distinct from an actual 0-99% in progress.
            _inProgress.update { it + (key to -1) }
            DownloadNotificationHelper.showProgress(appContext, track, percent = null)
            try {
                val streamUrl = track.streamUrl ?: resolveStreamUrl(track)
                ?: error("No playable stream found for \"${track.title}\"")
                val targetFile = File(downloadsDir(appContext), "$key.audio")
                downloadToFile(streamUrl, targetFile) { percent ->
                    _inProgress.update { it + (key to percent) }
                    DownloadNotificationHelper.showProgress(appContext, track, percent)
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
                        updatedAt = System.currentTimeMillis(),
                        sourceId = track.sourceId,
                        sourceType = track.sourceType?.name
                    )
                )
                DownloadNotificationHelper.showCompleted(appContext, track)
            } catch (e: CancellationException) {
                // A user-initiated cancel (see cancelDownload) - not a failure, just clean up
                // the partial file below and let the cancellation propagate as normal.
                File(downloadsDir(appContext), "$key.audio").delete()
                DownloadNotificationHelper.clear(appContext, key)
                throw e
            } catch (e: Exception) {
                // Don't leave a stale/broken row around - a partial file is useless, and the user
                // can just tap download again.
                File(downloadsDir(appContext), "$key.audio").delete()
                _failures.update { it + (key to (e.message ?: "Download failed")) }
                DownloadNotificationHelper.clear(appContext, key)
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

    /** The single download-resolution path, used by every caller of [startDownload] regardless of
     * which screen triggered it (Search, an Album/Playlist/Artist tracklist, Now Playing, or a
     * re-download from the Downloads list) - mirrors the exact priority `Track.toQueueMediaItem`
     * (in PlayerViewModel.kt) already uses to resolve the very same track for *playback*, so a
     * download never resolves a different recording than what the user actually sees/hears:
     *
     * 1. A known [Track.sourceId] on a [MusicSource.YOUTUBE_MUSIC] track resolves directly via
     *    [YouTubeStreamResolver] - the exact video the track identifies, not a guess.
     * 2. Otherwise (a mock-catalog track with no known source - the only remaining case, since
     *    [Track.streamUrl] is already checked by the caller before this is ever called), fall back
     *    to a fuzzy title+artist text search - JioSaavn first, then YouTube Music if JioSaavn
     *    comes back empty or throws, same resilience pattern Search/Home use.
     *
     * A YouTube-resolved URL is short-lived (see [YouTubeStreamResolver] - it 403s within
     * minutes), but that's fine here: it's read once, immediately, straight into a local file by
     * [downloadToFile] below, never stored or reused.
     */
    private suspend fun resolveStreamUrl(track: Track): String? {
        val sourceId = track.sourceId
        if (track.sourceType == MusicSource.YOUTUBE_MUSIC && sourceId != null) {
            return runCatching { YouTubeStreamResolver.resolve(appContext, sourceId) }.getOrNull()
        }

        val query = "${track.title} ${track.artist}"
        val jioStreamUrl = runCatching {
            JioSaavnProvider().search(query).firstOrNull { it.directStreamUrl != null }?.directStreamUrl
        }.getOrNull()
        if (jioStreamUrl != null) return jioStreamUrl

        return runCatching {
            val match = YouTubeMusicProvider(appContext).search(query).firstOrNull()
            match?.let { YouTubeStreamResolver.resolve(appContext, it.id) }
        }.getOrNull()
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
