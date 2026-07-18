package com.example

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import org.json.JSONArray
import org.json.JSONObject

/**
 * A snapshot of one Home mood/genre shelf's last successfully-fetched tracks (serialized as JSON,
 * matching how the rest of this codebase already parses provider responses with org.json), so
 * Home can still show real content while offline instead of the "couldn't load" state it'd
 * otherwise show for a network fetch that can't possibly succeed. Cover art keeps working too,
 * for free - it's the same image URLs Coil already has in its own disk cache from the original
 * online load. Shown tracks are visible but not playable unless separately downloaded; nothing
 * here changes that.
 */
@Entity(tableName = "home_shelf_cache")
data class HomeShelfCacheEntity(
    @PrimaryKey val shelfTitle: String,
    val tracksJson: String,
    val cachedAt: Long
)

@Dao
interface HomeShelfCacheDao {
    @Query("SELECT * FROM home_shelf_cache WHERE shelfTitle = :shelfTitle LIMIT 1")
    suspend fun get(shelfTitle: String): HomeShelfCacheEntity?

    /** Every cached shelf, for Library's "Cached" tile - which shows whatever Home has actually
     * cached for offline browsing, not a separate cache of its own. */
    @Query("SELECT * FROM home_shelf_cache")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<HomeShelfCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeShelfCacheEntity)
}

/** Serializes just the fields needed to fully reconstruct a playable/downloadable [Track] later -
 * see [parseTracksJson]. */
fun List<Track>.toJson(): String {
    val array = JSONArray()
    forEach { track ->
        array.put(
            JSONObject().apply {
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("duration", track.duration)
                put("plays", track.plays)
                put("gradientIndex", track.gradientIndex)
                put("imageUrl", track.imageUrl ?: JSONObject.NULL)
                put("streamUrl", track.streamUrl ?: JSONObject.NULL)
                put("sourceType", track.sourceType?.name ?: JSONObject.NULL)
                put("sourceId", track.sourceId ?: JSONObject.NULL)
            }
        )
    }
    return array.toString()
}

// org.json's optString() stringifies a stored JSONObject.NULL to the literal text "null" rather
// than returning null/blank - a plain isNotBlank() check would misread that as a real value, so
// nullable fields are read via isNull() first instead.
private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** Inverse of [List.toJson]. Never throws - a corrupt/outdated cache entry just yields an empty
 * list, same as a shelf that hasn't been cached at all. */
fun parseTracksJson(json: String): List<Track> = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        Track(
            title = obj.getString("title"),
            artist = obj.getString("artist"),
            album = obj.getString("album"),
            duration = obj.getString("duration"),
            plays = obj.optString("plays"),
            gradientIndex = obj.getInt("gradientIndex"),
            imageUrl = obj.optNullableString("imageUrl"),
            streamUrl = obj.optNullableString("streamUrl"),
            sourceType = obj.optNullableString("sourceType")
                ?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
            sourceId = obj.optNullableString("sourceId")
        )
    }
}.getOrDefault(emptyList())
