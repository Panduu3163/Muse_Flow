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

enum class StatsPeriod(val label: String, val days: Int?) {
    AllTime("All time", null), Week("1 week", 7), Month("1 month", 30), Quarter("3 months", 90)
}

enum class StatsMode { Continuous, Calendar }

internal fun historyForPeriod(history: List<PlaybackHistoryEntity>, events: List<PlaybackEventEntity>, period: StatsPeriod, now: Long, mode: StatsMode = StatsMode.Continuous): List<PlaybackHistoryEntity> {
    val days = period.days ?: return history
    val start = if (mode == StatsMode.Continuous) now - days * 86_400_000L else {
        val date = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val first = when (period) {
            StatsPeriod.Week -> date.with(java.time.DayOfWeek.MONDAY)
            StatsPeriod.Month -> date.withDayOfMonth(1)
            StatsPeriod.Quarter -> date.withDayOfMonth(1).withMonth(((date.monthValue - 1) / 3) * 3 + 1)
            StatsPeriod.AllTime -> date
        }
        first.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val counts = events.filter { it.playedAt in start..now }.groupingBy { it.trackKey }.eachCount()
    return history.mapNotNull { entry -> counts[entry.key]?.let { entry.copy(playCount = it) } }
}

/** One artist's aggregate listening stats, ranked by total plays across every track of theirs
 * with history - [PlaybackHistoryEntity] only tracks per-track play counts, so this is a rollup
 * over that, not a separately recorded stat. */
data class ArtistStat(val name: String, val plays: Int, val imageUrl: String?, val artistId: String?)

/**
 * Listening insights derived from aggregate history and the per-play event log. Older plays remain
 * visible in All time because their original timestamps cannot be recovered; rolling/calendar
 * periods count only plays recorded since the event log was introduced.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = PlaybackHistoryRepository.getInstance(application)

    val period = MutableStateFlow(StatsPeriod.AllTime)
    val mode = MutableStateFlow(StatsMode.Continuous)
    fun selectPeriod(value: StatsPeriod) { period.value = value }
    fun selectMode(value: StatsMode) { mode.value = value }

    private val history: StateFlow<List<PlaybackHistoryEntity>> = combine(
        historyRepository.observeAll(), historyRepository.observeEvents(), period, mode
    ) { history, events, period, mode -> historyForPeriod(history, events, period, System.currentTimeMillis(), mode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPlays: StateFlow<Int> = history
        .map { entries -> entries.sumOf { it.playCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uniqueTrackCount: StateFlow<Int> = history
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uniqueArtistCount: StateFlow<Int> = history
        .map { entries -> entries.map { it.artist }.filter(String::isNotBlank).distinct().size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uniqueAlbumCount: StateFlow<Int> = history
        .map { entries -> entries.map { it.album }.filter(String::isNotBlank).distinct().size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Estimated minutes from each track's duration times its counted starts. This is not actual
     * listened time because a play may be skipped before the track ends. */
    val totalListeningMinutes: StateFlow<Long> = history
        .map { entries -> entries.sumOf { it.durationSeconds() * it.playCount } / 60L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val topArtists: StateFlow<List<ArtistStat>> = history
        .map { entries ->
            entries.filter { it.artist.isNotBlank() }
                .groupBy { it.artist }
                .map { (name, tracks) ->
                    ArtistStat(
                        name = name,
                        plays = tracks.sumOf { it.playCount },
                        imageUrl = tracks.firstOrNull { it.imageUrl != null }?.imageUrl,
                        artistId = tracks.firstNotNullOfOrNull { it.artistId },
                    )
                }
                .sortedByDescending { it.plays }
                .take(10)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTracks: StateFlow<List<Track>> = history
        .map { entries -> entries.sortedByDescending { it.playCount }.take(10).map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private fun PlaybackHistoryEntity.durationSeconds(): Int {
    val parts = duration.split(":")
    val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return minutes * 60 + seconds
}
