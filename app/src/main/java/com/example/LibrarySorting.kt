package com.example

/** How a plain track list (Liked/Downloaded/Cached/Top 50/Local) can be sorted - [DEFAULT] is
 * whichever order the underlying query already returns (most recently liked/downloaded/played
 * first, depending on the section), not a client-side sort of its own. */
enum class TrackSortOption(val label: String) {
    DEFAULT("Default"),
    TITLE("Title"),
    ARTIST("Artist")
}

fun List<Track>.sortedByLibraryOption(option: TrackSortOption, ascending: Boolean): List<Track> {
    if (option == TrackSortOption.DEFAULT) return if (ascending) this else asReversed()
    val comparator = when (option) {
        TrackSortOption.TITLE -> compareBy<Track> { it.title.lowercase() }
        TrackSortOption.ARTIST -> compareBy<Track> { it.artist.lowercase() }
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

fun List<PlaylistEntity>.sortedByLibraryOption(option: PlaylistSortOption, ascending: Boolean): List<PlaylistEntity> {
    if (option == PlaylistSortOption.DEFAULT) return if (ascending) asReversed() else this
    val comparator = compareBy<PlaylistEntity> { it.name.lowercase() }
    return if (ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
}
