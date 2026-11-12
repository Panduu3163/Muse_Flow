package com.example

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.queueDataStore by preferencesDataStore(name = "playback_queue")

private object QueueKeys {
    val ITEMS = stringPreferencesKey("items")
    val INDEX = intPreferencesKey("index")
    val POSITION = longPreferencesKey("position_ms")
    val SHUFFLE = booleanPreferencesKey("shuffle")
    val REPEAT = intPreferencesKey("repeat")
    val ORDER = stringPreferencesKey("shuffle_order")
}

/** One saved queue entry - the minimum needed to rebuild a playable MediaItem. */
data class SavedQueueItem(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    /** Only set for tracks that play from a local file; online tracks are re-resolved on demand. */
    val localFilePath: String?,
    /** The artist's browseId, when known - lets Now Playing's artist name keep working after a
     * queue restore, not just on a freshly-started queue. */
    val artistId: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val queueOrigin: String? = null,
)

/** Old queues did not store content URIs as localFilePath; their identity is still playable. */
internal fun SavedQueueItem.restoredPlaybackUri(): Uri {
    val local = localFilePath?.takeIf { it.isNotBlank() }?.let { path ->
        val uri = path.toUri()
        when {
            // A colon in an artist/file name must not be parsed as a URI scheme.
            path.startsWith("/") -> Uri.Builder().scheme("file").authority("").path(path).build()
            uri.scheme == null -> Uri.fromFile(File(path))
            uri.scheme == "file" || uri.scheme == "content" -> uri
            else -> null // Never revive an expired remote URL from old persisted data.
        }
    }
    if (local != null) return local
    val identity = mediaId.toUri()
    return if (identity.scheme == "content" || identity.scheme == "file") identity
        else youTubeResolvePlaceholderUri(mediaId)
}

/** A restored queue: what was loaded, which item was active, and how far into it. */
data class SavedQueue(
    val items: List<SavedQueueItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val order: List<Int> = emptyList(),
)

/** Old/corrupt permutations get a deterministic complete order, never duplicate/invalid indices. */
internal fun restoredShuffleOrder(order: List<Int>, itemCount: Int, windowStart: Int = 0, windowSize: Int = itemCount): IntArray {
    val valid = order.size == itemCount && order.toSet().size == itemCount && order.all { it in 0 until itemCount }
    return (if (valid) order else (0 until itemCount).toList())
        .filter { it in windowStart until (windowStart + windowSize) }.map { it - windowStart }.toIntArray()
}

/**
 * Persists the playback queue so it survives the process being killed.
 *
 * Deliberately stores only *identity and metadata*, never resolved stream URLs: those expire within
 * minutes (see [ResolvedUrlCache]), so a restored queue re-resolves each track on demand exactly
 * like a fresh one. A downloaded track keeps its file path, which doesn't expire.
 */
class QueueRepository private constructor(private val appContext: Context) {

    suspend fun save(items: List<SavedQueueItem>, index: Int, positionMs: Long,
                     shuffle: Boolean = false, repeat: Int = 0, order: List<Int> = emptyList()) {
        val json = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("mediaId", item.mediaId)
                        put("title", item.title)
                        put("artist", item.artist)
                        put("album", item.album)
                        item.artworkUrl?.let { put("artworkUrl", it) }
                        item.localFilePath?.let { put("localFilePath", it) }
                        item.artistId?.let { put("artistId", it) }
                        item.albumId?.let { put("albumId", it) }
                        if (item.durationMs > 0) put("durationMs", item.durationMs)
                        item.queueOrigin?.let { put("queueOrigin", it) }
                    }
                )
            }
        }.toString()

        appContext.queueDataStore.edit { prefs ->
            prefs[QueueKeys.ITEMS] = json
            prefs[QueueKeys.INDEX] = index.coerceAtLeast(0)
            prefs[QueueKeys.POSITION] = positionMs.coerceAtLeast(0L)
            prefs[QueueKeys.SHUFFLE] = shuffle
            prefs[QueueKeys.REPEAT] = repeat.takeIf { it in 0..2 } ?: 0
            prefs[QueueKeys.ORDER] = JSONArray(order).toString()
        }
    }

    suspend fun load(): SavedQueue? {
        val prefs = appContext.queueDataStore.data.first()
        val json = prefs[QueueKeys.ITEMS]?.takeIf { it.isNotBlank() } ?: return null

        val items = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val mediaId = obj.optString("mediaId").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                SavedQueueItem(
                    mediaId = mediaId,
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    album = obj.optString("album"),
                    // isNull guards against JSONObject.NULL stringifying to the literal "null" -
                    // the same trap that produced a real bug in BackupRepository once already.
                    artworkUrl = obj.takeIf { !it.isNull("artworkUrl") }?.optString("artworkUrl"),
                    localFilePath = obj.takeIf { !it.isNull("localFilePath") }
                        ?.optString("localFilePath"),
                    artistId = obj.takeIf { !it.isNull("artistId") }?.optString("artistId")
                        ?.takeIf { it.isNotBlank() },
                    albumId = obj.takeIf { !it.isNull("albumId") }?.optString("albumId")
                        ?.takeIf { it.isNotBlank() },
                    durationMs = obj.optLong("durationMs").coerceAtLeast(0L),
                    queueOrigin = obj.optString("queueOrigin").takeIf { it.isNotBlank() },
                )
            }
        }.getOrNull().orEmpty()

        if (items.isEmpty()) return null

        return SavedQueue(
            items = items,
            index = (prefs[QueueKeys.INDEX] ?: 0).coerceIn(0, items.lastIndex),
            positionMs = (prefs[QueueKeys.POSITION] ?: 0L).coerceAtLeast(0L),
            shuffle = prefs[QueueKeys.SHUFFLE] ?: false,
            repeat = prefs[QueueKeys.REPEAT]?.takeIf { it in 0..2 } ?: 0,
            order = runCatching {
                val array = JSONArray(prefs[QueueKeys.ORDER] ?: "[]")
                (0 until array.length()).map { array.getInt(it) }
            }.getOrDefault(emptyList()),
        )
    }

    suspend fun clear() {
        appContext.queueDataStore.edit { it.clear() }
    }

    companion object {
        @Volatile private var instance: QueueRepository? = null

        fun getInstance(context: Context): QueueRepository =
            instance ?: synchronized(this) {
                instance ?: QueueRepository(context.applicationContext).also { instance = it }
            }
    }
}
