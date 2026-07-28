package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One artist's aggregate listening stats, ranked by total plays across every track of theirs
 * with history - [PlaybackHistoryEntity] only tracks per-track play counts, so this is a rollup
 * over that, not a separately recorded stat. */
data class ArtistStat(val name: String, val plays: Int, val imageUrl: String?, val artistId: String?)

/**
 * Listening insights, entirely derived from [PlaybackHistoryRepository] - no new data collection.
 *
 * Deliberately all-time only, not broken down by day/week/month: [PlaybackHistoryEntity] stores one
 * row per track with a running [PlaybackHistoryEntity.playCount] and its *most recent* [PlaybackHistoryEntity.playedAt],
 * not a timestamped event per play - there's no record of *when* each of those plays happened, so a
 * "this week vs last week" breakdown would have to be invented rather than computed. Echo's own
 * `StatPeriod` needs a per-play event log this app doesn't keep; adding one just for a period filter
 * would be a real schema change for a nice-to-have, not a bug fix.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = PlaybackHistoryRepository.getInstance(application)

    private val history: StateFlow<List<PlaybackHistoryEntity>> = historyRepository.observeAll()
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

    /** Total minutes across every recorded play, estimated from each track's own duration times how
     * many times it's played - the only honest "listening time" this data supports, since it isn't
     * timestamped per play (see the class doc). */
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
