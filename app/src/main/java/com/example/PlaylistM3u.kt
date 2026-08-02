package com.example

/** Why an M3U file couldn't be read, in words that tell the user what to do about it. */
class M3uFormatException(message: String) : Exception(message)

/**
 * Reads an M3U/M3U8 playlist into tracks, reusing [CsvTrack] so the result feeds the same
 * search-and-match pipeline the CSV importer already uses.
 *
 * Unlike a CSV export, an M3U file carries no dedicated title/artist columns - just an optional
 * `#EXTINF:<duration>,<artist> - <title>` line before each entry, and a URI or file path for the
 * entry itself. When `#EXTINF` is present it's the source of truth. When it's missing (a bare
 * list of paths/URLs, which plenty of M3U files are), the file name is the only signal available,
 * so a rough title is derived from it instead - the artist is left blank rather than guessed,
 * since a wrong guess would poison the match rather than just widen the search.
 */
fun parsePlaylistM3u(text: String): List<CsvTrack> {
    val lines = text.lines()
    if (lines.all { it.isBlank() }) throw M3uFormatException("That file is empty.")

    val tracks = mutableListOf<CsvTrack>()
    var pendingTitle: String? = null
    var pendingArtist: String? = null

    for (rawLine in lines) {
        val line = rawLine.trim().removePrefix("﻿")
        if (line.isEmpty()) continue

        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            // Format is "#EXTINF:<duration>,<artist> - <title>" - split on the first " - " once
            // past the duration.
            val info = line.substringAfter(',', missingDelimiterValue = "").trim()
            val separatorIndex = info.indexOf(" - ")
            when {
                separatorIndex >= 0 -> {
                    pendingArtist = info.substring(0, separatorIndex).trim()
                    pendingTitle = info.substring(separatorIndex + " - ".length).trim()
                }
                info.isNotEmpty() -> {
                    // No "Artist - Title" split available; still better than the bare file name.
                    pendingTitle = info
                    pendingArtist = null
                }
            }
            continue
        }

        // Any other directive (#EXTM3U, #EXTGRP, #PLAYLIST, ...) carries no track info.
        if (line.startsWith("#")) continue

        // A URI or file path entry - either the payload of the #EXTINF above it, or a bare entry
        // with no metadata at all.
        val title = pendingTitle
        val artist = pendingArtist
        pendingTitle = null
        pendingArtist = null

        when {
            !title.isNullOrBlank() -> tracks += CsvTrack(title = title, artist = artist.orEmpty())
            else -> titleFromPath(line)?.let { tracks += CsvTrack(title = it, artist = "") }
        }
    }

    return tracks
}

/** A rough, human-readable title guessed from a file path or URL: last path segment, extension
 * stripped, underscores/dashes turned into spaces. The only signal an M3U entry with no
 * `#EXTINF` line offers. */
private fun titleFromPath(path: String): String? {
    val withoutQueryOrFragment = path.substringBefore('?').substringBefore('#')
    val fileName = withoutQueryOrFragment.substringAfterLast('/').substringAfterLast('\\')
    if (fileName.isBlank()) return null
    val withoutExtension = fileName.substringBeforeLast('.', fileName)
    val spaced = withoutExtension
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    return spaced.ifBlank { null }
}

/** Whether a file name looks like an M3U/M3U8 playlist rather than a CSV one. */
fun isM3uFileName(name: String): Boolean {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension == "m3u" || extension == "m3u8"
}
