package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomaticBackupDestinationTest {
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private lateinit var repository: BackupRepository
    private val destination = FakeDestination()
    private val folder = Uri.parse("content://backups/tree/user-folder")
    private fun file(name: String) = File(context.filesDir, "backups/$name")

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        repository = BackupRepository(context, db, externalDestination = destination)
        repository.setAutomaticDestination(null)
        repository.setIncludeHistoryEnabled(false); repository.setIncludeSettingsEnabled(false)
        listOf("auto_backup.json", "auto_backup.json.bak", "auto_backup.json.new", "previous_backup.json").forEach { file(it).delete() }
    }
    @After fun tearDown() = runBlocking { repository.setAutomaticDestination(null); db.close() }

    @Test fun externalCopiesAreExplicitAndInternalOnlyDoesNotContactAProvider() = runBlocking {
        assertTrue(repository.writeAutoBackup())
        assertTrue(repository.hasInternalBackup.value)
        assertEquals(0, destination.writes.size)
        assertNotNull(repository.lastBackupAt.first())
        assertNull(repository.automaticDestination.first().lastExternalBackupAt)
        repository.setAutomaticDestination(folder)
        assertEquals("My backups", repository.automaticDestination.first().label)
        assertTrue(repository.writeAutoBackup())
        assertEquals(listOf(folder), destination.writes.map { it.first })
        assertNotNull(repository.automaticDestination.first().lastExternalBackupAt)
        assertNull(repository.automaticDestination.first().error)
    }

    @Test fun providerFailureKeepsInternalCopyAndLastVerifiedExternalTimestamp() = runBlocking {
        repository.setAutomaticDestination(folder)
        assertTrue(repository.writeAutoBackup())
        val first = repository.automaticDestination.first().lastExternalBackupAt
        val previous = repository.readInternalBackup()
        destination.fail = true
        assertFalse(repository.writeAutoBackup())
        assertEquals(first, repository.automaticDestination.first().lastExternalBackupAt)
        assertNotNull(repository.automaticDestination.first().error)
        assertEquals(previous, file("previous_backup.json").readText())
        assertNotNull(parseBackupDocument(repository.readInternalBackup()))
        val fresh = BackupRepository(context, db, externalDestination = destination)
        assertNotNull(fresh.automaticDestination.first().error)
        destination.fail = false
        assertTrue(fresh.writeAutoBackup())
        assertNull(fresh.automaticDestination.first().error)
    }

    @Test fun changingOrRemovingFolderReleasesOnlyOldGrantAndNeverDeletesBackups() = runBlocking {
        repository.setAutomaticDestination(folder)
        assertTrue(repository.writeAutoBackup())
        val other = Uri.parse("content://backups/tree/other")
        repository.setAutomaticDestination(other)
        assertEquals(listOf(folder), destination.released)
        assertNull(repository.automaticDestination.first().lastExternalBackupAt)
        repository.setAutomaticDestination(null)
        assertEquals(listOf(folder, other), destination.released)
        assertEquals(1, destination.writes.size)
        assertNull(repository.automaticDestination.first().treeUri)
    }

    @Test fun failedFolderSelectionLeavesPreviousDestinationUsable() = runBlocking {
        repository.setAutomaticDestination(folder)
        destination.failRetain = true
        val denied = Uri.parse("content://backups/tree/denied")
        try { repository.setAutomaticDestination(denied); fail("Expected rejection") } catch (_: IOException) { }
        assertEquals(folder.toString(), repository.automaticDestination.first().treeUri)
        assertEquals(listOf(denied), destination.released)
    }

    @Test fun cancellationDoesNotMisreportExternalSuccessAndKeepsInternalCopy() = runBlocking {
        repository.setAutomaticDestination(folder)
        destination.cancel = true
        try { repository.writeAutoBackup(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertNull(repository.automaticDestination.first().lastExternalBackupAt)
        assertNotNull(parseBackupDocument(repository.readInternalBackup()))
    }

    @Test fun destinationCannotChangeMidWrite() = runBlocking {
        repository.setAutomaticDestination(folder)
        destination.reached = CompletableDeferred(); destination.releaseWrite = CompletableDeferred()
        val backup = async { repository.writeAutoBackup() }
        destination.reached!!.await()
        val change = async(start = CoroutineStart.UNDISPATCHED) { repository.setAutomaticDestination(null) }
        try { assertFalse(change.isCompleted) } finally { destination.releaseWrite!!.complete(Unit) }
        assertTrue(backup.await()); change.await()
        assertEquals(folder, destination.writes.single().first)
        assertNull(repository.automaticDestination.first().treeUri)
    }

    @Test fun exportedSnapshotsDoNotContainSignedPlaybackAddresses() = runBlocking {
        db.likedSongDao().like(LikedSongEntity("yt:abcdefghijk", "Song", "Artist", "", "", 0, null,
            "https://audio.test/play?signature=PRIVATE", 1, "abcdefghijk", "YOUTUBE_MUSIC"))
        assertTrue(repository.writeAutoBackup())
        val json = repository.readInternalBackup()
        assertFalse(json.contains("PRIVATE"))
        assertEquals("abcdefghijk", parseBackupDocument(json).likes.single().sourceId)
        assertNull(parseBackupDocument(json).likes.single().streamUrl)
    }

    private class FakeDestination : ExternalBackupDestination {
        var fail = false; var failRetain = false; var cancel = false
        var reached: CompletableDeferred<Unit>? = null; var releaseWrite: CompletableDeferred<Unit>? = null
        val writes = mutableListOf<Pair<Uri, String>>()
        val released = mutableListOf<Uri>()
        override suspend fun retain(tree: Uri): String { if (failRetain) throw IOException("grant denied"); return "My backups" }
        override suspend fun release(tree: Uri) { released.add(tree) }
        override suspend fun writeVerified(tree: Uri, json: String): Uri {
            reached?.complete(Unit); releaseWrite?.await()
            if (cancel) throw CancellationException("interrupted")
            if (fail) throw IOException("provider unavailable")
            writes.add(tree to json)
            return Uri.parse("content://backups/document/${writes.size}")
        }
    }
}
