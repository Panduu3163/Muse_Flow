package com.example

import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches lyrics from SimpMusic's hosted lyrics API (https://api-lyrics.simpmusic.org, with a
 * fallback mirror), adapted from Echo-Music's `simpmusic/SimpMusicLyrics.kt`. Unlike MuseFlow's
 * other providers, this one is keyed by YouTube video id rather than title/artist search - the
 * API's data is crowdsourced against specific YouTube Music video ids - so it's only queried when
 * a confirmed real video id is available (see [LyricsViewModel]).
 *
 * Prioritizes `richSyncLyrics` (word-level, same enhanced-LRC tag convention PaxSenix uses - see
 * [PaxSenixProvider.parseEnhancedLrc]) over `syncedLyrics` (line-level LRC) over `plainLyrics`.
 *
 * Used as the "SimpMusic" entry in the configurable lyrics provider order (see
 * [LyricsViewModel]).
 */
class SimpMusicProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private object Api {
        const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"
        const val FALLBACK_URL = "https://vivi-yt-music-server.onrender.com/v1/"
    }

    private data class Track(
        val durationSeconds: Int?,
        val syncedLyrics: String?,
        val plainLyrics: String?,
        val richSyncLyrics: String?,
    )

    suspend fun fetchLyrics(videoId: String, durationSeconds: Int? = null): LyricsResult =
        withContext(Dispatchers.IO) {
            try {
                val tracks = fetchTracks(Api.BASE_URL + videoId) ?: fetchTracks(Api.FALLBACK_URL + videoId)
                if (tracks.isNullOrEmpty()) return@withContext LyricsResult.NotFound

                val candidates = if (durationSeconds != null && durationSeconds > 0) {
                    tracks.filter { it.durationSeconds == null || abs(it.durationSeconds - durationSeconds) <= 10 }
                        .ifEmpty { tracks }
                } else {
                    tracks
                }
                val best = if (durationSeconds != null && durationSeconds > 0) {
                    candidates.minByOrNull { abs((it.durationSeconds ?: durationSeconds) - durationSeconds) }
                } else {
                    candidates.firstOrNull()
                } ?: return@withContext LyricsResult.NotFound

                best.richSyncLyrics?.takeIf { it.isNotBlank() }?.let {
                    val lines = PaxSenixProvider.parseEnhancedLrc(it)
                    if (lines.isNotEmpty()) return@withContext LyricsResult.Synced(lines)
                }
                best.syncedLyrics?.takeIf { it.isNotBlank() }?.let {
                    val lines = LrcLibProvider.parseLrc(it)
                    if (lines.isNotEmpty()) return@withContext LyricsResult.Synced(lines)
                }
                best.plainLyrics?.takeIf { it.isNotBlank() }?.let {
                    return@withContext LyricsResult.PlainOnly(it)
                }
                LyricsResult.NotFound
            } catch (e: Exception) {
                LyricsResult.Error(e.message ?: "Unknown error")
            }
        }

    /** Null return means "couldn't reach this mirror, try the next one"; an empty list means the
     * mirror responded but genuinely has nothing for this video id. */
    private fun fetchTracks(url: String): List<Track>? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "MuseFlow/1.0")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("type") != "success") return emptyList()
                val data = json.optJSONArray("data") ?: return emptyList()
                parseTracks(data)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTracks(data: JSONArray): List<Track> =
        (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            Track(
                durationSeconds = obj.optInt("durationSeconds", -1).takeIf { it >= 0 },
                syncedLyrics = obj.optString("syncedLyrics").takeIf { it.isNotBlank() },
                plainLyrics = obj.optString("plainLyric").takeIf { it.isNotBlank() },
                richSyncLyrics = obj.optString("richSyncLyrics").takeIf { it.isNotBlank() },
            )
        }
}
