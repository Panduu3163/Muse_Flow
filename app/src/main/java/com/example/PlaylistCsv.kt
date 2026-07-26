package com.example

/** One row of an imported playlist file, before it has been matched to anything playable. */
data class CsvTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
)

/** Why a CSV couldn't be read, in words that tell the user what to do about it. */
class CsvFormatException(message: String) : Exception(message)

/**
 * Splits CSV text into rows of fields, following RFC 4180.
 *
 * Written out rather than split on commas because the fields this has to survive routinely
 * contain them - "Artist Name(s)" for a collaboration is `"Shakira, Burna Boy"`, and a naive
 * split turns one track into two broken ones. Quoted fields may also contain newlines, so rows
 * are terminated only outside quotes, and `""` inside a quoted field is a literal quote.
 */
internal fun parseCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0

    fun endField() {
        row.add(field.toString())
        field.setLength(0)
    }

    fun endRow() {
        endField()
        // A trailing newline shouldn't manufacture a blank row.
        if (row.size > 1 || row.first().isNotBlank()) rows.add(row)
        row = mutableListOf()
    }

    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                field.append('"')
                i++
            }
            c == '"' -> inQuotes = !inQuotes
            !inQuotes && c == ',' -> endField()
            !inQuotes && (c == '\n' || c == '\r') -> {
                // Consume CRLF as one terminator.
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                endRow()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()

    return rows
}

/** Header names different exporters use for the same column, lowercase. Exportify - the tool most
 * people reach for - writes "Track Name"/"Artist Name(s)"; others differ, so each column accepts
 * several spellings rather than demanding one exact export format. */
private val TITLE_HEADERS = listOf("track name", "title", "song", "song name", "name", "track")
private val ARTIST_HEADERS = listOf("artist name(s)", "artist name", "artist(s)", "artists", "artist", "album artist")
private val ALBUM_HEADERS = listOf("album name", "album")

private fun List<String>.indexOfHeader(candidates: List<String>): Int {
    val normalized = map { it.trim().lowercase().removePrefix("﻿") }
    // Exact match first: "album" would otherwise match "album artist" by prefix and steal the
    // artist column on exports that carry both.
    candidates.forEach { candidate ->
        val exact = normalized.indexOf(candidate)
        if (exact >= 0) return exact
    }
    candidates.forEach { candidate ->
        val partial = normalized.indexOfFirst { it.contains(candidate) }
        if (partial >= 0) return partial
    }
    return -1
}

/**
 * Reads a playlist export into tracks.
 *
 * Columns are located by header name rather than position, since exporters order them
 * differently. Rows missing a title or artist are dropped rather than imported as blanks - a row
 * with no artist can't be matched to anything, so keeping it would only produce a failure later.
 */
fun parsePlaylistCsv(text: String): List<CsvTrack> {
    val rows = parseCsvRows(text)
    if (rows.isEmpty()) throw CsvFormatException("That file is empty.")

    val header = rows.first()
    val titleIndex = header.indexOfHeader(TITLE_HEADERS)
    val artistIndex = header.indexOfHeader(ARTIST_HEADERS)
    if (titleIndex < 0 || artistIndex < 0) {
        throw CsvFormatException(
            "Couldn't find track and artist columns in that file. It needs a header row with " +
                "columns like \"Track Name\" and \"Artist Name(s)\"."
        )
    }
    val albumIndex = header.indexOfHeader(ALBUM_HEADERS)

    return rows.drop(1).mapNotNull { row ->
        val title = row.getOrNull(titleIndex)?.trim().orEmpty()
        val artist = row.getOrNull(artistIndex)?.trim().orEmpty()
        if (title.isEmpty() || artist.isEmpty()) return@mapNotNull null
        CsvTrack(
            title = title,
            artist = artist,
            album = albumIndex.takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() },
        )
    }
}
