package com.example

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

private const val SHARE_CODE_PREFIX = "museflow-playlist:v1:"
private const val SHARE_SCHEMA_VERSION = 1

/** Same "absent or explicit null" handling [BackupRepository] uses for its own JSON round-trip
 * (see its own doc on why plain `optString().takeIf { isNotBlank() }` is wrong here) - kept as its
 * own file-private copy rather than a shared import, matching how this exact small helper is
 * already duplicated per-file elsewhere in this codebase (`ArtistAlbumPageCache.kt`,
 * `HomeShelfCache.kt`) rather than exported, since a top-level Kotlin function's visibility can't
 * be widened without colliding with those other files' own same-named private declarations. */
private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

/** A playlist decoded from a share code - [ImportSharedPlaylistScreen] shows this as a preview
 * before the user commits to adding it to their own library. */
data class SharedPlaylist(val name: String, val tracks: List<Track>)

/**
 * Encodes/decodes a single playlist as a compact text code, so one MuseFlow user can hand a
 * playlist to another without either app needing a server of its own: the whole playlist travels
 * inside the code itself (gzipped, then base64url'd), shared through whatever channel the user
 * already has (chat, email, ...) via the normal Android share sheet, and pasted into
 * [ImportSharedPlaylistScreen] on the receiving end.
 *
 * Reuses the exact portable, no-Room-id JSON field shape [BackupRepository] already established
 * for its own export/import (title/artist/album/duration/imageUrl/streamUrl/sourceId/sourceType/
 * albumId/artistId) rather than inventing a second one - both features solve the same "describe a
 * track well enough to recreate it on another device" problem.
 *
 * On-device (local file) tracks are dropped before encoding, never round-tripped: their only
 * identifier is a `content://` URI meaningless on any other phone, so including them would just
 * produce dead rows the receiver could never play. The caller ([PlaylistDetailScreen]'s share
 * action) is responsible for telling the sharer how many were left out.
 */
object PlaylistShareCodec {

