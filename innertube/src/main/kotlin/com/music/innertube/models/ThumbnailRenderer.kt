package com.music.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThumbnailRenderer(
    @JsonNames("croppedSquareThumbnailRenderer")
    val musicThumbnailRenderer: MusicThumbnailRenderer?,
    val musicAnimatedThumbnailRenderer: MusicAnimatedThumbnailRenderer?,
    val croppedSquareThumbnailRenderer: MusicThumbnailRenderer?,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
        val thumbnailCrop: String?,
        val thumbnailScale: String?,
    ) {
        /** The largest thumbnail YouTube offered, size-upgraded further still - every caller in
         * this app (search rows, album/artist/playlist headers, Now Playing, the media
         * notification) wants sharp art, and there's no lower-res use case worth preserving the
         * original size for. */
        fun getThumbnailUrl() = thumbnail.thumbnails.lastOrNull()?.url?.let(::upgradeThumbnailSize)
    }

    @Serializable
    data class MusicAnimatedThumbnailRenderer(
        val animatedThumbnail: Thumbnails,
        val backupRenderer: MusicThumbnailRenderer,
    )
}

/** YouTube's thumbnail URLs (googleusercontent.com) encode the requested size in the URL itself
 * (e.g. "=w120-h120-l90-rj") - most responses default to a size meant for a small list row.
 * Bumped to 544px (enough for a full-bleed Now Playing/Artist/Album header) for any URL that has
 * that size-param pattern; a plain `i.ytimg.com` path or one without the pattern passes through
 * unchanged rather than being mangled. */
fun upgradeThumbnailSize(rawUrl: String): String =
    rawUrl.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
