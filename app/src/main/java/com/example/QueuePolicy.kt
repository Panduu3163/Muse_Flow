package com.example

/** Hard limit for the active playback timeline, including the current song. */
internal const val MAX_QUEUE_ITEMS = 51

/** The source rules survive queue persistence and connectivity changes. */
internal enum class QueueOrigin { ONLINE, SEARCH_RADIO, OFFLINE_DOWNLOADS, ON_DEVICE }

/** A search hit expresses one recording choice; a collection tap expresses its source order. */
internal fun initialPlaybackQueue(
    selected: TrackResult,
    source: List<TrackResult>,
    origin: QueueOrigin,
): List<TrackResult> = if (origin == QueueOrigin.SEARCH_RADIO) listOf(selected)
    else source.ifEmpty { listOf(selected) }

internal fun playbackQueueOrigin(
    sourceType: MusicSource,
    online: Boolean,
    fromSearch: Boolean,
): QueueOrigin = when {
    sourceType == MusicSource.LOCAL_DEVICE -> QueueOrigin.ON_DEVICE
    !online -> QueueOrigin.OFFLINE_DOWNLOADS
    fromSearch -> QueueOrigin.SEARCH_RADIO
    else -> QueueOrigin.ONLINE
}

/** Search radio must not fabricate a full queue by cloning the chosen song on lookup failure. */
internal fun canRecycleQueueToFill(origin: String?): Boolean = origin != QueueOrigin.SEARCH_RADIO.name

/** Keep the selected list intact, add other eligible songs, then cycle only if the local
 * collection has fewer than 51 songs. No remote item can enter an offline/device queue. */
internal fun <T, K> fillLocalQueue(
    source: List<T>,
    additional: List<T>,
    key: (T) -> K,
): List<T> {
    if (source.isEmpty()) return emptyList()
    val merged = source.take(MAX_QUEUE_ITEMS).toMutableList()
    val seen = merged.map(key).toMutableSet()
    additional.forEach { item ->
        if (merged.size < MAX_QUEUE_ITEMS && seen.add(key(item))) merged += item
    }
    if (merged.isEmpty()) return emptyList()
    val cycle = merged.toList()
    var cursor = 0
    while (merged.size < MAX_QUEUE_ITEMS) merged += cycle[cursor++ % cycle.size]
    return merged
}

/** Keep the chosen track and as much context on both sides as the timeline can hold. */
internal fun <T> queueWindowAround(items: List<T>, selectedIndex: Int): Pair<List<T>, Int> {
    if (items.isEmpty()) return emptyList<T>() to 0
    val selected = selectedIndex.coerceIn(items.indices)
    if (items.size <= MAX_QUEUE_ITEMS) return items to selected
    val first = (selected - MAX_QUEUE_ITEMS / 2)
        .coerceIn(0, items.size - MAX_QUEUE_ITEMS)
    return items.subList(first, first + MAX_QUEUE_ITEMS) to selected - first
}

/** Clear means only the current song remains; shuffle and repeat cannot reveal hidden rows. */
internal fun indicesExceptCurrent(itemCount: Int, currentIndex: Int): List<Int> =
    (0 until itemCount).filter { it != currentIndex }.asReversed()

/** If concurrent queue edits exceed the cap, discard the farthest edge, never the playing song. */
internal fun queueOverflowRemovalIndex(itemCount: Int, currentIndex: Int): Int =
    if (currentIndex >= itemCount / 2) 0 else itemCount - 1
