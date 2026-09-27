package com.example

import android.util.Log
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "InnerTubeResolver"

/**
 * Resolves a YouTube videoId to a playable audio URL using **only** the ported `:innertube`
 * module - no part of MuseFlow's own cipher/PoToken pipeline is involved.
 *
 * YouTube rejects most clients for most videos, and which client works varies per video, per
 * region, and over time - a single client is never enough. So this walks a fallback chain
 * (mirroring the order Echo-Music uses in its own `YTPlayerUtils`, which is a
 * tested-in-production ordering rather than a guess) and takes the first client whose
 * `playabilityStatus` comes back OK.
 *
 * If *every* client is rejected - typically `LOGIN_REQUIRED` - there's still one path left:
 * [YouTube.getNewPipeStreamUrls] runs the videoId through NewPipeExtractor, which does its own
 * independent extraction and signature deobfuscation and doesn't consult `playabilityStatus` at
 * all. That's the difference between "InnerTube can't play this" and "InnerTube can't play
 * anything".
 */
object InnerTubeStreamResolver {

    /** Tried in order; the first with `playabilityStatus == OK` wins. Clients needing a PoToken
     * are deliberately excluded - minting one is exactly the MuseFlow machinery this path exists
     * to avoid depending on. */
    private val CLIENT_CHAIN: List<YouTubeClient> = listOf(
        YouTubeClient.VISIONOS,
        YouTubeClient.TVHTML5,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.IPADOS,
        YouTubeClient.IOS,
        YouTubeClient.MOBILE,
        YouTubeClient.ANDROID_NO_SDK,
    )

    /** Audio-only itags, best first, ranked by actual bitrate within each codec (251 ~160kbps
     * Opus > 141 ~256kbps AAC > 140 ~128kbps AAC > 250 ~70kbps Opus > 249 ~50kbps Opus >
     * 139 ~48kbps AAC > 172 ~192kbps Vorbis > 171 ~128kbps Vorbis) - this is the fallback used
     * only when [resolveViaNewPipeOnly] has no bitrate metadata to rank by directly. */
    private val AUDIO_ITAG_PREFERENCE = listOf(251, 141, 140, 250, 249, 139, 172, 171)

    /**
     * Highest-quality audio-only stream, or null if nothing playable came back.
     *
     * Returns a [StreamResolution] rather than a bare URL because **the URL alone is not enough**:
     * YouTube's CDN ties a resolved URL to the User-Agent of the client that resolved it and
     * answers 403 to anything else. The winning client's User-Agent therefore has to travel with
     * the URL all the way to the HTTP request that finally fetches it.
     */
    suspend fun resolve(videoId: String): StreamResolution? {
        // NewPipe first, deliberately. The `player()` endpoint hands back URLs whose `n` query
        // parameter is still obfuscated; YouTube answers 403 to those unless it has been
        // transformed by the player JS. NewPipeExtractor performs that transform itself, so its
        // URLs are actually fetchable - whereas a "successful" player() response often isn't.
        resolveViaNewPipeOnly(videoId)?.let { return it }

        for (client in CLIENT_CHAIN) {
            val response = YouTube.player(videoId = videoId, client = client)
                .getOrElse { error ->
                    Log.w(TAG, "player() threw for $videoId on ${client.clientName}: ${error.message}")
                    null
                } ?: continue

            val status = response.playabilityStatus.status
            if (status != "OK") {
                Log.d(TAG, "$videoId rejected by ${client.clientName}: $status")
                continue
            }

            response.bestAudioFormat()?.let { format ->
                val url = format.url!!
                if (!validateStream(url, client.userAgent, format.contentLength)) return@let
                Log.i(TAG, "$videoId resolved via ${client.clientName}")
                return StreamResolution(url = url, userAgent = client.userAgent)
            }

            // Playable, but the URLs are signature-obfuscated - let NewPipe decipher them.
            runCatching { YouTube.newPipePlayer(videoId, response) }
                .getOrNull()
                ?.bestAudioFormat()
                ?.let { format ->
                    val url = format.url!!
                    if (!validateStream(url, client.userAgent, format.contentLength)) return@let
                    Log.i(TAG, "$videoId resolved via ${client.clientName} + NewPipe deobfuscation")
                    return StreamResolution(url = url, userAgent = client.userAgent)
                }
        }

        Log.w(TAG, "no client and no NewPipe extraction produced a stream for $videoId")
        return null
    }

    private val probeClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()

    /** Echo Music reference: range probes reject preview-only URLs before playback starts.
     * Match the playback user agent; never forward account cookies to CDN probes. */
    private suspend fun validateStream(url: String, userAgent: String?, length: Long?): Boolean = withContext(Dispatchers.IO) {
        try {
            val range = if (length != null && length > 0) "bytes=${length - 1}-${length - 1}" else "bytes=0-0"
            val request = okhttp3.Request.Builder().url(url).head().header("Range", range)
                .apply { userAgent?.let { header("User-Agent", it) } }.build()
            probeClient.newCall(request).execute().use { it.isSuccessful || it.code == 405 }
        } catch (_: java.io.IOException) { true }
    }

    /** NewPipe URLs aren't tied to one of our client User-Agents, so none is attached here. */
    private suspend fun resolveViaNewPipeOnly(videoId: String): StreamResolution? = withContext(Dispatchers.IO) {
        val streams = runCatching { YouTube.getNewPipeStreamUrls(videoId) }
            .onFailure { Log.w(TAG, "NewPipe extraction failed for $videoId", it) }
            .getOrNull()
            .orEmpty()

        if (streams.isEmpty()) {
            Log.d(TAG, "NewPipe returned no streams for $videoId")
            return@withContext null
        }

        val ordered = streams.distinctBy { it.second }.sortedBy { (itag, _) ->
            AUDIO_ITAG_PREFERENCE.indexOf(itag).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        val url = ordered.firstOrNull { (_, candidate) -> validateStream(candidate, null, null) }?.second
            ?: return@withContext null

        Log.i(TAG, "$videoId resolved via NewPipe standalone extraction")
        StreamResolution(url = url)
    }
}

/**
 * Picks the best audio-only stream. Adaptive formats are the audio-only ones, so they're preferred
 * and ranked by bitrate; the muxed [PlayerResponse.StreamingData.formats] list is used only if no
 * adaptive audio format carries a URL - those carry video too, but a playable stream beats none.
 */
private fun PlayerResponse.bestAudioFormat(): PlayerResponse.StreamingData.Format? {
    val streaming = streamingData ?: return null

    return streaming.adaptiveFormats
        .filter { it.isAudio && it.isOriginal && it.url != null }
        .maxByOrNull { it.bitrate }
        ?: streaming.formats?.firstOrNull { it.url != null }
}
