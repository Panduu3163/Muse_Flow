package com.example

/** Playback identity is the provider recording ID, not the legacy title/artist storage key. */
fun TrackResult.playbackId(): String = id

/** Matches the ID used when a persisted Track is converted into a playback item. */
fun Track.playbackId(): String = sourceId ?: "$title|$artist"

/** A same-named download is not proof that it contains the requested recording. Older
 * downloads without IDs can still play when their own file was explicitly selected. */
internal fun canReuseDownloadForPlayback(
    requestedId: String,
    requestedUri: String?,
    storedId: String?,
    storedPath: String,
): Boolean {
    if (!storedId.isNullOrBlank()) return requestedId == storedId
    return requestedUri != null && (requestedUri == storedPath || requestedUri == "file://$storedPath")
}
