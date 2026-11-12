package com.example

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One history row. New playback events have a count of one; legacy aggregate rows retain the
 * earlier play count because the old database did not store individual timestamps.
 */
data class HistoryEntry(
    val track: Track,
    val playCount: Int,
)

/** One day's worth of listening history, in the order the screen renders it. */
data class HistoryDay(
    val label: String,
    val entries: List<HistoryEntry>,
)

/** Every new play gets its own chronological row. Legacy rows keep their last-known date. */
fun chronologicalHistory(history: List<PlaybackHistoryEntity>, events: List<PlaybackEventEntity>): List<HistoryDay> {
    val byKey = history.associateBy { it.key }
    val eventRows = events.mapNotNull { event ->
        byKey[event.trackKey]?.copy(playedAt = event.playedAt, playCount = 1)
    }
    val eventKeys = events.map { it.trackKey }.toSet()
    return groupHistoryByDay(eventRows + history.filter { it.key !in eventKeys })
}

/**
 * Buckets history into days, newest first.
 *
 * Grouping is by *calendar* day in the device's zone rather than by elapsed hours, because
 * "Yesterday" has to mean yesterday's date - something played at 1am and something played at 11pm
 * the same evening belong together, however many hours separate them.
 *
 * [today] is a parameter rather than read inside so the boundary cases stay testable without
 * waiting for midnight.
 */
fun groupHistoryByDay(
    entries: List<PlaybackHistoryEntity>,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): List<HistoryDay> = entries
    .groupBy { entry ->
        Instant.ofEpochMilli(entry.playedAt).atZone(zone).toLocalDate()
    }
    .toList()
    .sortedByDescending { (date, _) -> date }
    .map { (date, dayEntries) ->
        HistoryDay(
            label = dayLabel(date, today),
            entries = dayEntries
                .sortedByDescending { it.playedAt }
                .map { HistoryEntry(it.toTrack(), it.playCount) },
        )
    }

private val monthDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val monthDayYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

/** Relative wording for the two days people actually recognise, an explicit date for the rest -
 * "3 days ago" reads as arithmetic homework once it's more than a day old. The year is only shown
 * when it isn't the current one, which is the common case and just noise. */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> if (date.year == today.year) monthDay.format(date) else monthDayYear.format(date)
}
