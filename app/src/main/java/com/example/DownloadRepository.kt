package com.example

import android.content.Context
import androidx.core.net.toUri
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/** Durable request journal plus the existing app-private, complete-file playback index. */
class DownloadRepository internal constructor(
    context: Context,
    private val db: MuseFlowDatabase = MuseFlowDatabase.getInstance(context.applicationContext),
    private val transfer: DownloadTransfer = HttpDownloadTransfer(),
    private val resolver: suspend (Track, Boolean) -> StreamResolution? = { track, fresh -> resolveDownloadStream(context.applicationContext, track, fresh) },
    private val startService: (Context) -> Unit = DownloadService::start,
    private val online: (Context) -> Boolean = ::isOnline,
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext
    private val dao = db.downloadedTrackDao()
    private val files = DownloadFiles(appContext)
    private val journal = DownloadJournal(db, files)
    private val initializeMutex = Mutex()
    private var initialized = false
    private val cleanupMutex = Mutex()
    private val ownership = Any()
    private var cleaning = false
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeTracks = ConcurrentHashMap<String, Track>()
    private val interruptions = ConcurrentHashMap<String, String>()
    private val downloadSlots = Semaphore(3)
    private val _inProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val inProgress = _inProgress.asStateFlow()
    private val transientFailures = MutableStateFlow<Map<String, String>>(emptyMap())
    val requests: Flow<List<DownloadRequestEntity>> = journal.requests.observeAll()
    val failures: StateFlow<Map<String, String>> = combine(requests, transientFailures, inProgress) { saved, transient, active ->
        (saved.filter { it.error != null }.associate { it.key to it.error!! } + transient).filterKeys { it !in active }
    }.stateIn(repositoryScope, SharingStarted.Eagerly, emptyMap())

    private val completedRequests = requests.map { saved ->
        saved.filter { it.requestState == DownloadRequestState.COMPLETED }.associateBy { it.key }
    }.distinctUntilChanged()
    val completedDownloads: Flow<List<DownloadedTrackEntity>> = combine(dao.observeCompleted(), completedRequests) { entries, states ->
        entries.filter { entry -> available(entry, states[entry.key]) }
    }.flowOn(Dispatchers.IO)

    private fun available(entry: DownloadedTrackEntity, request: DownloadRequestEntity?): Boolean {
        val file = File(entry.filePath)
        return entry.status == DownloadStatus.COMPLETED.name && files.owns(file) && file.isFile && file.length() > 0 &&
            (request?.requestState != DownloadRequestState.COMPLETED || request.totalBytes == null || file.length() == request.totalBytes)
    }

    suspend fun getByKey(key: String): DownloadedTrackEntity? = withContext(Dispatchers.IO) {
        dao.getByKey(key)?.takeIf { available(it, journal.requests.get(key)) }
    }

    suspend fun initialize() = initializeMutex.withLock {
        if (!initialized) { withContext(Dispatchers.IO) { journal.reconcileAtStartup() }; initialized = true }
    }

    fun isDownloading(track: Track): Boolean = activeJobs.containsKey(track.downloadKey())

    fun startDownload(track: Track): Boolean = synchronized(ownership) {
        val key = track.downloadKey()
        if (cleaning) return@synchronized false
        if (activeJobs.containsKey(key)) return@synchronized true
        transientFailures.update { it - key }
        val job = repositoryScope.launch(start = CoroutineStart.LAZY) { runDownload(track) }
        activeJobs[key] = job
        job.invokeOnCompletion {
            synchronized(ownership) {
                if (activeJobs.remove(key, job)) interruptions.remove(key)
            }
        }
        job.start()
        true
    }

    private fun refreshNotification() {
        val active = _inProgress.value.mapNotNull { (key, percent) -> activeTracks[key]?.let { key to (it to percent) } }.toMap()
        runCatching { DownloadNotificationHelper.updateProgress(appContext, active) }
    }

    private suspend fun runDownload(track: Track) {
        val key = track.downloadKey()
        var request: DownloadRequestEntity? = null
        var acquired = false
        var promoted = false
        var committed = false
        try {
            initialize()
            getByKey(key)?.let { journal.keepExistingCompleted(it); return }
            // A DB failure after promotion may leave a locally recoverable verification record.
            journal.requests.get(key)?.takeIf { it.requestState == DownloadRequestState.VERIFYING }?.let { pending ->
                if (journal.finishVerification(pending)) return
                journal.save(pending.copy(state = DownloadRequestState.INTERRUPTED.name, error = "Retry the interrupted download."))
            }
            var current = journal.enqueue(track)
            request = current
            suspend fun checkpoint(updated: DownloadRequestEntity) {
                current = updated
                request = updated
                journal.save(updated)
            }
            // A remembered online recording may still carry its removed download's file URI.
            // Re-resolve its exact recording ID, while genuine device-only files stay local.
            val localAddress = track.streamUrl?.let { it.startsWith("content:") || it.startsWith("file:") } == true
            val remoteRecording = track.sourceId?.looksLikeYouTubeVideoId() == true
            if (track.sourceType == MusicSource.LOCAL_DEVICE || (localAddress && !remoteRecording))
                throw DownloadTransferFailure("This song is already on your device. Use its local file for playback.")
            activeTracks[key] = track
            _inProgress.update { it + (key to -1) }
            startService(appContext)
            refreshNotification()
            downloadSlots.acquire(); acquired = true
            var result: DownloadTransferResult? = null
            for (retry in 0..1) {
                try {
                    if (!online(appContext)) throw DownloadTransferFailure("You are offline. Connect, then retry the download.")
                    checkpoint(current.copy(state = DownloadRequestState.RESOLVING.name, error = null))
                    val stream = resolver(track, retry > 0 || current.attempt > 1)
                        ?: throw DownloadTransferFailure("No playable stream was found for this recording. Retry later.")
                    currentCoroutineContext().ensureActive()
                    checkpoint(current.copy(state = DownloadRequestState.DOWNLOADING.name, downloadedBytes = 0, totalBytes = null))
                    result = transfer.fetch(stream, files.file(key, "part")) { bytes, total ->
                        checkpoint(current.copy(downloadedBytes = bytes, totalBytes = total))
                        _inProgress.update { it + (key to if (total != null && total > 0) (bytes.toDouble() / total * 100).toInt().coerceIn(0, 100) else -1) }
                        refreshNotification()
                    }
                    break
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    val retryable = (error as? DownloadTransferFailure)?.retryable ?: (error is IOException &&
                        error.message?.contains("ENOSPC", ignoreCase = true) != true)
                    if (retry == 1 || !retryable) throw error
                    files.deletePartial(key)
                    checkpoint(current.copy(state = DownloadRequestState.QUEUED.name, downloadedBytes = 0, totalBytes = null,
                        attempt = (current.attempt.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), error = "Transfer interrupted. Retrying once."))
                    _inProgress.update { it + (key to -1) }
                    delay(1_000)
                }
            }
            check(result != null)
            val cover = track.imageUrl?.let { transfer.artwork(upgradeThumbnailUrl(it)) }
            runCatching { AudioTagger.embedIfSupported(files.file(key, "part"), result.contentType, track, cover) }
            currentCoroutineContext().ensureActive()
            current = journal.prepareVerification(current)
            request = current
            currentCoroutineContext().ensureActive()
            val target = files.file(key, "audio")
            if (target.exists() || !files.file(key, "part").renameTo(target))
                throw DownloadTransferFailure("Could not save the audio. Remove the unfinished download in Storage, then retry.")
            promoted = true
            // Promotion is the commit boundary. Late cancellation cannot undo an already-saved file.
            withContext(NonCancellable) { journal.commit(current); committed = true }
            if (cover != null) runCatching { writeCover(key, cover) }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                if (!committed) {
                    val reason = interruptions[key]
                    request?.let { saved ->
                        runCatching {
                            if (!promoted) files.deletePartial(key)
                            journal.save(saved.copy(state = if (promoted) DownloadRequestState.VERIFYING.name else
                                if (reason != null) DownloadRequestState.INTERRUPTED.name else DownloadRequestState.CANCELLED.name,
                                downloadedBytes = if (promoted) saved.downloadedBytes else 0,
                                totalBytes = if (promoted) saved.totalBytes else null, sha256 = if (promoted) saved.sha256 else null,
                                error = if (promoted) "Audio is saved; retry to finish local recovery." else reason ?: "Download cancelled. Retry when you want to download it."))
                        }
                    }
                }
            }
            throw cancelled
        } catch (error: Exception) {
            val message = if (promoted) "Audio is saved; retry to finish local recovery." else
                (error as? DownloadTransferFailure)?.message ?: "Download failed. Check your connection and free storage, then retry."
            transientFailures.update { it + (key to message) }
            request?.let { saved -> runCatching {
                if (!promoted) files.deletePartial(key)
                journal.save(saved.copy(state = if (promoted) DownloadRequestState.VERIFYING.name else DownloadRequestState.FAILED.name,
                    downloadedBytes = if (promoted) saved.downloadedBytes else 0, totalBytes = if (promoted) saved.totalBytes else null,
                    sha256 = if (promoted) saved.sha256 else null, error = message))
            } }
        } finally {
            if (acquired) downloadSlots.release()
            _inProgress.update { it - key }
            activeTracks.remove(key)
            interruptions.remove(key)
            synchronized(ownership) { activeJobs.remove(key) }
            refreshNotification()
        }
    }

    fun cancelDownload(track: Track) {
        val key = track.downloadKey()
        activeJobs[key]?.let { job ->
            _inProgress.update { if (key in it) it + (key to -2) else it }
            job.cancel()
        }
    }

    /** Stop immediately for a service timeout/destruction. Old active rows also recover on restart. */
    fun interruptAll(reason: String) = synchronized(ownership) {
        activeJobs.forEach { (key, job) -> interruptions.putIfAbsent(key, reason); job.cancel() }
    }

    suspend fun deleteDownload(track: Track) = cleanupMutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val key = track.downloadKey()
            synchronized(ownership) { cleaning = true }
            try {
                activeJobs[key]?.let { it.cancelAndJoin() }
                initialize()
                dao.getByKey(key)?.let { entry ->
                    localCoverFile(appContext, key, entry.filePath)?.let(files::delete)
                    val audio = File(entry.filePath)
                    if (files.owns(audio)) files.delete(audio)
                }
                files.deletePartial(key)
                listOf("audio", "cover").forEach { files.delete(files.file(key, it)) }
                db.withTransaction { dao.deleteByKey(key); journal.requests.delete(key) }
                transientFailures.update { it - key }
            } finally { synchronized(ownership) { cleaning = false } }
        }
    }

    suspend fun deleteAllDownloads() = cleanupMutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val jobs = synchronized(ownership) { cleaning = true; activeJobs.values.toList() }
            try {
                jobs.forEach { it.cancel() }; jobs.forEach { it.join() }
                initialize()
                files.directory.walkBottomUp().filter { it != files.directory }.forEach(files::delete)
                db.withTransaction { dao.clearAll(); journal.requests.clear() }
                transientFailures.value = emptyMap()
            } finally { synchronized(ownership) { cleaning = false } }
        }
    }

    suspend fun backfillMissingCovers() {
        initialize()
        if (!online(appContext)) return
        for (entry in completedDownloads.first()) {
            currentCoroutineContext().ensureActive()
            if (localCoverFile(appContext, entry.key, entry.filePath) != null) continue
            if (getByKey(entry.key) == null) continue
            val url = entry.imageUrl ?: continue
            transfer.artwork(upgradeThumbnailUrl(url))?.let { bytes ->
                cleanupMutex.withLock {
                    if (getByKey(entry.key) != null) runCatching { writeCover(entry.key, bytes) }
                }
            }
        }
    }

    private fun writeCover(key: String, bytes: ByteArray) {
        files.directory.mkdirs()
        writeAtomicFile(files.file(key, "cover"), bytes)
    }

    private fun upgradeThumbnailUrl(raw: String) = raw.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")

    companion object {
        fun downloadsDir(context: Context): File = DownloadFiles(context).directory
        fun localCoverFile(context: Context, key: String, audioFilePath: String? = null): File? {
            val paths = DownloadFiles(context)
            val sibling = audioFilePath?.let { raw ->
                val path = if (raw.startsWith("file:")) raw.toUri().path else raw
                path?.let { File(it).let { audio -> File(audio.parentFile, audio.nameWithoutExtension + ".cover") } }
            }
            return sequenceOf(paths.file(key, "cover"), sibling, File(paths.directory, "$key.cover"))
                .filterNotNull().firstOrNull { it.isFile && it.length() > 0 && paths.owns(it) }
        }
        @Volatile private var instance: DownloadRepository? = null
        fun getInstance(context: Context): DownloadRepository = instance ?: synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { instance = it }
        }
    }
}

private suspend fun resolveDownloadStream(context: Context, track: Track, fresh: Boolean): StreamResolution? {
    val source = track.sourceId
    if (source != null && (track.sourceType == MusicSource.YOUTUBE_MUSIC || source.looksLikeYouTubeVideoId())) {
        if (fresh) StreamResolverRouter.invalidate(source)
        return StreamResolverRouter.resolve(context, source, qualityOverride = AudioQualityMode.BestAvailable)
    }
    if (track.sourceType == MusicSource.LOCAL_DEVICE) return null
    track.streamUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { return StreamResolution(it) }
    val match = MusicSearchRouter(context).searchTracks("${track.title} ${track.artist}").firstOrNull() ?: return null
    if (fresh) StreamResolverRouter.invalidate(match.id)
    return StreamResolverRouter.resolve(context, match.id, qualityOverride = AudioQualityMode.BestAvailable)
}
