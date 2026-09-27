package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * The full listening history, grouped by day.
 *
 * Reads the same table Home's "Recently played" shelf and Library's Top 50 read - this is the
 * unbounded view of it, so removing something here removes it from those too.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaybackHistoryRepository.getInstance(application)

    val query = MutableStateFlow("")
    val source = MutableStateFlow("All")
    val days: StateFlow<List<HistoryDay>> = combine(repository.observeAll(), repository.observeEvents(), query, source) { entries, events, query, source ->
        val filtered = entries.filter {
            (query.isBlank() || "${it.title} ${it.artist}".contains(query.trim(), ignoreCase = true)) &&
                (source == "All" || (it.sourceType == MusicSource.LOCAL_DEVICE.name) == (source == "Local"))
        }
        chronologicalHistory(filtered, events)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun forget(track: Track) = repository.forget(track)

    fun clear() = repository.clear()
}
