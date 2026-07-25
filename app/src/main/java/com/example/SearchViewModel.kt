package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Search state for the Search screen.
 *
 * Results are held as a [UiState] so the screen can distinguish "still loading" from "loaded, but
 * genuinely nothing matched" - a distinction that matters more than usual here, because with the
 * extractor router in strict mode an empty result is real evidence about the selected backend
 * rather than something to paper over.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val router = MusicSearchRouter(application)
    private val history = SearchHistoryRepository.getInstance(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<TrackResult>>>(UiState.Success(emptyList()))
    val results: StateFlow<UiState<List<TrackResult>>> = _results.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    /** Which backend actually served the visible results, surfaced in the UI so the extractor
     * toggle's effect is observable rather than guesswork. */
    private val _activeBackend = MutableStateFlow(ExtractorPreference.default)
    val activeBackend: StateFlow<ExtractorBackend> = _activeBackend.asStateFlow()

    val recentQueries = history.observeRecent()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        // Debounced so a fast typist doesn't fire a request per keystroke.
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(250)
            _suggestions.value = runCatching { router.suggestions(newQuery) }.getOrDefault(emptyList())
        }
    }

    fun search(query: String = _query.value) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        _query.value = trimmed
        _suggestions.value = emptyList()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _results.value = UiState.Loading
            _activeBackend.value = StreamResolverRouter.activeBackend(getApplication())
            history.record(trimmed)

            _results.value = runCatching { router.searchTracks(trimmed) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { error ->
                        // Being offline is by far the most common failure and isn't something the
                        // user can act on from a stack-trace-flavoured message, so it gets plain
                        // language. Anything else still names the backend, which is what makes a
                        // genuine extractor problem diagnosable.
                        UiState.Error(
                            if (!isOnline(getApplication())) {
                                "Oops! You don't have internet. Connect and try again."
                            } else {
                                "${_activeBackend.value.label} search failed: " +
                                    (error.message ?: error::class.simpleName ?: "unknown error")
                            }
                        )
                    }
                )
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        suggestJob?.cancel()
        _query.value = ""
        _suggestions.value = emptyList()
        _results.value = UiState.Success(emptyList())
    }

    fun deleteRecent(query: String) = history.delete(query)
}
