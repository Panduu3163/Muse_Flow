package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ytcipher.YtCipherDeobfuscator
import com.example.ytcipher.YtCipherFunctionExtractor
import com.example.ytcipher.YtPlayerJsFetcher
import com.example.ytcipher.potoken.PoTokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "YtStreamResolver"

/**
 * Resolves a YouTube video id to a genuinely playable stream URL via an authenticated WEB_REMIX
 * `/player` request - the real pipeline validated in isolation (`ProviderTestScreen`'s "Full Auth
 * Resolve"/"Play via real ExoPlayer pipeline" buttons, since removed) and now wired into actual
 * playback.
 *
 * Combines three pieces built separately: a real `visitorData` (fetched from
 * `music.youtube.com/sw.js_data`, same source Metrolist's `YouTube.visitorData()` uses),
 * [PoTokenGenerator]'s BotGuard-minted PoToken, and [YtCipherDeobfuscator]'s signature/n-parameter
 * deciphering - see each call site below for exactly where every piece goes in the request,
 * following Metrolist's wiring (GPL-3.0, https://github.com/MetrolistGroup/Metrolist).
 *
 * **Never cache the result.** Confirmed by testing: a resolved URL 403s within minutes (the
 * PoToken-bound freshness window is much shorter than the `expire=` timestamp embedded in the URL
 * suggests). [resolve] must be called fresh immediately before every actual playback attempt -
 * see [PlaybackService]'s `museflow.invalid` resolving data source, which calls this on every
 * single HTTP (re)open, not just once per track.
 */
object YouTubeStreamResolver {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // visitorData itself isn't the short-lived part (only the minted stream URL/PoToken pairing
    // is) - fetched once and reused, refreshed only if a resolve attempt fails outright.
    private val visitorDataMutex = Mutex()
    private var cachedVisitorData: String? = null

