package com.example

/** How a plain track list (Liked/Downloaded/Cached/Top 50/Local) can be sorted - [DEFAULT] is
 * whichever order the underlying query already returns (most recently liked/downloaded/played
 * first, depending on the section), not a client-side sort of its own. */
enum class TrackSortOption(val label: String) {
    DEFAULT("Date added"),
    TITLE("Name"),
    ARTIST("Artist"),
    DURATION("Play time"),
}

fun List<Track>.sortedByLibraryOption(option: TrackSortOption, ascending: Boolean): List<Track> {
    if (option == TrackSortOption.DEFAULT) return if (ascending) this else asReversed()
    val comparator = when (option) {
        TrackSortOption.TITLE -> compareBy<Track> { it.title.lowercase() }
        TrackSortOption.ARTIST -> compareBy<Track> { it.artist.lowercase() }
        TrackSortOption.DURATION -> compareBy<Track> { parseDurationToSeconds(it.duration) }
        TrackSortOption.DEFAULT -> return this
    }
    return if (ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
}

/** How the Playlists grid can be sorted. [DEFAULT] is creation order (newest first), same as
 * [PlaylistRepository.observeAll]'s own query. */
enum class PlaylistSortOption(val label: String) {
    DEFAULT("Date created"),
    NAME("Name")
}

/**
 * Pinned playlists sort first regardless of [option] - a pin is an override on top of sort, not a
 * sort option of its own, matching [PlaylistDao.observeAll]'s own ordering. Everything else is
 * sorted by [option] within the pinned/unpinned groups.
 */
fun List<PlaylistEntity>.sortedByLibraryOption(option: PlaylistSortOption, ascending: Boolean): List<PlaylistEntity> {
    val (pinned, rest) = partition { it.isPinned }
    val comparator = when (option) {
        PlaylistSortOption.DEFAULT -> compareBy<PlaylistEntity> { it.createdAt }.let {
            if (ascending) it else it.reversed()
        }
        PlaylistSortOption.NAME -> compareBy<PlaylistEntity> { it.name.lowercase() }.let {
            if (ascending) it else it.reversed()
        }
    }
    return pinned.sortedWith(comparator) + rest.sortedWith(comparator)
}

/** Summarizes known durations without presenting missing metadata as zero play time. */
fun List<Track>.totalDurationLabel(): String = durationSummaryLabel(map { it.duration })

internal fun durationSummaryLabel(durations: Iterable<String?>): String {
    var count = 0
    var known = 0
    var totalSeconds = 0L
    for (duration in durations) {
        count++
        val parts = duration?.trim()?.split(":").orEmpty()
        if (parts.size !in 2..3 || parts.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) continue
        val values = parts.map { it.toLongOrNull() }
        if (values.any { it == null } || values.last()!! > 59L ||
            (parts.size == 3 && values[1]!! > 59L)) continue
        val seconds = try {
            values.fold(0L) { value, part -> Math.addExact(Math.multiplyExact(value, 60L), part!!) }
        } catch (_: ArithmeticException) { continue }
        totalSeconds = try { Math.addExact(totalSeconds, seconds) }
            catch (_: ArithmeticException) { return "Duration unavailable" }
        known++
    }
    if (count == 0) return "0 min"
    if (known == 0) return "Duration unavailable"
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val label = when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        minutes > 0 -> "$minutes min"
        known < count -> "$totalSeconds sec"
        else -> "Less than a minute"
    }
    return if (known < count) "At least $label" else label
}
