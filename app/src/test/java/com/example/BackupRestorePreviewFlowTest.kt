package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestorePreviewFlowTest {
    private lateinit var application: Application
    private lateinit var db: MuseFlowDatabase
    private lateinit var model: BackupViewModel
    private lateinit var repository: BackupRepository
    private val store = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(application, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        repository = BackupRepository(application, db)
        model = BackupViewModel(application, repository)
        store.put("backup", model)
    }

    @After fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun previewAndCancellationNeverModifyTheLibrary() = runTest {
        model.previewRestoreFrom(Uri.fromFile(backupFile()))
        assertEquals(1, model.restorePreview.filterNotNull().first().newLikedSongs)
        model.busy.first { !it }
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        model.cancelRestorePreview()
        model.confirmRestore()
        assertNull(model.restorePreview.value)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertNull(model.outcome.value)
    }

    @Test fun confirmationUsesValidatedContentsEvenWhenTheFileChangesAndRunsOnlyOnce() = runTest {
        val file = backupFile()
        model.previewRestoreFrom(Uri.fromFile(file))
        model.restorePreview.filterNotNull().first()
        model.busy.first { !it }
        file.writeText("This is no longer the selected backup")
        model.confirmRestore()
        model.confirmRestore()
        val result = model.outcome.filterNotNull().first() as BackupOutcome.Restored
        model.busy.first { !it }
        assertEquals(1, result.likedSongs)
        assertEquals("Kept recording", db.likedSongDao().backupSnapshot().single().title)
        assertNull(model.restorePreview.value)
    }

    @Test fun invalidFileHasNoConfirmationAndLeavesTheLibraryUntouched() = runTest {
        val file = backupFile().apply { writeText("{invalid") }
        model.previewRestoreFrom(Uri.fromFile(file))
        assertTrue(model.outcome.filterNotNull().first() is BackupOutcome.Failed)
        model.busy.first { !it }
        model.confirmRestore()
        assertNull(model.restorePreview.value)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
    }

    @Test fun historyPreviewAndConfirmationReportObservedCountsWithoutInventingCompletions() = runTest {
        val history = BackupHistory(
            listOf(PlaybackHistoryEntity("local:kept", "Song", "Artist", "", "3:00", 0, null, null, 100, playCount = 7)),
            listOf(PlaybackEventEntity(trackKey = "local:kept", playedAt = 100), PlaybackEventEntity(trackKey = "local:kept", playedAt = 200)),
            listOf(ListeningSessionEntity("observed", "local:kept", null, "Artist", 100, 30_000, 180_000, "OPEN")),
        )
        val file = backupFile().apply {
            writeText(JSONObject().put("schemaVersion", 3).put("historyIncluded", true)
                .put("likedSongs", JSONArray()).put("playlists", JSONArray()).put("followedArtists", JSONArray())
                .apply { putBackupHistory(history) }.toString())
        }
        model.previewRestoreFrom(Uri.fromFile(file))
        val preview = model.restorePreview.filterNotNull().first()
        model.busy.first { !it }
        assertTrue(preview.includesHistory)
        assertEquals(1, preview.newHistoryTracks)
        assertEquals(2, preview.newHistoryEvents)
        assertEquals(1, preview.newListeningSessions)
        assertTrue(db.playbackHistoryDao().backupHistory().isEmpty())
        model.confirmRestore()
        val result = model.outcome.filterNotNull().first() as BackupOutcome.Restored
        model.busy.first { !it }
        assertTrue(result.includesHistory)
        assertEquals(1, result.historyTracks)
        assertEquals(2, result.historyEvents)
        assertEquals(1, result.listeningSessions)
        assertEquals("OPEN", db.playbackHistoryDao().backupSessions().single().outcome)
        assertEquals(30_000L, db.playbackHistoryDao().backupSessions().single().listenedMs)
    }

    @Test fun internalRecoveryCopyAlsoRequiresPreviewAndExplicitConfirmation() = runTest {
        repository.setIncludeHistoryEnabled(false); repository.setIncludeSettingsEnabled(false)
        repository.setAutomaticDestination(null)
        db.likedSongDao().like(LikedSongEntity("local:recovery", "Recovered", "Artist", "", "", 0, null, null, 1))
        assertTrue(repository.writeAutoBackup())
        db.likedSongDao().unlike("local:recovery")
        model.previewInternalBackup()
        assertEquals(1, model.restorePreview.filterNotNull().first().newLikedSongs)
        model.busy.first { !it }
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        model.confirmRestore()
        assertTrue(model.outcome.filterNotNull().first() is BackupOutcome.Restored)
        model.busy.first { !it }
        assertEquals("Recovered", db.likedSongDao().backupSnapshot().single().title)
    }

    private fun backupFile() = File(application.cacheDir, "preview-backup.json").apply {
        writeText("""{"schemaVersion":1,"likedSongs":[{"key":"local:kept","title":"Kept recording"}],"playlists":[]}""")
    }
}