    /** Resolves [videoId] to a directly-playable URL, or null if any step fails. Safe to call
     * from any thread - internally dispatches to [Dispatchers.IO]. */
    suspend fun resolve(context: Context, videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            YtCipherDeobfuscator.initialize(context)

            val visitorData = getVisitorData(forceRefresh = false)
                ?: run { Log.w(TAG, "[$videoId] could not obtain visitorData"); return@withContext null }

            val playerJs = YtPlayerJsFetcher.getPlayerJs()
                ?: run { Log.w(TAG, "[$videoId] could not fetch player.js"); return@withContext null }
            val signatureTimestamp = YtCipherFunctionExtractor.extractSignatureTimestamp(playerJs.source, context, playerJs.hash)
                ?: run { Log.w(TAG, "[$videoId] could not determine signatureTimestamp"); return@withContext null }

            val poToken = PoTokenGenerator.generate(context, videoId, visitorData)
                ?: run { Log.w(TAG, "[$videoId] PoTokenGenerator.generate() returned null"); return@withContext null }

            var playerResponse = requestPlayerEndpoint(videoId, visitorData, signatureTimestamp, poToken.playerRequestPoToken)
            var status = playerResponse.optJSONObject("playabilityStatus")?.optString("status")

            // A stale/invalid visitorData surfaces as an auth-flavored playability failure - retry
            // once with a freshly-fetched one before giving up (distinct from the stream-URL
            // freshness problem this resolver exists to solve in the first place).
            if (status != "OK" && isLikelyAuthFailure(status)) {
                Log.w(TAG, "[$videoId] playabilityStatus=$status, retrying once with a fresh visitorData")
                val freshVisitorData = getVisitorData(forceRefresh = true)
                    ?: return@withContext null
                playerResponse = requestPlayerEndpoint(videoId, freshVisitorData, signatureTimestamp, poToken.playerRequestPoToken)
                status = playerResponse.optJSONObject("playabilityStatus")?.optString("status")
            }

            if (status != "OK") {
                Log.w(TAG, "[$videoId] playabilityStatus=$status - not playable")
                return@withContext null
            }

            val format = pickBestAudioFormat(playerResponse)
                ?: run { Log.w(TAG, "[$videoId] no audio format in adaptiveFormats"); return@withContext null }

            val directUrl = format.optString("url").takeIf { it.isNotBlank() }
            val signatureCipher = format.optString("signatureCipher").takeIf { it.isNotBlank() }
                ?: format.optString("cipher").takeIf { it.isNotBlank() }

            var streamUrl = when {
                directUrl != null -> directUrl
                signatureCipher != null -> YtCipherDeobfuscator.deobfuscateStreamUrl(signatureCipher)
                else -> null
            } ?: run { Log.w(TAG, "[$videoId] could not resolve a stream URL"); return@withContext null }

            // n-transform (throttle avoidance), THEN append the per-video PoToken - Metrolist's
            // exact ordering (YTPlayerUtils.kt).
            streamUrl = YtCipherDeobfuscator.transformNParamInUrl(streamUrl)
            val separator = if ("?" in streamUrl) "&" else "?"
            streamUrl = "$streamUrl${separator}pot=${Uri.encode(poToken.streamingDataPoToken)}"

            streamUrl
        } catch (e: Exception) {
            Log.e(TAG, "[$videoId] resolve() failed", e)
            null
        }
    }

    private fun isLikelyAuthFailure(status: String?): Boolean =
        status in setOf("LOGIN_REQUIRED", "ERROR", "UNPLAYABLE")

    /** Same source Metrolist's `YouTube.visitorData()` reads: `sw.js_data` is a JSONP-ish blob
     * (anti-XSSI `)]}'` prefix, then a JSON array) that embeds a fresh visitorData token
     * (base64, conventionally starting "Cgt"/"Cgs") among `data[0][2]`'s elements. */
    private suspend fun getVisitorData(forceRefresh: Boolean): String? = visitorDataMutex.withLock {
        if (!forceRefresh) cachedVisitorData?.let { return it }
        val fetched = fetchVisitorData()
        if (fetched != null) cachedVisitorData = fetched
        fetched
    }

    private fun fetchVisitorData(): String? {
        val request = Request.Builder()
            .url("https://music.youtube.com/sw.js_data")
            .header("User-Agent", WEB_USER_AGENT)
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        } ?: return null

        val jsonText = body.removePrefix(")]}'").trimStart('\n')
        val third = JSONArray(jsonText).getJSONArray(0).optJSONArray(2) ?: return null
        val pattern = Regex("^Cg[ts]")
        for (i in 0 until third.length()) {
            val value = third.opt(i)
            if (value is String && pattern.containsMatchIn(value)) return value
        }
        return null
    }

    private fun requestPlayerEndpoint(
        videoId: String,
        visitorData: String,
        signatureTimestamp: Int,
        playerRequestPoToken: String
    ): JSONObject {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_CLIENT_VERSION)
                    put("gl", "US")
                    put("hl", "en")
                    put("visitorData", visitorData)
                })
                put("request", JSONObject().apply {
                    put("internalExperimentFlags", JSONArray())
                    put("useSsl", true)
                })
                put("user", JSONObject().apply { put("lockedSafetyMode", false) })
            })
            put("videoId", videoId)
            put("playlistId", JSONObject.NULL)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("signatureTimestamp", signatureTimestamp)
                })
            })
            put("serviceIntegrityDimensions", JSONObject().apply {
                put("poToken", playerRequestPoToken)
            })
        }

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", "67") // WEB_REMIX's clientId
            .header("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)
            .header("X-Goog-Visitor-Id", visitorData)
            .header("X-Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", WEB_USER_AGENT)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            JSONObject(response.body?.string() ?: error("Empty /player response (HTTP ${response.code})"))
        }
    }

    private fun pickBestAudioFormat(playerResponse: JSONObject): JSONObject? {
        val formats = playerResponse.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        var best: JSONObject? = null
        var bestBitrate = -1
        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            if (!format.optString("mimeType").startsWith("audio/")) continue
            val bitrate = format.optInt("bitrate")
            if (bitrate > bestBitrate) {
                bestBitrate = bitrate
                best = format
            }
        }
        return best
    }

    private const val WEB_REMIX_CLIENT_VERSION = "1.20260114.03.00"
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
}