    fun encode(name: String, tracks: List<Track>): String {
        val shareable = tracks.filter { it.sourceType != MusicSource.LOCAL_DEVICE }
        val root = JSONObject().apply {
            put("schemaVersion", SHARE_SCHEMA_VERSION)
            put("name", name)
            put(
                "tracks",
                JSONArray().apply {
                    shareable.forEach { track ->
                        put(
                            JSONObject().apply {
                                put("title", track.title)
                                put("artist", track.artist)
                                put("album", track.album)
                                put("duration", track.duration)
                                put("imageUrl", track.imageUrl ?: JSONObject.NULL)
                                put("streamUrl", track.streamUrl ?: JSONObject.NULL)
                                put("sourceId", track.sourceId ?: JSONObject.NULL)
                                put("sourceType", track.sourceType?.name ?: JSONObject.NULL)
                                put("albumId", track.albumId ?: JSONObject.NULL)
                                put("artistId", track.artistId ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
        }
        val gzipped = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return SHARE_CODE_PREFIX +
            Base64.encodeToString(gzipped, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Returns null for anything that isn't a code this function produced - malformed input, a
     * schema version this build doesn't understand, or just plain unrelated pasted text - never
     * throws. The prefix only needs to appear *somewhere* in [code] (rather than exactly at its
     * start) so pasting a whole shared message, not just the bare code, still works. */
    fun decode(code: String): SharedPlaylist? {
        val trimmed = code.trim()
        if (!trimmed.contains(SHARE_CODE_PREFIX)) return null
        val payload = trimmed.substringAfter(SHARE_CODE_PREFIX).trim()
        return runCatching {
            val gzipped = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val root = JSONObject(json)
            if (root.optInt("schemaVersion", -1) != SHARE_SCHEMA_VERSION) return null
            val name = root.optString("name").takeIf { it.isNotBlank() } ?: return null
            val tracksArray = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = (0 until tracksArray.length()).mapNotNull { i ->
                val obj = tracksArray.optJSONObject(i) ?: return@mapNotNull null
                val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Track(
                    title = title,
                    artist = obj.optString("artist"),
                    album = obj.optString("album"),
                    duration = obj.optString("duration"),
                    plays = "",
                    gradientIndex = 0,
                    imageUrl = obj.optNullableString("imageUrl"),
                    streamUrl = obj.optNullableString("streamUrl"),
                    sourceId = obj.optNullableString("sourceId"),
                    sourceType = obj.optNullableString("sourceType")
                        ?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
                    albumId = obj.optNullableString("albumId"),
                    artistId = obj.optNullableString("artistId"),
                )
            }
            SharedPlaylist(name, tracks)
        }.getOrNull()
    }
}

/**
 * Builds and fires the share sheet for a playlist [name]/[tracks] - the write side of
 * [PlaylistShareCodec], reused by every "Share" action on a *local* playlist (Library's playlist
 * grid, [com.example.ui.screens.PlaylistDetailScreen]'s own menu) so the intent/toast logic lives
 * in exactly one place.
 *
 * [remoteId] is the playlist's YouTube id when it was originally saved from an online playlist
 * (`PlaylistEntity.remoteId`) - when set, this shares the plain YouTube link instead of a MuseFlow
 * code, via [shareYouTubePlaylistLink]: that playlist's real content already lives on YouTube, so
 * there's no reason to re-encode it into a MuseFlow-specific code a non-MuseFlow recipient (or a
 * future YouTube-side edit to the playlist) couldn't use anyway - see the bug this fixed, where a
 * saved-from-YouTube playlist shared from Library was wrongly going out as a MuseFlow code.
 *
 * For a genuinely local playlist ([remoteId] null), refuses (with an explanatory toast, not a
 * broken empty share) to share one that's entirely on-device songs, since none of them can travel
 * with it; otherwise toasts how many were left out, if any, since that's easy to miss otherwise.
 */
fun Context.sharePlaylist(name: String, tracks: List<Track>, remoteId: String? = null) {
    if (remoteId != null) {
        shareYouTubePlaylistLink(name, remoteId)
        return
    }
    val shareableCount = tracks.count { it.sourceType != MusicSource.LOCAL_DEVICE }
    val excludedCount = tracks.size - shareableCount
    if (shareableCount == 0) {
        Toast.makeText(this, "This playlist only has on-device songs, which can't be shared.", Toast.LENGTH_LONG).show()
        return
    }
    val code = PlaylistShareCodec.encode(name, tracks)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "\"$name\" - a MuseFlow playlist ($shareableCount song${if (shareableCount == 1) "" else "s"})\n\n" +
                "Open MuseFlow > Library > Import shared playlist and paste this code:\n$code",
        )
    }
    startActivity(Intent.createChooser(intent, "Share playlist"))
    if (excludedCount > 0) {
        Toast.makeText(
            this,
            "Shared without $excludedCount on-device song${if (excludedCount == 1) "" else "s"} - those can't travel with it.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** A YouTube-sourced playlist needs no encoding of its own to share - the id alone is already
 * exactly what any MuseFlow install (or a browser) needs to reopen the same playlist. Shared by
 * [RemotePlaylistScreen]'s own Share action and [sharePlaylist]'s [remoteId] branch, so the two
 * "share a YouTube playlist" entry points (a not-yet-saved one, and one already saved to Library)
 * produce the exact same link. */
fun Context.shareYouTubePlaylistLink(title: String, playlistId: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\nhttps://music.youtube.com/playlist?list=$playlistId")
    }
    startActivity(Intent.createChooser(intent, "Share playlist"))
}

/** A YouTube playlist id from a pasted `music.youtube.com`/`youtube.com` link's `list=` query
 * param, or a bare playlist id pasted on its own (YouTube Music's own IDs commonly start `PL`,
 * `VL`, `OLAK5uy_`, or `RD`) - lets [ImportSharedPlaylistScreen] accept either a MuseFlow share
 * code or a plain YouTube playlist link/id through the same paste field. Null if [text] contains
 * neither shape. */
fun extractYouTubePlaylistId(text: String): String? {
    val trimmed = text.trim()
    Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(trimmed)?.let { return it.groupValues[1] }
    Regex("""\b(PL|VL|RD|OLAK5uy_)[a-zA-Z0-9_-]{8,}\b""").find(trimmed)?.let { return it.value }
    return null
}
