package com.example

/**
 * Multi-select bookkeeping, kept as pure set operations over **list positions**.
 *
 * Position rather than track identity on purpose. The app's own lists routinely contain the same
 * song twice - a phone's music folder holds duplicate files, a playlist can hold a track twice,
 * and a backend can return the same browseId twice - which is the same reason `LazyColumn` keys
 * here are positional (see the handoff's Compose notes). Selecting by `"title|artist"` would tick
 * both copies of a duplicate and would make "remove this one" ambiguous.
 *
 * The cost of positions is that they are only meaningful against the list that produced them, so a
 * screen must clear the selection whenever that list is re-ordered or swapped - which is why the
 * sort header is replaced by the selection bar rather than left live beside it.
 */

/** Adds [index] if absent, removes it if present. */
fun Set<Int>.toggledSelection(index: Int): Set<Int> =
    if (contains(index)) this - index else this + index

/**
 * The selected entries of this list, in list order.
 *
 * Out-of-range positions are dropped rather than throwing: a selection can outlive the list that
 * produced it by one frame when a batch action shortens it - deleting downloads, or removing from
 * a playlist - and a crash on the way to a cleared selection would be a poor trade.
 */
fun <T> List<T>.atSelected(indices: Set<Int>): List<T> =
    filterIndexed { index, _ -> indices.contains(index) }

/** Every position in a list of [count] items. */
fun allSelectionIndices(count: Int): Set<Int> =
    if (count <= 0) emptySet() else (0 until count).toSet()

/**
 * True when [indices] covers a non-empty list of [count] items.
 *
 * Drives whether the bar's select-all control selects everything or clears - one control with two
 * meanings, which is how every mail client behaves and saves a second icon in a crowded bar.
 */
fun isEverythingSelected(indices: Set<Int>, count: Int): Boolean =
    count > 0 && indices.size >= count && allSelectionIndices(count).all(indices::contains)
