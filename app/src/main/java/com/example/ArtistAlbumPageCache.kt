package com.example

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.music.innertube.models.upgradeThumbnailSize
import org.json.JSONArray
import org.json.JSONObject

/**
 * A snapshot of one artist page's last successfully-fetched [ArtistTracklist] (serialized as JSON,
 * same approach as [HomeShelfCacheEntity]) - so revisiting an artist already opened this session (or
 * a prior one) shows real content immediately instead of a blank loading spinner while the network
 * call is in flight. Always followed by a live refetch (see [ArtistScreen]) - this is a
 * stale-while-revalidate cache, not a replacement for the network call.
 */
@Entity(tableName = "artist_page_cache")
data class ArtistPageCacheEntity(
    @PrimaryKey val artistId: String,
    val tracklistJson: String,
    val cachedAt: Long,
)

@Dao
interface ArtistPageCacheDao {
    @Query("SELECT * FROM artist_page_cache WHERE artistId = :artistId LIMIT 1")
    suspend fun get(artistId: String): ArtistPageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArtistPageCacheEntity)
}

/** Same idea as [ArtistPageCacheEntity], for [AlbumDetails]. */
@Entity(tableName = "album_page_cache")
data class AlbumPageCacheEntity(
    @PrimaryKey val albumId: String,
    val detailsJson: String,
    val cachedAt: Long,
)

@Dao
interface AlbumPageCacheDao {
    @Query("SELECT * FROM album_page_cache WHERE albumId = :albumId LIMIT 1")
    suspend fun get(albumId: String): AlbumPageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlbumPageCacheEntity)
}

// org.json's optString() stringifies a stored JSONObject.NULL to the literal text "null" rather
// than returning null/blank - same guard as HomeShelfCache.kt's optNullableString.
private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.putTrackResult(track: TrackResult) {
    put("id", track.id)
    put("title", track.title)
    put("artist", track.artist)
    put("duration", track.duration ?: JSONObject.NULL)
    put("source", track.source)
    put("sourceType", track.sourceType.name)
    put("directStreamUrl", track.directStreamUrl ?: JSONObject.NULL)
    put("imageUrl", track.imageUrl ?: JSONObject.NULL)
    put("albumId", track.albumId ?: JSONObject.NULL)
    put("artistId", track.artistId ?: JSONObject.NULL)
}

private fun JSONObject.toTrackResult(): TrackResult = TrackResult(
    id = getString("id"),
    title = getString("title"),
    artist = getString("artist"),
    duration = optNullableString("duration"),
    source = optString("source"),
    sourceType = optNullableString("sourceType")
        ?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() } ?: MusicSource.YOUTUBE_MUSIC,
    directStreamUrl = optNullableString("directStreamUrl"),
    imageUrl = optNullableString("imageUrl")?.let(::upgradeThumbnailSize),
    albumId = optNullableString("albumId"),
    artistId = optNullableString("artistId"),
)

private fun JSONObject.putAlbumResult(album: AlbumResult) {
    put("id", album.id)
    put("title", album.title)
    put("artist", album.artist)
    put("imageUrl", album.imageUrl ?: JSONObject.NULL)
    put("songCount", album.songCount ?: JSONObject.NULL)
    put("sourceType", album.sourceType.name)
}

private fun JSONObject.toAlbumResult(): AlbumResult = AlbumResult(
    id = getString("id"),
    title = getString("title"),
    artist = optString("artist"),
    imageUrl = optNullableString("imageUrl")?.let(::upgradeThumbnailSize),
    songCount = if (isNull("songCount")) null else optInt("songCount"),
    sourceType = optNullableString("sourceType")
        ?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() } ?: MusicSource.YOUTUBE_MUSIC,
)

private fun JSONObject.putArtistResult(artist: ArtistResult) {
    put("id", artist.id)
    put("name", artist.name)
    put("imageUrl", artist.imageUrl ?: JSONObject.NULL)
    put("sourceType", artist.sourceType.name)
    put("listenerCount", artist.listenerCount ?: JSONObject.NULL)
}

private fun JSONObject.toArtistResult(): ArtistResult = ArtistResult(
    id = getString("id"),
    name = getString("name"),
    imageUrl = optNullableString("imageUrl")?.let(::upgradeThumbnailSize),
    sourceType = optNullableString("sourceType")
        ?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() } ?: MusicSource.YOUTUBE_MUSIC,
    listenerCount = optNullableString("listenerCount"),
)

fun ArtistTracklist.toJson(): String = JSONObject().apply {
    put("tracks", JSONArray().apply { tracks.forEach { put(JSONObject().apply { putTrackResult(it) }) } })
    put("subscriberCountText", subscriberCountText ?: JSONObject.NULL)
    put("monthlyListenerCountText", monthlyListenerCountText ?: JSONObject.NULL)
    put("name", name ?: JSONObject.NULL)
    put("imageUrl", imageUrl ?: JSONObject.NULL)
    put("description", description ?: JSONObject.NULL)
    put("albums", JSONArray().apply { albums.forEach { put(JSONObject().apply { putAlbumResult(it) }) } })
    put("relatedArtists", JSONArray().apply { relatedArtists.forEach { put(JSONObject().apply { putArtistResult(it) }) } })
}.toString()

/** Inverse of [ArtistTracklist.toJson]. Never throws - a corrupt/outdated cache entry is treated as
 * "no cache", same as an artist that was never visited before. */
fun parseArtistTracklistJson(json: String): ArtistTracklist? = runCatching {
    val obj = JSONObject(json)
    val tracks = obj.getJSONArray("tracks").let { array ->
        (0 until array.length()).map { array.getJSONObject(it).toTrackResult() }
    }
    val albums = obj.getJSONArray("albums").let { array ->
        (0 until array.length()).map { array.getJSONObject(it).toAlbumResult() }
    }
    val relatedArtists = obj.getJSONArray("relatedArtists").let { array ->
        (0 until array.length()).map { array.getJSONObject(it).toArtistResult() }
    }
    ArtistTracklist(
        tracks = tracks,
        subscriberCountText = obj.optNullableString("subscriberCountText"),
        monthlyListenerCountText = obj.optNullableString("monthlyListenerCountText"),
        name = obj.optNullableString("name"),
        imageUrl = obj.optNullableString("imageUrl")?.let(::upgradeThumbnailSize),
        description = obj.optNullableString("description"),
        albums = albums,
        relatedArtists = relatedArtists,
    )
}.getOrNull()

fun AlbumDetails.toJson(): String = JSONObject().apply {
    put("title", title ?: JSONObject.NULL)
    put("artist", artist ?: JSONObject.NULL)
    put("imageUrl", imageUrl ?: JSONObject.NULL)
    put("tracks", JSONArray().apply { tracks.forEach { put(JSONObject().apply { putTrackResult(it) }) } })
}.toString()

/** Inverse of [AlbumDetails.toJson]. Never throws, same reasoning as [parseArtistTracklistJson]. */
fun parseAlbumDetailsJson(json: String): AlbumDetails? = runCatching {
    val obj = JSONObject(json)
    val tracks = obj.getJSONArray("tracks").let { array ->
        (0 until array.length()).map { array.getJSONObject(it).toTrackResult() }
    }
    AlbumDetails(
        title = obj.optNullableString("title"),
        artist = obj.optNullableString("artist"),
        imageUrl = obj.optNullableString("imageUrl")?.let(::upgradeThumbnailSize),
        tracks = tracks,
    )
}.getOrNull()
