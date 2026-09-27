package com.example

import org.junit.Assert.*
import org.junit.Test

class VideoLinkTest {
    @Test fun acceptsWatchShortAndLiveLinks() {
        listOf("https://youtu.be/dQw4w9WgXcQ?t=20", "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=abc", "https://www.youtube.com/shorts/dQw4w9WgXcQ", "https://youtube.com/live/dQw4w9WgXcQ").forEach {
            assertEquals("dQw4w9WgXcQ", videoIdFromLink(it))
        }
    }
    @Test fun rejectsLookalikeHostsAndMalformedIds() {
        listOf("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ", "https://youtube.com/watch?v=bad", "some song title", "file:///watch?v=dQw4w9WgXcQ").forEach { assertNull(videoIdFromLink(it)) }
    }
}
