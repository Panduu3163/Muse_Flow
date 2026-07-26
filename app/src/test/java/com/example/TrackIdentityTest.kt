package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard in front of Share and Start radio, both of which turn a track's id into something
 * external - a link someone else opens, and an API seed.
 *
 * The trap this covers: `Track.asTrackResult()` fabricates `"title|artist"` as the id when a
 * stored track has no `sourceId`, *and* defaults the source to YouTube Music. So a track can
 * report itself as a YouTube one while carrying an id YouTube has never heard of. Checking the
 * source alone - the obvious implementation - passes that track and produces a dead share link.
 */
class TrackIdentityTest {

    private fun track(id: String, source: MusicSource = MusicSource.YOUTUBE_MUSIC) = TrackResult(
        id = id,
        title = "Title",
        artist = "Artist",
        duration = "3:30",
        source = "Album",
        sourceType = source,
    )

    @Test
    fun `accepts a real video id`() {
        assertTrue(track("dQw4w9WgXcQ").hasRealVideoId())
    }

    @Test
    fun `accepts ids using the URL-safe punctuation`() {
        assertTrue(track("a_b-c1d2e3F").hasRealVideoId())
    }

    @Test
    fun `rejects the title-artist id asTrackResult synthesises`() {
        assertFalse(track("Some Song|Some Artist").hasRealVideoId())
    }

    /** The synthesised form is rejected even when it happens to be 11 characters long. */
    @Test
    fun `rejects an eleven-character synthesised id`() {
        assertFalse(track("Son|Artist1").hasRealVideoId())
    }

    @Test
    fun `rejects a local file regardless of its id`() {
        assertFalse(track("dQw4w9WgXcQ", MusicSource.LOCAL_DEVICE).hasRealVideoId())
    }

    @Test
    fun `rejects ids of the wrong length`() {
        assertFalse(track("tooShort").hasRealVideoId())
        assertFalse(track("waaaayTooLongToBeReal").hasRealVideoId())
    }

    @Test
    fun `rejects an empty id`() {
        assertFalse(track("").hasRealVideoId())
    }
}
