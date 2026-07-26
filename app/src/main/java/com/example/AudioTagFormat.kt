package com.example

/**
 * Pure mapping from a download's HTTP `Content-Type` to the file extension [AudioTagger] needs to
 * pick the right tag writer - jaudiotagger identifies a format by file extension, not by sniffing
 * content, so [DownloadRepository] must know this before it can even attempt to tag anything.
 * Extracted so this mapping is directly unit-testable. Returns null for anything not worth
 * attempting - notably `audio/webm`/`audio/opus` (YouTube's actual common format, per
 * [YouTubeStreamResolver.pickBestAudioFormat]'s bitrate-picking - very often Opus-in-WebM, not
 * M4A/AAC) has no supported writer here, so those downloads are simply left untagged rather than
 * risking a bad write against a format this library doesn't understand.
 */
fun audioTagFileExtensionFor(contentType: String?): String? {
    val normalized = contentType?.substringBefore(';')?.trim()?.lowercase()
    return when (normalized) {
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> ".m4a"
        "audio/mpeg", "audio/mp3" -> ".mp3"
        else -> null
    }
}
