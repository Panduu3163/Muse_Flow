package com.example

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Searches the device's own local audio files via [MediaStore] - no network involved. Backs
 * Search's "On device" mode, as a local counterpart to [JioSaavnProvider]/[YouTubeMusicProvider].
 *
 * Each result plays directly from its MediaStore `content://` URI - Media3/ExoPlayer handles
 * `content://` out of the box (same as it already does `file://` for downloaded tracks) - so
 * [TrackResult.directStreamUrl] is set immediately, same as a JioSaavn result, and no separate
 * resolve step or download button is ever needed for these (see [MusicSource.LOCAL_DEVICE]'s
 * handling in [SearchScreen]).
 */
class LocalAudioProvider(private val context: Context) {

    /** Blank [query] returns every local audio track (used when the user opens "On device" mode
     * before typing anything). Matches on title or artist, case-insensitively (MediaStore's LIKE
     * is case-insensitive by default for ASCII). */
    suspend fun search(query: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val trimmed = query.trim()
        val selection: String
        val selectionArgs: Array<String>?
        if (trimmed.isEmpty()) {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            selectionArgs = null
        } else {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (" +
                "${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
            val likeArg = "%$trimmed%"
            selectionArgs = arrayOf(likeArg, likeArg)
        }

        val results = mutableListOf<TrackResult>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: continue
                // MediaStore reports "<unknown>" (literally) for files with no artist tag set.
                val artist = cursor.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: "Unknown artist"
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                results.add(
                    TrackResult(
                        id = uri.toString(),
                        title = title,
                        artist = artist,
                        duration = formatDurationMs(cursor.getLong(durationCol)),
                        source = "This device",
                        sourceType = MusicSource.LOCAL_DEVICE,
                        directStreamUrl = uri.toString()
                    )
                )
            }
        }
        results
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
