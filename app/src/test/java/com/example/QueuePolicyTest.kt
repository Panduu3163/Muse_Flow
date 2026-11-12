package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePolicyTest {
    @Test fun largeListKeepsSelectedSongAndBothSides() {
        val (window, selected) = queueWindowAround((0 until 100).toList(), 62)
        assertEquals(51, window.size)
        assertEquals(62, window[selected])
        assertEquals(37, window.first())
        assertEquals(87, window.last())
    }

    @Test fun endsStayWithinLimit() {
        val songs = (0 until 100).toList()
        assertEquals(0 to 0, queueWindowAround(songs, 0).let { it.first.first() to it.second })
        val (lastWindow, lastIndex) = queueWindowAround(songs, 99)
        assertEquals(49, lastWindow.first())
        assertEquals(50, lastIndex)
    }

    @Test fun clearTargetsEveryOtherTimelineItemInSafeOrder() {
        assertEquals(listOf(4, 3, 1, 0), indicesExceptCurrent(5, 2))
        assertEquals(emptyList<Int>(), indicesExceptCurrent(1, 0))
    }

    @Test fun overflowNeverTargetsCurrentTrack() {
        assertEquals(51, queueOverflowRemovalIndex(52, 12))
        assertEquals(0, queueOverflowRemovalIndex(52, 49))
    }

    @Test fun shortLocalListStaysFirstThenFillsOnlyFromEligibleCollection() {
        val result = fillLocalQueue((10..29).toList(), (0..60).toList()) { it }
        assertEquals(51, result.size)
        assertEquals((10..29).toList(), result.take(20))
        assertEquals(51, result.distinct().size)
    }

    @Test fun aSmallOfflineCollectionCyclesWithoutIntroducingRemoteSongs() {
        val result = fillLocalQueue(listOf("download-b", "download-c"),
            listOf("download-a", "download-b", "download-c")) { it }
        assertEquals(51, result.size)
        assertEquals(listOf("download-b", "download-c", "download-a"), result.take(3))
        assertEquals(setOf("download-a", "download-b", "download-c"), result.toSet())
    }
}
