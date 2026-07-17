package com.example

import android.net.Uri

/**
 * A syntactically-valid-but-fake HTTPS URL standing in for a YouTube track's real stream URL in
 * the playback queue. Using the IANA-reserved `.invalid` TLD (RFC 2606, guaranteed to fail real
 * DNS resolution) means [PlaybackService]'s `DefaultDataSource` still routes it through the
 * normal HTTP data source path (unlike a genuinely custom URI scheme, which `DefaultDataSource`
 * wouldn't know how to dispatch at all) - [PlaybackService]'s resolving wrapper then intercepts
 * it before any real network call happens and swaps in a freshly-resolved real URL.
 *
 * Shared between [PlayerViewModel] (which creates these placeholders) and [PlaybackService]
 * (which recognizes and resolves them) so the scheme/prefix lives in exactly one place.
 */
private const val YOUTUBE_RESOLVE_HOST = "museflow.invalid"
private const val YOUTUBE_RESOLVE_PATH_PREFIX = "/yt-resolve/"

fun youTubeResolvePlaceholderUri(videoId: String): Uri =
    Uri.parse("https://$YOUTUBE_RESOLVE_HOST$YOUTUBE_RESOLVE_PATH_PREFIX$videoId")

/** Extracts the video id back out of a placeholder built by [youTubeResolvePlaceholderUri], or
 * null if [uri] isn't one. */
fun youTubeVideoIdFromResolvePlaceholder(uri: Uri): String? {
    if (uri.host != YOUTUBE_RESOLVE_HOST) return null
    val path = uri.path ?: return null
    if (!path.startsWith(YOUTUBE_RESOLVE_PATH_PREFIX)) return null
    return path.removePrefix(YOUTUBE_RESOLVE_PATH_PREFIX).takeIf { it.isNotBlank() }
}
