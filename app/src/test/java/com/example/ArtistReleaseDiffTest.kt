package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistReleaseDiffTest {

    @Test
    fun `an empty baseline never reports new releases - first check just seeds it`() {
        val result = newReleaseTrackIds(knownTrackIds = emptySet(), currentTrackIds = listOf("a", "b", "c"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a track id not in the known set is reported as new`() {
        val result = newReleaseTrackIds(knownTrackIds = setOf("a", "b"), currentTrackIds = listOf("a", "b", "c"))
        assertEquals(setOf("c"), result)
    }

    @Test
    fun `no new releases when the tracklist is unchanged`() {
        val result = newReleaseTrackIds(knownTrackIds = setOf("a", "b", "c"), currentTrackIds = listOf("a", "b", "c"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a reordered tracklist is not treated as new releases`() {
        val result = newReleaseTrackIds(knownTrackIds = setOf("a", "b", "c"), currentTrackIds = listOf("c", "a", "b"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `a track dropping out of the current list is not itself reported`() {
        // Only additions are "new releases" - a track disappearing (removed, region-locked) is a
        // separate concern the diff function isn't responsible for.
        val result = newReleaseTrackIds(knownTrackIds = setOf("a", "b", "c"), currentTrackIds = listOf("a", "b"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple simultaneous new tracks are all reported`() {
        val result = newReleaseTrackIds(knownTrackIds = setOf("a"), currentTrackIds = listOf("a", "b", "c"))
        assertEquals(setOf("b", "c"), result)
    }
}
