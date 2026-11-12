package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DurableDownloadRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var files: DownloadFiles
    private val track = Track("Song", "Artist", "Album", "3:00", "", 0, sourceId = "abcdefghijk", sourceType = MusicSource.YOUTUBE_MUSIC)
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        files = DownloadFiles(context); files.directory.mkdirs()
    }
    @After fun tearDown() = runBlocking { scope.coroutineContext.job.cancelAndJoin(); db.close() }
    private fun repository(transport: DownloadTransfer, resolved: suspend (Track, Boolean) -> StreamResolution? = { _, _ -> StreamResolution("https://audio.test") }) =
        DownloadRepository(context, db, transport, resolved, startService = {}, online = { true }, repositoryScope = scope)
    private fun track(index: Int) = track.copy(title = "Song $index", sourceId = "abcdefghij$index")
    private suspend fun awaitState(repo: DownloadRepository, key: String, state: DownloadRequestState) = withTimeout(10_000) {
        repo.requests.first { rows -> rows.any { it.key == key && it.requestState == state } }.first { it.key == key }
    }

    @Test fun queueIsDurableDeduplicatedAndRunsAtMostThreeTransfers() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val active = java.util.concurrent.atomic.AtomicInteger()
        val maximum = java.util.concurrent.atomic.AtomicInteger()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val threeStarted = CompletableDeferred<Unit>()
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                calls.incrementAndGet(); val count = active.incrementAndGet(); maximum.updateAndGet { maxOf(it, count) }
                if (count == 3) threeStarted.complete(Unit)
                try { release.await(); target.writeText("audio bytes"); progress(target.length(), target.length()); return DownloadTransferResult("audio/webm", target.length()) }
                finally { active.decrementAndGet() }
            }
        }
        val repo = repository(transport)
        val tracks = (0..3).map(::track)
        tracks.forEach { repo.startDownload(it); repo.startDownload(it) }
        withTimeout(10_000) { repo.requests.first { rows -> rows.count { it.requestState == DownloadRequestState.DOWNLOADING } == 3 && rows.size == 4 } }
        withTimeout(5_000) { threeStarted.await() }
        assertEquals(4, repo.inProgress.value.size)
        release.complete(Unit)
        withTimeout(10_000) { repo.requests.first { it.count { row -> row.requestState == DownloadRequestState.COMPLETED } == 4 } }
        assertEquals(4, calls.get()); assertEquals(3, maximum.get())
        assertEquals(4, repo.completedDownloads.first().size)
    }

    @Test fun explicitCancelPersistsItsOutcomeAndRemovesPartialBytes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                target.writeText("partial"); started.complete(Unit); awaitCancellation()
            }
        }
        val repo = repository(transport)
        repo.startDownload(track); withTimeout(5_000) { started.await() }
        repo.cancelDownload(track)
        awaitState(repo, track.downloadKey(), DownloadRequestState.CANCELLED)
        assertFalse(files.file(track.downloadKey(), "part").exists())
        assertTrue(repo.completedDownloads.first().isEmpty())
    }

    @Test fun systemInterruptionPersistsRetryableReasonWithoutAutomaticRestart() = runBlocking {
        var transfers = 0
        val started = CompletableDeferred<Unit>()
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                transfers++; started.complete(Unit); awaitCancellation()
            }
        }
        val repo = repository(transport)
        repo.startDownload(track); withTimeout(5_000) { started.await() }
        repo.interruptAll("Android service time limit. Open MuseFlow and retry.")
        val interrupted = awaitState(repo, track.downloadKey(), DownloadRequestState.INTERRUPTED)
        assertTrue(interrupted.error!!.contains("time limit"))
        repository(transport).initialize()
        assertEquals(1, transfers)
        assertEquals(DownloadRequestState.INTERRUPTED, db.downloadRequestDao().get(track.downloadKey())!!.requestState)
    }

    @Test fun transientFailureRetriesOnceWithFreshResolutionAndKeepsRecordingIdentity() = runBlocking {
        val freshFlags = mutableListOf<Boolean>(); var transfers = 0
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                transfers++
                if (transfers == 1) throw DownloadTransferFailure("HTTP 403", retryable = true)
                target.writeText("correct recording"); return DownloadTransferResult("audio/webm", target.length())
            }
        }
        val repo = repository(transport) { requested, fresh -> assertEquals(track.sourceId, requested.sourceId); freshFlags.add(fresh); StreamResolution("https://audio.test", "Agent") }
        repo.startDownload(track)
        val completed = awaitState(repo, track.downloadKey(), DownloadRequestState.COMPLETED)
        assertEquals(listOf(false, true), freshFlags)
        assertEquals(2, completed.attempt)
        assertEquals(track.sourceId, db.downloadedTrackDao().getByKey(track.downloadKey())!!.sourceId)
    }

    @Test fun repeatedFailureIsBoundedAndSurvivesANewRepository() = runBlocking {
        var transfers = 0
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                transfers++; throw DownloadTransferFailure("Server unavailable", retryable = true)
            }
        }
        val repo = repository(transport); repo.startDownload(track)
        assertEquals(2, awaitState(repo, track.downloadKey(), DownloadRequestState.FAILED).attempt)
        val fresh = repository(transport); fresh.initialize()
        assertEquals("Server unavailable", withTimeout(5_000) { fresh.failures.first { track.downloadKey() in it } }[track.downloadKey()])
        assertEquals(2, transfers)
    }

    @Test fun cleanupWaitsForCancellationAndBlocksNewEnqueuesUntilFinished() = runBlocking {
        val cancelReached = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                target.writeText("partial")
                started.complete(Unit)
                try { awaitCancellation() } catch (cancelled: CancellationException) {
                    cancelReached.complete(Unit); withContext(NonCancellable) { release.await() }; throw cancelled
                }
            }
        }
        val unrelated = File(context.filesDir, "outside-downloads.txt").apply { writeText("keep") }
        val repo = repository(transport); repo.startDownload(track)
        withTimeout(5_000) { started.await() }
        val clear = async { repo.deleteAllDownloads() }
        try {
            withTimeout(5_000) { cancelReached.await() }
            assertFalse(clear.isCompleted); repo.startDownload(track(2)); assertFalse(repo.isDownloading(track(2)))
        }
        finally { release.complete(Unit) }
        withTimeout(5_000) { clear.await() }
        assertTrue(db.downloadRequestDao().snapshot().isEmpty()); assertTrue(db.downloadedTrackDao().snapshot().isEmpty())
        assertEquals(0, files.directory.listFiles()!!.size); assertEquals("keep", unrelated.readText())
    }

    @Test fun savedAudioAfterDatabaseFailureFinishesLocallyOnExplicitRetry() = runBlocking {
        var transfers = 0
        val transport = object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                transfers++; target.writeText("complete audio"); return DownloadTransferResult("audio/webm", target.length())
            }
        }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_commit BEFORE INSERT ON downloaded_tracks BEGIN SELECT RAISE(ABORT, 'injected'); END")
        val repo = repository(transport); repo.startDownload(track)
        withTimeout(10_000) { repo.requests.first { rows -> rows.any { it.requestState == DownloadRequestState.VERIFYING && it.error != null } } }
        withTimeout(5_000) { while (repo.isDownloading(track)) delay(10) }
        assertTrue(files.file(track.downloadKey(), "audio").isFile)
        assertTrue(repo.completedDownloads.first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_commit")
        repo.startDownload(track); awaitState(repo, track.downloadKey(), DownloadRequestState.COMPLETED)
        assertEquals(1, transfers); assertEquals("complete audio", File(repo.getByKey(track.downloadKey())!!.filePath).readText())
    }

    @Test fun aStaleFailedIntentCannotHideAnExistingCompletedAudioFile() = runBlocking {
        val old = DownloadJournal(db, files).enqueue(track)
        DownloadJournal(db, files).save(old.copy(state = DownloadRequestState.FAILED.name, error = "Old failure"))
        val audio = File(files.directory, "existing-audio.audio").apply { writeText("keep these bytes") }
        db.downloadedTrackDao().upsert(DownloadedTrackEntity(track.downloadKey(), track.title, track.artist, track.album, track.duration,
            0, null, audio.path, DownloadStatus.COMPLETED.name, 1, track.sourceId, track.sourceType!!.name))
        val repo = repository(object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult = error("Must not download")
        })
        repo.startDownload(track)
        awaitState(repo, track.downloadKey(), DownloadRequestState.COMPLETED)
        assertEquals("keep these bytes", audio.readText())
        assertNotNull(repo.getByKey(track.downloadKey()))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun cancellationBeforeTheCoroutineBeginsCannotLeaveAPermanentActiveJob() = kotlinx.coroutines.test.runTest {
        val pausedScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        try {
            val repo = DownloadRepository(context, db, repositoryScope = pausedScope)
            repo.startDownload(track)
            assertTrue(repo.isDownloading(track))
            repo.cancelDownload(track)
            runCurrent()
            assertFalse(repo.isDownloading(track))
            assertTrue(db.downloadRequestDao().snapshot().isEmpty())
        } finally { pausedScope.cancel() }
    }

    @Test fun artworkBackfillCannotRecreateFilesAfterClearDownloadsCompletes() = runBlocking {
        val reached = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val audio = files.file(track.downloadKey(), "audio").apply { writeText("kept audio") }
        db.downloadedTrackDao().upsert(DownloadedTrackEntity(track.downloadKey(), track.title, track.artist, track.album, track.duration,
            0, "https://artwork.test/cover", audio.path, DownloadStatus.COMPLETED.name, 1, track.sourceId, track.sourceType!!.name))
        val repo = repository(object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray { reached.complete(Unit); release.await(); return byteArrayOf(1, 2, 3) }
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult = error("Must not download audio")
        })
        val backfill = async { repo.backfillMissingCovers() }
        try {
            withTimeout(5_000) { reached.await() }
            withTimeout(5_000) { repo.deleteAllDownloads() }
        } finally { release.complete(Unit) }
        backfill.await()
        assertTrue(db.downloadedTrackDao().snapshot().isEmpty())
        assertTrue(files.directory.listFiles()!!.isEmpty())
    }

    @Test fun removedDownloadInRememberedQueueResolvesTheSameOnlineRecording() = runBlocking {
        val stale = track.copy(streamUrl = File(files.directory, "removed.audio").toURI().toString())
        var resolvedId: String? = null
        val repo = repository(object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
                assertEquals("https://fresh.test/same-recording", stream.url)
                target.writeText("fresh recording bytes")
                return DownloadTransferResult("audio/webm", target.length())
            }
        }) { requested, _ ->
            resolvedId = requested.sourceId
            StreamResolution("https://fresh.test/same-recording")
        }
        repo.startDownload(stale)
        awaitState(repo, stale.downloadKey(), DownloadRequestState.COMPLETED)
        assertEquals(track.sourceId, resolvedId)
        assertEquals("fresh recording bytes", File(repo.getByKey(stale.downloadKey())!!.filePath).readText())
    }

    @Test fun deviceFilesAndUnidentifiedLocalAddressesNeverResolveOnline() = runBlocking {
        val repo = repository(object : DownloadTransfer {
            override suspend fun artwork(url: String): ByteArray? = null
            override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult = error("Must not transfer local files")
        }) { _, _ -> error("Must not search for a different recording") }
        val localTracks = listOf(
            track.copy(title = "Device", sourceType = MusicSource.LOCAL_DEVICE, streamUrl = "content://media/audio/123"),
            track.copy(title = "File", sourceId = null, streamUrl = "file:///missing.audio"),
            track.copy(title = "Content", sourceId = null, streamUrl = "content://media/audio/456"),
        )
        for (local in localTracks) {
            repo.startDownload(local)
            val failed = awaitState(repo, local.downloadKey(), DownloadRequestState.FAILED)
            assertTrue(failed.error!!.contains("already on your device"))
        }
        assertTrue(repo.completedDownloads.first().isEmpty())
    }
}
