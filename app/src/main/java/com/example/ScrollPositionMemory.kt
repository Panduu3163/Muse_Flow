package com.example

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
 * Process-wide and unbounded on purpose: this only ever holds a handful of (Int, Int) pairs - one
 * per section/playlist the user has actually scrolled - never anything large enough to matter.
 */
object ScrollPositionMemory {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()

    fun save(key: String, index: Int, offset: Int) {
        positions[key] = index to offset
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
    val listState = rememberLazyListState()
    var hasRestored by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, isContentReady) {
        if (hasRestored || !isContentReady) return@LaunchedEffect
        ScrollPositionMemory.restore(key)?.let { (index, offset) ->
            runCatching { listState.scrollToItem(index, offset) }
        }
        hasRestored = true
    }

    LaunchedEffect(key, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> ScrollPositionMemory.save(key, index, offset) }
    }

    return listState
}
