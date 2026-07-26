package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Grouping is by calendar day in the device's zone, not by elapsed hours - the distinction these
 * pin down is that 1am and 11pm on the same date belong to one group even though 22 hours separate
 * them, while 11pm and 1am across midnight do not, despite being two hours apart.
 */
class HistoryGroupingTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 25)

    private fun entryAt(dateTime: LocalDateTime, title: String, plays: Int = 1) = PlaybackHistoryEntity(
        key = "$title|artist",
        title = title,
        artist = "artist",
        album = "album",
        duration = "3:00",
        gradientIndex = 0,
        imageUrl = null,
        streamUrl = null,
        playedAt = dateTime.atZone(zone).toInstant().toEpochMilli(),
        sourceId = null,
        sourceType = null,
        playCount = plays,
    )

    @Test
    fun `same calendar day groups together however far apart`() {
        val days = groupHistoryByDay(
            listOf(
                entryAt(LocalDateTime.of(2026, 7, 25, 23, 0), "late"),
                entryAt(LocalDateTime.of(2026, 7, 25, 1, 0), "early"),
            ),
            zone,
            today,
        )

        assertEquals(1, days.size)
        assertEquals("Today", days[0].label)
        assertEquals(listOf("late", "early"), days[0].entries.map { it.track.title })
    }

    @Test
    fun `two hours across midnight are two days`() {
        val days = groupHistoryByDay(
            listOf(
                entryAt(LocalDateTime.of(2026, 7, 25, 1, 0), "after"),
                entryAt(LocalDateTime.of(2026, 7, 24, 23, 0), "before"),
            ),
            zone,
            today,
        )

        assertEquals(listOf("Today", "Yesterday"), days.map { it.label })
    }

    @Test
    fun `days are newest first`() {
        val days = groupHistoryByDay(
            listOf(
                entryAt(LocalDateTime.of(2026, 7, 20, 12, 0), "oldest"),
                entryAt(LocalDateTime.of(2026, 7, 25, 12, 0), "newest"),
                entryAt(LocalDateTime.of(2026, 7, 24, 12, 0), "middle"),
            ),
            zone,
            today,
        )

        assertEquals(listOf("Today", "Yesterday", "20 July"), days.map { it.label })
    }

    @Test
    fun `an earlier year keeps its year in the label`() {
        val days = groupHistoryByDay(
            listOf(entryAt(LocalDateTime.of(2025, 12, 31, 12, 0), "old")),
            zone,
            today,
        )

        assertEquals("31 December 2025", days.single().label)
    }

    @Test
    fun `tracks within a day are newest first`() {
        val days = groupHistoryByDay(
            listOf(
                entryAt(LocalDateTime.of(2026, 7, 25, 9, 0), "morning"),
                entryAt(LocalDateTime.of(2026, 7, 25, 21, 0), "evening"),
                entryAt(LocalDateTime.of(2026, 7, 25, 15, 0), "afternoon"),
            ),
            zone,
            today,
        )

        assertEquals(listOf("evening", "afternoon", "morning"), days.single().entries.map { it.track.title })
    }

    @Test
    fun `play count is carried through`() {
        val days = groupHistoryByDay(
            listOf(entryAt(LocalDateTime.of(2026, 7, 25, 12, 0), "repeat", plays = 7)),
            zone,
            today,
        )

        assertEquals(7, days.single().entries.single().playCount)
    }

    @Test
    fun `no history is no days`() {
        assertEquals(emptyList<HistoryDay>(), groupHistoryByDay(emptyList(), zone, today))
    }
}
