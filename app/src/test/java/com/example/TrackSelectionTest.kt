package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selection is positional, so what these pin down is the behaviour identity-keyed selection would
 * get wrong: duplicates stay independently selectable, and a selection that outlives the list it
 * indexes resolves to fewer tracks rather than throwing.
 */
class TrackSelectionTest {

    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `toggle adds then removes the same position`() {
        val once = emptySet<Int>().toggledSelection(2)
        assertEquals(setOf(2), once)
        assertEquals(emptySet<Int>(), once.toggledSelection(2))
    }

    @Test
    fun `toggle leaves other positions alone`() {
        assertEquals(setOf(0, 2), setOf(0, 1, 2).toggledSelection(1))
    }

    @Test
    fun `selected entries come back in list order, not selection order`() {
        assertEquals(listOf("a", "c"), list.atSelected(setOf(2, 0)))
    }

    /** The case identity-keyed selection cannot express: two copies, one ticked. */
    @Test
    fun `duplicate entries are selected independently`() {
        val withDuplicate = listOf("song", "other", "song")
        assertEquals(listOf("song"), withDuplicate.atSelected(setOf(2)))
        assertEquals(listOf("song", "song"), withDuplicate.atSelected(setOf(0, 2)))
    }

    /** A batch delete shortens the list a frame before the selection clears. */
    @Test
    fun `positions past the end are dropped rather than throwing`() {
        assertEquals(listOf("d"), list.atSelected(setOf(3, 9)))
        assertEquals(emptyList<String>(), list.atSelected(setOf(4)))
    }

    @Test
    fun `all indices covers the list and is empty for an empty one`() {
        assertEquals(setOf(0, 1, 2, 3), allSelectionIndices(4))
        assertEquals(emptySet<Int>(), allSelectionIndices(0))
        assertEquals(emptySet<Int>(), allSelectionIndices(-1))
    }

    @Test
    fun `everything selected is true only for full coverage`() {
        assertTrue(isEverythingSelected(setOf(0, 1, 2, 3), 4))
        assertFalse(isEverythingSelected(setOf(0, 1, 2), 4))
    }

    /** Stale positions must not fake full coverage by size alone. */
    @Test
    fun `stale positions do not count towards full coverage`() {
        assertFalse(isEverythingSelected(setOf(0, 1, 7, 8), 4))
    }

    @Test
    fun `nothing is everything-selected in an empty list`() {
        assertFalse(isEverythingSelected(emptySet(), 0))
    }
}
