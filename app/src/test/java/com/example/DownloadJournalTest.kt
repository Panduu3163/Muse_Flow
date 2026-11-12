package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadJournalTest {
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private lateinit var files: DownloadFiles
    private lateinit var journal: DownloadJournal
    private val track = Track("Song", "Artist", "Album", "3:00", "", 0, streamUrl = "https://signed.test/?secret=PRIVATE",
        sourceId = "abcdefghijk", sourceType = MusicSource.YOUTUBE_MUSIC)
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        files = DownloadFiles(context); files.directory.mkdirs(); journal = DownloadJournal(db, files)
        listOf("part", "audio", "cover").forEach { files.file(track.downloadKey(), it).delete() }
    }
    @After fun tearDown() { db.close() }

    @Test fun durableIntentKeepsRecordingIdentityWithoutSavingResolvedAddresses() = runBlocking {
        val request = journal.enqueue(track)
        assertEquals(track.downloadKey(), request.key)
        assertEquals(DownloadRequestState.QUEUED, request.requestState)
        assertNull(request.toTrack().streamUrl)
        assertEquals(track.sourceId, request.toTrack().sourceId)
        try { journal.enqueue(track); fail("Duplicate active request") } catch (_: IllegalStateException) { }
        assertEquals(request, journal.requests.get(request.key))
    }

    @Test fun restartMarksUnfinishedTransfersInterruptedAndRemovesOnlyTheirPartialFile() = runBlocking {
        val request = journal.enqueue(track)
        journal.save(request.copy(state = DownloadRequestState.DOWNLOADING.name, downloadedBytes = 2, totalBytes = 10))
        files.file(request.key, "part").writeBytes(byteArrayOf(1, 2))
        DownloadJournal(db, files).reconcileAtStartup()
        val recovered = journal.requests.get(request.key)!!
        assertEquals(DownloadRequestState.INTERRUPTED, recovered.requestState)
        assertFalse(files.file(request.key, "part").exists())
        assertNull(db.downloadedTrackDao().getByKey(request.key))
        assertEquals(2, journal.enqueue(recovered.toTrack()).attempt)
    }

    @Test fun processDeathBeforePromotionCanFinishVerifiedPartialLocally() = runBlocking {
        val request = journal.enqueue(track)
        files.file(request.key, "part").writeBytes(byteArrayOf(3, 4, 5))
        journal.prepareVerification(request)
        DownloadJournal(db, files).reconcileAtStartup()
        assertEquals(DownloadRequestState.COMPLETED, journal.requests.get(request.key)!!.requestState)
        assertArrayEquals(byteArrayOf(3, 4, 5), File(db.downloadedTrackDao().getByKey(request.key)!!.filePath).readBytes())
        assertFalse(files.file(request.key, "part").exists())
    }

    @Test fun processDeathAfterPromotionCommitsOneCompletedRowWithoutDownloadingAgain() = runBlocking {
        val request = journal.enqueue(track)
        files.file(request.key, "part").writeText("audio payload")
        journal.prepareVerification(request)
        assertTrue(files.file(request.key, "part").renameTo(files.file(request.key, "audio")))
        repeat(2) { DownloadJournal(db, files).reconcileAtStartup() }
        assertEquals(1, db.downloadedTrackDao().snapshot().size)
        assertEquals(DownloadRequestState.COMPLETED, journal.requests.get(request.key)!!.requestState)
    }

    @Test fun corruptOrTruncatedPartialNeverBecomesCompleted() = runBlocking {
        val request = journal.enqueue(track)
        files.file(request.key, "part").writeText("correct bytes")
        journal.prepareVerification(request)
        files.file(request.key, "part").writeText("wrong bytes!!")
        journal.reconcileAtStartup()
        assertEquals(DownloadRequestState.INTERRUPTED, journal.requests.get(request.key)!!.requestState)
        assertNull(db.downloadedTrackDao().getByKey(request.key))
        assertFalse(files.file(request.key, "audio").exists())
    }

    @Test fun existingUnmatchedFinalFileIsKeptAndNeverOverwrittenDuringRecovery() = runBlocking {
        val request = journal.enqueue(track)
        files.file(request.key, "part").writeText("new complete payload")
        journal.prepareVerification(request)
        files.file(request.key, "audio").writeText("previous file")
        journal.reconcileAtStartup()
        assertEquals("previous file", files.file(request.key, "audio").readText())
        assertNull(db.downloadedTrackDao().getByKey(request.key))
    }

    @Test fun failedDatabaseCommitRollsBackCompletionIndexAndKeepsRecoverablePromotion() = runBlocking {
        val request = journal.enqueue(track)
        files.file(request.key, "part").writeText("saved payload")
        val ready = journal.prepareVerification(request)
        files.file(request.key, "part").renameTo(files.file(request.key, "audio"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_completed BEFORE UPDATE ON download_requests WHEN NEW.state = 'COMPLETED' BEGIN SELECT RAISE(ABORT, 'injected'); END")
        try { journal.commit(ready); fail("Expected rollback") } catch (_: Exception) { }
        assertNull(db.downloadedTrackDao().getByKey(request.key))
        assertEquals(DownloadRequestState.VERIFYING, journal.requests.get(request.key)!!.requestState)
        assertTrue(files.file(request.key, "audio").isFile)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_completed")
        journal.reconcileAtStartup()
        assertEquals(DownloadRequestState.COMPLETED, journal.requests.get(request.key)!!.requestState)
    }

    @Test fun staleAttemptCannotOverwriteARetriedRequest() = runBlocking {
        val first = journal.enqueue(track)
        journal.save(first.copy(state = DownloadRequestState.FAILED.name))
        val second = journal.enqueue(track)
        try { journal.save(first.copy(state = DownloadRequestState.COMPLETED.name)); fail("Stale owner") } catch (_: IllegalStateException) { }
        assertEquals(second.token, journal.requests.get(first.key)!!.token)
        assertEquals(DownloadRequestState.QUEUED, journal.requests.get(first.key)!!.requestState)
    }

    @Test fun legacyCompletedFileAndLocationArePreservedWhileMissingAudioBecomesRetryable() = runBlocking {
        val legacy = File(files.directory, "legacy-name.audio").apply { writeText("legacy audio") }
        val completed = DownloadedTrackEntity(track.downloadKey(), track.title, track.artist, track.album, track.duration, 0, null,
            legacy.path, DownloadStatus.COMPLETED.name, 100, track.sourceId, track.sourceType!!.name)
        db.downloadedTrackDao().upsert(completed)
        journal.reconcileAtStartup()
        assertEquals(completed, db.downloadedTrackDao().getByKey(completed.key))
        assertEquals(DownloadRequestState.COMPLETED, journal.requests.get(completed.key)!!.requestState)
        assertEquals("legacy audio", legacy.readText())
        legacy.delete()
        journal.reconcileAtStartup()
        assertEquals(DownloadRequestState.MISSING, journal.requests.get(completed.key)!!.requestState)
        assertEquals("MISSING", db.downloadedTrackDao().getByKey(completed.key)!!.status)
    }

    @Test fun fileHelpersCannotDeleteOutsideThePrivateDownloadDirectory() = runBlocking {
        val unrelated = File(context.filesDir, "keep-me.txt").apply { writeText("keep") }
        assertFalse(files.owns(unrelated))
        try { files.delete(unrelated); fail("Outside file must be rejected") } catch (_: IllegalStateException) { }
        assertEquals("keep", unrelated.readText())
    }
}
