package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The full listening history, grouped by day.
 *
 * Reads the same table Home's "Recently played" shelf and Library's Top 50 read - this is the
 * unbounded view of it, so removing something here removes it from those too.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaybackHistoryRepository.getInstance(application)

    val days: StateFlow<List<HistoryDay>> = repository.observeAll()
        .map { groupHistoryByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun forget(track: Track) = repository.forget(track)

    fun clear() = repository.clear()
}
