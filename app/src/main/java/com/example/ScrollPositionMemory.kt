package com.example

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Remembers each list's last scroll position across navigating away and back - e.g. tapping a
 * track to open Now Playing, then pressing back, used to always land back at the very top of
 * Liked/Downloads/Top 50/On device/a playlist/an artist's songs instead of where the tapped song
 * actually was.
 *
 * A plain in-memory singleton rather than relying on Navigation Compose's own per-back-stack-entry
 * `rememberSaveable` restoration for [LazyListState]: that mechanism didn't reliably survive the
 * round trip to Now Playing and back in practice, and this sidesteps the question of exactly why
 * by not depending on it at all. Keyed by a caller-supplied stable string (a playlist id, a
 * `LibrarySection` name, ...) - not tied to any one screen's shape.
 *
 * Process-wide and bounded to the latest 128 lists, including Search queries. Stores only
 * positions, never result objects or artwork.
 */
object ScrollPositionMemory {
    private val positions = LinkedHashMap<String, Pair<Int, Int>>()

    fun save(key: String, index: Int, offset: Int) {
        positions.remove(key)
        positions[key] = index.coerceAtLeast(0) to offset.coerceAtLeast(0)
        while (positions.size > 128) positions.remove(positions.keys.first())
    }

    fun restore(key: String): Pair<Int, Int>? = positions[key]
}

/**
 * A [LazyListState] that saves its position into [ScrollPositionMemory] continuously (not just at
 * some one dispose-time moment - see below for why) and restores it once, the first time
 * [isContentReady] turns true after this composable (re-)enters composition under [key].
 *
 * Two real gotchas a naive first attempt at this ran into, both worth spelling out since they're
 * easy to reintroduce:
 *
 * 1. Saving only in a `DisposableEffect`'s `onDispose` depends on that effect actually firing, and
 *    firing before the value is read back - a single missed/delayed dispose (e.g. a destination
 *    Navigation Compose keeps alive slightly differently than expected) silently loses the
 *    position with no fallback. Saving continuously via `snapshotFlow` instead means the last
 *    known-good position is already stored well before any dispose could even happen.
 *
 * 2. Seeding the restored position through `rememberLazyListState`'s own
 *    `initialFirstVisibleItemIndex`/`initialFirstVisibleItemScrollOffset` constructor params looks
 *    right, but those only apply at the exact moment the state is *constructed* - if the list this
 *    state backs is still empty on that first frame (a `collectAsState()`/Flow-backed track list
 *    that hasn't emitted its real value yet), `LazyColumn` clamps the state to index 0 for that
 *    zero-item layout pass, and that clamp sticks even once real items arrive a frame later - the
 *    state has no memory of what it originally wanted to scroll to. Explicitly calling
 *    `scrollToItem` once the real content is actually present (gated on [isContentReady], not on
 *    "the state was just created") sidesteps this entirely.
 */
@Composable
fun rememberRestoredLazyListState(key: String, isContentReady: Boolean): LazyListState {
    // Each section needs its own state instance. Reusing one state while the LazyColumn swaps
    // content can measure the new section at the previous section's index for a frame, leaving
    // rows drawn under the header and preserving a stale item composition after a tab change.
    val listState = remember(key) { LazyListState() }
    var hasRestored by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, isContentReady) {
        if (hasRestored || !isContentReady) return@LaunchedEffect
        ScrollPositionMemory.restore(key)?.let { (index, offset) ->
            runCatching { listState.scrollToItem(index, offset) }
        }
        hasRestored = true
    }

    // Do not save the new state's initial (0, 0) before its saved position has been restored.
    LaunchedEffect(key, listState, hasRestored) {
        if (!hasRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> ScrollPositionMemory.save(key, index, offset) }
    }

    return listState
}

@Composable
fun rememberRestoredLazyGridState(key: String, isContentReady: Boolean): LazyGridState {
    val state = remember(key) { LazyGridState() }
    var restored by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, isContentReady) {
        if (restored || !isContentReady) return@LaunchedEffect
        ScrollPositionMemory.restore("grid:$key")?.let { (index, offset) -> state.scrollToItem(index, offset) }
        restored = true
    }
    LaunchedEffect(key, state, restored) {
        if (restored) snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> ScrollPositionMemory.save("grid:$key", index, offset) }
    }
    return state
}
