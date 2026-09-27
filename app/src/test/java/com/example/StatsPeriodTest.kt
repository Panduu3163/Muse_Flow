package com.example

import org.junit.Assert.*
import org.junit.Test

class StatsPeriodTest {
    private val song = PlaybackHistoryEntity("song", "Song", "Artist", "Album", "3:00", 0, null, null, 0, playCount = 99)
    @Test fun weeklyStatsUseEventsNotLifetimeCounts() {
        val now = 100 * 86_400_000L
        val events = listOf(PlaybackEventEntity(trackKey = "song", playedAt = now - 1), PlaybackEventEntity(trackKey = "song", playedAt = now - 8 * 86_400_000L))
        assertEquals(1, historyForPeriod(listOf(song), events, StatsPeriod.Week, now).single().playCount)
        assertEquals(2, historyForPeriod(listOf(song), events, StatsPeriod.Month, now).single().playCount)
        assertEquals(99, historyForPeriod(listOf(song), events, StatsPeriod.AllTime, now).single().playCount)
    }
    @Test fun legacyHistoryDoesNotInventDatedPlays() {
        assertTrue(historyForPeriod(listOf(song), emptyList(), StatsPeriod.Week, 0).isEmpty())
    }
    @Test fun calendarWeekUsesLocalMondayBoundary() {
        val zone = java.time.ZoneId.systemDefault()
        val wed = java.time.LocalDate.of(2026, 9, 23).atStartOfDay(zone).toInstant().toEpochMilli()
        val sun = java.time.LocalDate.of(2026, 9, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = listOf(PlaybackEventEntity(trackKey = "song", playedAt = wed), PlaybackEventEntity(trackKey = "song", playedAt = sun))
        assertEquals(1, historyForPeriod(listOf(song), events, StatsPeriod.Week, wed + 1, StatsMode.Calendar).single().playCount)
        assertEquals(2, historyForPeriod(listOf(song), events, StatsPeriod.Week, wed + 1, StatsMode.Continuous).single().playCount)
    }
}
