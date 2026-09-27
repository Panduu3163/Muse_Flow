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

/**
 * Sums [Track.duration] ("m:ss" strings) into a human label - "2 hr 14 min" past an hour, "14 min"
 * under one, "Less than a minute" for a handful of very short tracks. A track whose duration
 * didn't parse contributes zero rather than throwing or dropping the whole total.
 */
fun List<Track>.totalDurationLabel(): String {
    val totalSeconds = sumOf { track ->
        val parts = track.duration.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        minutes * 60 + seconds
    }
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        minutes > 0 -> "$minutes min"
        else -> "Less than a minute"
    }
}
