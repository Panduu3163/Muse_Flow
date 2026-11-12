package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
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
class PlaylistImportRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = Uri.parse("content://test/playlist")
    private val csv = "Title,Artist\nFirst,Artist\nSecond,Artist"
    private fun match(id: String) = TrackResult(id, "Song $id", "Artist", "3:00", "YouTube Music", MusicSource.YOUTUBE_MUSIC)
    private fun model(read: suspend (Uri) -> String = { csv }, find: suspend (CsvTrack) -> TrackResult? = { match(it.title) },
                      save: suspend (String, List<Track>) -> Long) = PlaylistImportViewModel(
        ApplicationProvider.getApplicationContext<Application>(), PlaylistImportOperations(read, find, save))
    @Before fun mainDispatcher() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private suspend fun waitForResult(model: PlaylistImportViewModel) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (model.state.value.isWorking) { dispatcher.scheduler.runCurrent(); delay(5) } }
        }
    }

    @Test fun cancellationDuringMatchingCreatesNothingAndAllowsNewRequest() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var saves = 0
        val vm = model(find = { entered.complete(Unit); release.await(); match(it.title) }) { _, _ -> saves++; 1 }
        vm.importFrom(uri, "Mix.csv")
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!entered.isCompleted) { dispatcher.scheduler.runCurrent(); delay(5) } } }
        assertEquals(0, saves)
        vm.cancel(); release.complete(Unit); runCurrent()
        assertEquals(ImportState.Idle, vm.state.value); assertEquals(0, saves)
    }

    @Test fun noMatchesDoesNotCreateAnEmptyPlaylist() = runTest(dispatcher) {
        var saves = 0
        val vm = model(find = { null }) { _, _ -> saves++; 1 }
        vm.importFrom(uri, "Mix.csv"); waitForResult(vm)
        val result = vm.state.value as ImportState.Done
        assertEquals(0, saves); assertFalse(result.playlistCreated); assertEquals(2, result.unmatched.size)
    }

    @Test fun missingMetadataRowsAreReportedWithoutCreatingAnEmptyPlaylist() = runTest(dispatcher) {
        var saves = 0
        val vm = model(read = { "Title,Artist\n,Artist\nSong," }) { _, _ -> saves++; 1 }
        vm.importFrom(uri, "Broken.csv"); waitForResult(vm)
        val result = vm.state.value as ImportState.Done
        assertEquals(2, result.invalidRows); assertFalse(result.playlistCreated); assertEquals(0, saves)
    }

    @Test fun matchingFailureLeavesNoPartialPlaylist() = runTest(dispatcher) {
        var saves = 0; var attempts = 0
        val vm = model(find = { if (++attempts == 2) error("Connection lost") else match(it.title) }) { _, _ -> saves++; 1 }
        vm.importFrom(uri, "Mix.csv"); waitForResult(vm)
        assertEquals(0, saves); assertEquals(ImportState.Failed("Connection lost"), vm.state.value)
    }

    @Test fun completeMatchingCommitsOneOrderedDeduplicatedBatch() = runTest(dispatcher) {
        var saved: List<Track> = emptyList(); var saves = 0
        val vm = model(find = { match("abcdefghijk") }) { name, tracks -> assertEquals("Mix", name); saved = tracks; saves++; 1 }
        vm.importFrom(uri, "Mix.csv"); vm.importFrom(uri, "Duplicate.csv"); waitForResult(vm)
        val result = vm.state.value as ImportState.Done
        assertEquals(1, saves); assertEquals(1, saved.size); assertEquals(1, result.imported); assertEquals(1, result.duplicates)
    }

    @Test fun savingCannotBeReportedCancelledAfterCommitBegins() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var saved = false
        val vm = model { _, _ -> entered.complete(Unit); release.await(); saved = true; 1 }
        vm.importFrom(uri, "Mix.csv")
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!entered.isCompleted) { dispatcher.scheduler.runCurrent(); delay(5) } } }
        vm.cancel(); vm.dismiss(); assertEquals(ImportState.Saving, vm.state.value)
        release.complete(Unit); waitForResult(vm)
        assertTrue(saved); assertTrue(vm.state.value is ImportState.Done)
    }

    @Test fun transactionFailureBecomesRecoverableFailureInsteadOfSuccess() = runTest(dispatcher) {
        val vm = model { _, _ -> error("Not enough storage") }
        vm.importFrom(uri, "Mix.csv"); waitForResult(vm)
        assertEquals(ImportState.Failed("Not enough storage"), vm.state.value)
    }

    @Test fun oversizedAndInvalidUtf8FilesAreRejectedBeforeMatching() = runTest {
        assertTrue(runCatching { readPlaylistImportText(ByteArrayInputStream(ByteArray(MAX_PLAYLIST_IMPORT_BYTES + 1))) }.isFailure)
        assertTrue(runCatching { readPlaylistImportText(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }.isFailure)
    }
}
