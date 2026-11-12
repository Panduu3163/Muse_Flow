package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneralVideoSearchTest {
    private fun track(id: String) = TrackResult(
        id = id, title = id, artist = "Artist", duration = "3:00",
        source = "YouTube video", sourceType = MusicSource.YOUTUBE_MUSIC,
    )

    @Test fun generalVideosAppearAlongsideMusicResultsWithoutDuplicateIds() {
        val merged = interleaveTrackResults(
            music = listOf(track("music-one"), track("shared"), track("music-three")),
            videos = listOf(track("video-only"), track("shared"), track("video-two")),
        )
        assertEquals(listOf("music-one", "video-only", "shared", "music-three", "video-two"),
            merged.map { it.id })
    }
}
