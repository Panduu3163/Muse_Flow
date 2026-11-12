package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Matching(val done: Int, val total: Int, val current: String) : ImportState
    data object Saving : ImportState
    data class Done(val playlistName: String, val imported: Int, val unmatched: List<String>,
                    val playlistCreated: Boolean = imported > 0, val duplicates: Int = 0, val invalidRows: Int = 0) : ImportState
    data class Failed(val message: String) : ImportState
    val isWorking: Boolean get() = this is Reading || this is Matching || this is Saving
}

internal const val MAX_PLAYLIST_IMPORT_BYTES = 8 * 1024 * 1024
internal const val MAX_PLAYLIST_IMPORT_TRACKS = 5_000

internal class PlaylistImportOperations(
    val read: suspend (Uri) -> String,
    val match: suspend (CsvTrack) -> TrackResult?,
    val save: suspend (String, List<Track>) -> Long,
)

/** Matching never writes a playlist. Only the final, non-cancellable Room transaction commits. */
class PlaylistImportViewModel internal constructor(application: Application, private val operations: PlaylistImportOperations) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, playlistImportOperations(application))
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state = _state.asStateFlow()
    private var importJob: Job? = null
    private var generation = 0L

    fun cancel() {
        // Once Saving is visible the transaction finishes; never report committed changes undone.
        if (_state.value is ImportState.Saving) return
        generation++
        importJob?.cancel()
        importJob = null
        _state.value = ImportState.Idle
    }

    fun dismiss() { if (!_state.value.isWorking) _state.value = ImportState.Idle }

    fun importFrom(uri: Uri, fallbackName: String) {
        if (importJob?.isActive == true) return
        val operation = ++generation
        _state.value = ImportState.Reading
        importJob = viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val text = operations.read(uri)
                    check(text.toByteArray(Charsets.UTF_8).size <= MAX_PLAYLIST_IMPORT_BYTES) { "That playlist is too large. Choose a file up to 8 MiB." }
                    (if (isM3uFileName(fallbackName)) PlaylistParseResult(parsePlaylistM3u(text)) else parsePlaylistCsvReport(text)).also {
                        check(it.tracks.size <= MAX_PLAYLIST_IMPORT_TRACKS) { "That playlist has too many tracks. Split it into files of up to 5,000 tracks." }
                    }
                }
                ensureActive()
                val name = fallbackName.substringBeforeLast('.').ifBlank { "Imported playlist" }.take(512)
                val tracks = parsed.tracks
                if (tracks.isEmpty()) {
                    check(parsed.skippedRows > 0) { "No tracks found in that file." }
                    _state.value = ImportState.Done(name, 0, emptyList(), invalidRows = parsed.skippedRows)
                    return@launch
                }
                val unmatched = mutableListOf<String>()
                val matched = mutableListOf<Track>()
                tracks.forEachIndexed { index, track ->
                    ensureActive()
                    _state.value = ImportState.Matching(index, tracks.size, track.title)
                    val match = operations.match(track)
                    ensureActive()
                    if (match == null) unmatched += "${track.title} — ${track.artist}"
                    else matched += match.toPlayableTrack(match.id.hashCode())
                }
                ensureActive()
                if (operation != generation) return@launch
                val unique = matched.distinctBy { it.downloadKey() }
                if (unique.isNotEmpty()) {
                    _state.value = ImportState.Saving
                    withContext(NonCancellable) { operations.save(name, unique) }
                }
                _state.value = ImportState.Done(name, unique.size, unmatched.toList(), duplicates = matched.size - unique.size, invalidRows = parsed.skippedRows)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (operation == generation) _state.value = ImportState.Failed(error.message ?: "Import failed. Check your connection or storage and retry.")
            }
        }
    }
}

private fun playlistImportOperations(application: Application): PlaylistImportOperations {
    val router = MusicSearchRouter(application)
    return PlaylistImportOperations(
        read = { uri -> withContext(Dispatchers.IO) {
            application.contentResolver.openInputStream(uri)?.use { readPlaylistImportText(it) }
                ?: error("Couldn't open that file. Select it again.")
        } },
        match = { track ->
            check(isOnline(application)) { "You are offline. Connect and retry; no playlist was created." }
            // Search failures abort before commit, rather than masquerading as unmatched songs.
            router.searchTracks("${track.title} ${track.artist}").firstOrNull {
                isLikelyMatch(track.title, track.artist, it.title, it.artist)
            }
        },
        save = { name, tracks -> PlaylistRepository.getInstance(application).createWithTracks(name, tracks) },
    )
}

internal suspend fun readPlaylistImportText(input: java.io.InputStream): String {
    val bytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        check(count <= MAX_PLAYLIST_IMPORT_BYTES - bytes.size()) { "That playlist is too large. Choose a file up to 8 MiB." }
        bytes.write(buffer, 0, count)
    }
    return try { bytes.toByteArray().decodeToString(throwOnInvalidSequence = true) }
        catch (_: java.nio.charset.CharacterCodingException) { error("This file is not valid UTF-8 text. Export it as UTF-8 and retry.") }
}
