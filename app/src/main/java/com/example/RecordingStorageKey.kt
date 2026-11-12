package com.example

/** A recording keeps its identity even when a title changes, and never shares status with a
 * different audio/video recording. Metadata fallback is only for genuinely unidentified legacy rows. */
internal fun recordingStorageKey(title: String, artist: String, sourceId: String?, sourceType: String?): String {
    val id = sourceId?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) && it != "$title|$artist" }
        ?: return "${title.trim().lowercase()}::${artist.trim().lowercase()}"
    val local = sourceType == MusicSource.LOCAL_DEVICE.name || id.startsWith("content://") || id.startsWith("file://")
    return (if (local) "local:" else "yt:") + id
}
