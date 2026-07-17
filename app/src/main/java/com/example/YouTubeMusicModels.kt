package com.example

/** A single song found via [YouTubeMusicProvider.search]. */
data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val duration: String?
)
