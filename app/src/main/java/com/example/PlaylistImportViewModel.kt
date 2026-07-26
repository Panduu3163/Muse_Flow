package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where an import has got to. Matching is the slow part - one search per track - so it reports
 * progress rather than leaving the user watching a spinner of unknown length. */
sealed interface ImportState {
    data object Idle : ImportState
    data class Matching(val done: Int, val total: Int, val current: String) : ImportState
    data class Done(
        val playlistName: String,
        val imported: Int,
        /** Tracks with no confident YouTube match, named so the user can add them by hand. */
        val unmatched: List<String>,
    ) : ImportState

    data class Failed(val message: String) : ImportState
}

/**
 * Imports a playlist exported from another service as a CSV.
 *
 * The file supplies names only, so every track is searched on YouTube Music and matched by
 * [isLikelyMatch] - the same comparison the app already uses to reconcile results across sources.
 * A track that doesn't match confidently is reported rather than guessed at: silently importing
 * the wrong song is worse than saying which ones need a look.
 */
class PlaylistImportViewModel(application: Application) : AndroidViewModel(application) {

    private val router = MusicSearchRouter(application)
    private val playlistRepository = PlaylistRepository.getInstance(application)

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var importJob: Job? = null

    fun cancel() {
        importJob?.cancel()
        _state.value = ImportState.Idle
    }

    fun dismiss() {
        _state.value = ImportState.Idle
    }

    fun importFrom(uri: Uri, fallbackName: String) {
        if (importJob?.isActive == true) return

        importJob = viewModelScope.launch {
            val tracks = try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("Couldn't open that file")
                }
                parsePlaylistCsv(text)
            } catch (e: CsvFormatException) {
                _state.value = ImportState.Failed(e.message ?: "That file couldn't be read.")
                return@launch
            } catch (e: Exception) {
                _state.value = ImportState.Failed(e.message ?: "That file couldn't be read.")
                return@launch
            }

            if (tracks.isEmpty()) {
                _state.value = ImportState.Failed("No tracks found in that file.")
                return@launch
            }

            val name = fallbackName.substringBeforeLast('.').ifBlank { "Imported playlist" }
            val playlistId = playlistRepository.create(name)

            val unmatched = mutableListOf<String>()
            val matched = mutableListOf<Track>()

            tracks.forEachIndexed { index, csvTrack ->
                _state.value = ImportState.Matching(index, tracks.size, csvTrack.title)

                val match = findMatch(csvTrack)
                if (match == null) {
                    unmatched += "${csvTrack.title} — ${csvTrack.artist}"
                } else {
                    matched += match.toPlayableTrack(match.id.hashCode())
                }
            }

            // One write at the end rather than per track: the playlist appears complete instead of
            // filling in a row at a time behind the progress dialog.
            playlistRepository.addTracks(playlistId, matched)

            _state.value = ImportState.Done(name, matched.size, unmatched)
        }
    }

    /**
     * Finds the YouTube track a CSV row refers to.
     *
     * Searched as "title artist" because that's what a person would type, then confirmed with
     * [isLikelyMatch] against every result rather than trusting the first hit - YouTube happily
     * returns covers, live versions and reaction videos above the actual track.
     */
    private suspend fun findMatch(csvTrack: CsvTrack): TrackResult? {
        val results = runCatching {
            router.searchTracks("${csvTrack.title} ${csvTrack.artist}")
        }.getOrNull().orEmpty()

        return results.firstOrNull { candidate ->
            isLikelyMatch(csvTrack.title, csvTrack.artist, candidate.title, candidate.artist)
        }
    }
}
