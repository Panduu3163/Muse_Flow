package com.example

import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

// A document limit, not a limit on the user's live library. Reject before allocating unbounded input.
internal const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
private const val MAX_BACKUP_RECORDS = 100_000
private const val MAX_BACKUP_STRING_LENGTH = 16_384

internal data class BackupDocument(
    val likes: List<LikedSongEntity>,
    val playlists: List<BackupPlaylist>,
    val artists: List<FollowedArtistEntity> = emptyList(),
    val includesArtists: Boolean = false,
    val history: BackupHistory? = null,
    val settings: PortableSettings? = null,
)

internal data class BackupPlaylist(val playlist: PlaylistEntity, val tracks: List<PlaylistTrackEntity>)

internal fun readBackupDocument(input: InputStream, maxBytes: Int = MAX_BACKUP_BYTES): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        require(read <= maxBytes - output.size()) { "Backup file is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
}

/** Validate every supported record before the first write. Unknown fields remain forward-compatible. */
internal fun parseBackupDocument(json: String): BackupDocument {
    require(json.length <= MAX_BACKUP_BYTES && json.toByteArray().size <= MAX_BACKUP_BYTES) {
        "Backup file is too large"
    }
    val reader = JSONTokener(json)
    val root = reader.nextValue() as? JSONObject
        ?: throw IllegalArgumentException("Not a backup document")
    require(reader.nextClean() == '\u0000') { "Unexpected data after backup document" }
    val version = root.opt("schemaVersion")
    require(version is Number && version.toDouble() in listOf(1.0, 2.0, 3.0, 4.0)) {
        "This backup was made by an incompatible app version"
    }
    val likes = root.opt("likedSongs") as? JSONArray
        ?: throw IllegalArgumentException("Backup is missing its liked songs list")
    val playlists = root.opt("playlists") as? JSONArray
        ?: throw IllegalArgumentException("Backup is missing its playlists list")
    val includesArtists = version.toInt() >= 2
    val artists = if (includesArtists) root.opt("followedArtists") as? JSONArray
        ?: throw IllegalArgumentException("Backup is missing its followed artists list") else JSONArray()
    val includesHistory = if (version.toInt() >= 3) {
        require(root.opt("historyIncluded") is Boolean) { "Missing history coverage declaration" }
        root.getBoolean("historyIncluded")
    } else false
    val historyRecords = if (includesHistory) listOf("playbackHistory", "playbackEvents", "listeningSessions").sumOf { field ->
        (root.opt(field) as? JSONArray ?: throw IllegalArgumentException("Missing history list: $field")).length().toLong()
    } else 0L
    require(likes.length().toLong() + playlists.length() + artists.length() + historyRecords <= MAX_BACKUP_RECORDS) {
        "Backup contains too many records"
    }
    val parsedLikes = (0 until likes.length()).map { index ->
        val song = likes.getJSONObject(index)
        song.validateTrack()
        song.validateTimestamp("likedAt")
        (song.toLikedSongEntity() ?: throw IllegalArgumentException("A liked song has no recording key"))
            .copy(likedAt = song.optLong("likedAt", 0L))
    }.distinctBy { it.key }
    var recordCount = likes.length().toLong() + playlists.length() + artists.length() + historyRecords
    require(recordCount <= MAX_BACKUP_RECORDS) { "Backup contains too many records" }
    val parsedPlaylists = (0 until playlists.length()).map { index ->
        val obj = playlists.getJSONObject(index)
        obj.validateStrings("name", "coverImageUrl", "remoteId")
        val name = obj.optString("name")
        require(name.isNotBlank()) { "A playlist has no name" }
        obj.validateTimestamp("createdAt")
        require(!obj.has("isPinned") || obj.opt("isPinned") is Boolean) { "Invalid playlist pin" }
        val tracks = obj.opt("tracks") as? JSONArray
            ?: throw IllegalArgumentException("A playlist is missing its tracks list")
        recordCount += tracks.length()
        require(recordCount <= MAX_BACKUP_RECORDS) { "Backup contains too many records" }
        var previousTime = -1L
        val parsedTracks = (0 until tracks.length()).map { trackIndex ->
            val track = tracks.getJSONObject(trackIndex)
            track.validateTrack()
            track.validateTimestamp("addedAt")
            val entity = track.toPlaylistTrackEntity(0L)
                ?: throw IllegalArgumentException("A playlist track has no recording key")
            // JSON array order is authoritative. Preserve valid times, repair ties/missing times
            // so the existing ORDER BY addedAt query can reproduce that order after restoration.
            val time = maxOf(track.optLong("addedAt", 0L), Math.addExact(previousTime, 1L))
            previousTime = time
            entity.copy(addedAt = time)
        }
        require(parsedTracks.map { it.key }.distinct().size == parsedTracks.size) {
            "A playlist contains a duplicate recording"
        }
        BackupPlaylist(
            PlaylistEntity(
                name = name,
                createdAt = obj.optLong("createdAt", 0L),
                coverImageUrl = obj.takeUnless { it.isNull("coverImageUrl") }?.optString("coverImageUrl")?.takeIf { it.isNotBlank() },
                remoteId = obj.takeUnless { it.isNull("remoteId") }?.optString("remoteId")?.takeIf { it.isNotBlank() },
                isPinned = obj.optBoolean("isPinned", false),
            ),
            parsedTracks,
        )
    }
    val parsedArtists = (0 until artists.length()).map { index ->
        val artist = artists.getJSONObject(index)
        artist.validateStrings("artistId", "name", "imageUrl", "sourceType")
        require(!artist.isNull("artistId") && artist.optString("artistId").isNotBlank()) { "An artist has no identity" }
        require(!artist.isNull("name") && artist.optString("name").isNotBlank()) { "An artist has no name" }
        require(MusicSource.entries.any { it.name == artist.optString("sourceType") }) { "Unsupported artist source" }
        artist.validateTimestamp("followedAt")
        FollowedArtistEntity(
            artistId = artist.getString("artistId"), name = artist.getString("name"),
            imageUrl = if (artist.isNull("imageUrl")) null else artist.getString("imageUrl"),
            sourceType = artist.getString("sourceType"), followedAt = artist.optLong("followedAt", 0L),
            // Release-check baselines belong to this installation, not a portable library backup.
            knownTrackIds = "",
        )
    }.distinctBy { it.artistId }
    val settings = if (version.toInt() >= 4) {
        require(root.opt("settingsIncluded") is Boolean) { "Missing settings coverage declaration" }
        if (root.getBoolean("settingsIncluded")) {
            val payload = root.getJSONObject("settings")
            require(payload.toString().length <= 131_072) { "Settings payload is too large" }
            PortableSettingsFormat.parse(payload)
        } else null
    } else null
    return BackupDocument(parsedLikes, parsedPlaylists, parsedArtists, includesArtists,
        if (includesHistory) parseBackupHistory(root) else null, settings)
}

internal fun JSONObject.validateTrack() {
    validateStrings("key", "title", "artist", "album", "duration", "imageUrl", "streamUrl", "sourceId", "sourceType", "albumId", "artistId")
    require(!isNull("key") && optString("key").isNotBlank()) { "A song has no recording key" }
    if (has("gradientIndex")) {
        val value = get("gradientIndex")
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toInt().toDouble()) {
            "Invalid song colour value"
        }
    }
}

internal fun JSONObject.validateStrings(vararg fields: String) {
    fields.forEach { field ->
        if (!isNull(field)) {
            val value = get(field)
            require(value is String && value.length <= MAX_BACKUP_STRING_LENGTH) { "Invalid backup text field: $field" }
        }
    }
}

internal fun JSONObject.validateTimestamp(field: String) {
    if (has(field)) {
        val value = get(field)
        require(value is Int || value is Long) { "Invalid backup timestamp: $field" }
        require((value as Number).toLong() >= 0) { "Invalid backup timestamp: $field" }
    }
}
