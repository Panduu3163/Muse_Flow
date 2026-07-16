package com.example

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks directly to YouTube Music's private "InnerTube" API - the same undocumented endpoints
 * the official web/mobile clients use, reverse-engineered by the open-source community (see
 * Metrolist: https://github.com/MetrolistGroup/Metrolist). There is no public API for this.
 *
 * Two different pretend "clients" are used deliberately:
 *  - WEB_REMIX for search: matches music.youtube.com's own client, gets clean song results.
 *  - ANDROID_VR for stream resolution: this client type returns direct, un-ciphered stream
 *    URLs. Most other clients (including WEB_REMIX) require deciphering a `signatureCipher`/`n`
 *    parameter against YouTube's ever-changing player.js, which in turn requires a JS engine
 *    (WebView) and often a BotGuard-based "poToken" - a whole subsystem on its own. ANDROID_VR
 *    sidesteps all of that at the cost of not working for some restricted/private content.
 *
 * This is a from-scratch reimplementation, not copied code, informed by reading Metrolist's
 * (AGPL-3.0) `innertube` module for the request/response shapes and client behavior.
 */
class YouTubeMusicProvider : Provider<TrackResult> {

    override val name = "YouTube Music"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()
    private val playerResponseAdapter = moshi.adapter(YtPlayerResponse::class.java)

    private object Client {
        const val ORIGIN = "https://music.youtube.com"
        const val API_BASE = "$ORIGIN/youtubei/v1/"

        // WEB_REMIX: music.youtube.com's own web client. Used for search only.
        const val WEB_REMIX_NAME = "WEB_REMIX"
        const val WEB_REMIX_VERSION = "1.20260114.03.00"
        const val WEB_REMIX_CLIENT_ID = "67"
        const val WEB_REMIX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        // "Song" search filter chip (opaque protobuf token YouTube Music uses internally).
        const val SEARCH_FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

        // ANDROID_VR 1.43.32: returns direct un-ciphered stream URLs, no PoToken required.
        // Version pinned deliberately - Metrolist's own notes say this exact build avoids
        // adaptive-bitrate audio stuttering.
        const val ANDROID_VR_NAME = "ANDROID_VR"
        const val ANDROID_VR_VERSION = "1.43.32"
        const val ANDROID_VR_CLIENT_ID = "28"
        const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)"

        // IOS: fallback client, also useSignatureTimestamp=false / loginSupported=false.
        const val IOS_NAME = "IOS"
        const val IOS_VERSION = "21.03.1"
        const val IOS_CLIENT_ID = "5"
        const val IOS_USER_AGENT = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)"
    }

    class YouTubeMusicException(message: String) : Exception(message)

    /** Searches YouTube Music for songs matching [query]. Runs on [Dispatchers.IO]. */
    override suspend fun search(query: String): List<TrackResult> = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("context", buildContext(Client.WEB_REMIX_NAME, Client.WEB_REMIX_VERSION))
            put("query", query)
            put("params", Client.SEARCH_FILTER_SONG)
        }

        val responseJson = postJson(
            path = "search",
            body = requestBody,
            clientId = Client.WEB_REMIX_CLIENT_ID,
            clientVersion = Client.WEB_REMIX_VERSION,
            userAgent = Client.WEB_REMIX_USER_AGENT
        )

        parseSearchResults(responseJson).map {
            TrackResult(id = it.videoId, title = it.title, artist = it.artist, duration = it.duration, source = name)
        }
    }

    /** A "pretend" YouTube client config, just enough to request un-ciphered stream URLs. */
    private data class PlayerClient(
        val name: String,
        val version: String,
        val clientId: String,
        val userAgent: String,
        val includeUserAgentInContext: Boolean = false,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: String? = null,
    )

    // Tried in order. Both avoid signature deciphering/PoToken, but YouTube's bot-check
    // enforcement varies by client, IP, and over time - what works can change day to day,
    // hence trying more than one before giving up.
    private val playerClients = listOf(
        PlayerClient(
            name = Client.ANDROID_VR_NAME,
            version = Client.ANDROID_VR_VERSION,
            clientId = Client.ANDROID_VR_CLIENT_ID,
            userAgent = Client.ANDROID_VR_USER_AGENT,
            includeUserAgentInContext = true,
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
        ),
        PlayerClient(
            name = Client.IOS_NAME,
            version = Client.IOS_VERSION,
            clientId = Client.IOS_CLIENT_ID,
            userAgent = Client.IOS_USER_AGENT,
        ),
    )

    /**
     * Resolves [item] to a direct, playable audio stream by trying each client in
     * [playerClients] in turn. Throws with the last failure's detail if every client fails.
     *
     * The returned [StreamResolution.userAgent] MUST be used when actually fetching the stream
     * URL - YouTube's CDN ties the URL to the User-Agent that requested it and rejects a
     * mismatched one.
     */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (client in playerClients) {
            try {
                val url = resolveStreamUrl(item.id, client) ?: continue
                return@withContext StreamResolution(url = url, userAgent = client.userAgent)
            } catch (e: YouTubeMusicException) {
                lastError = "[${client.name}] ${e.message}"
            }
        }
        throw YouTubeMusicException(lastError ?: "No player client succeeded")
    }

    private fun resolveStreamUrl(videoId: String, client: PlayerClient): String? {
        val requestBody = JSONObject().apply {
            put("context", buildContext(client))
            put("videoId", videoId)
            put("playlistId", JSONObject.NULL)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val responseJson = postJson(
            path = "player",
            body = requestBody,
            clientId = client.clientId,
            clientVersion = client.version,
            userAgent = client.userAgent
        )

        val playerResponse = playerResponseAdapter.fromJson(responseJson.toString())
            ?: throw YouTubeMusicException("Could not parse player response")

        val status = playerResponse.playabilityStatus?.status
        if (status != "OK") {
            throw YouTubeMusicException(
                "Video not playable (status=$status): ${playerResponse.playabilityStatus?.reason ?: "unknown reason"}"
            )
        }

        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudioOnly && it.url != null }
            .orEmpty()

        if (audioFormats.isEmpty()) {
            throw YouTubeMusicException(
                "No direct audio URL available for this video (it may require signature deciphering, which this provider doesn't implement)"
            )
        }

        // Highest bitrate audio-only stream.
        return audioFormats.maxByOrNull { it.bitrate ?: 0 }?.url
    }

    private fun buildContext(client: PlayerClient): JSONObject {
        val clientJson = JSONObject().apply {
            put("clientName", client.name)
            put("clientVersion", client.version)
            put("gl", "US")
            put("hl", "en")
            if (client.includeUserAgentInContext) put("userAgent", client.userAgent)
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
        }
        return JSONObject().apply {
            put("client", clientJson)
            put("request", JSONObject().apply {
                put("internalExperimentFlags", JSONArray())
                put("useSsl", true)
            })
            put("user", JSONObject().apply {
                put("lockedSafetyMode", false)
            })
        }
    }

    private fun buildContext(clientName: String, clientVersion: String): JSONObject {
        val clientJson = JSONObject().apply {
            put("clientName", clientName)
            put("clientVersion", clientVersion)
            put("gl", "US")
            put("hl", "en")
        }
        return JSONObject().apply {
            put("client", clientJson)
            put("request", JSONObject().apply {
                put("internalExperimentFlags", JSONArray())
                put("useSsl", true)
            })
            put("user", JSONObject().apply {
                put("lockedSafetyMode", false)
            })
        }
    }

    private fun postJson(
        path: String,
        body: JSONObject,
        clientId: String,
        clientVersion: String,
        userAgent: String
    ): JSONObject {
        val request = Request.Builder()
            .url("${Client.API_BASE}$path?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", clientId) // yes, the client *id*, not the name
            .header("X-YouTube-Client-Version", clientVersion)
            .header("X-Origin", Client.ORIGIN)
            .header("Referer", "${Client.ORIGIN}/")
            .header("User-Agent", userAgent)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string()
                ?: throw YouTubeMusicException("Empty response from YouTube Music (HTTP ${response.code})")
            if (!response.isSuccessful) {
                throw YouTubeMusicException("YouTube Music request failed: HTTP ${response.code} - $bodyString")
            }
            return JSONObject(bodyString)
        }
    }

    /**
     * Search responses are a deeply-nested, mostly-optional renderer tree that changes shape
     * often. Traversing it defensively with org.json (rather than strict typed models) is more
     * resilient to the odd missing/renamed field.
     */
    private fun parseSearchResults(root: JSONObject): List<YtSearchResult> {
        val results = mutableListOf<YtSearchResult>()

        val sections = root
            .optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return results

        for (i in 0 until sections.length()) {
            val shelfItems = sections.optJSONObject(i)
                ?.optJSONObject("musicShelfRenderer")
                ?.optJSONArray("contents")
                ?: continue

            for (j in 0 until shelfItems.length()) {
                val renderer = shelfItems.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer")
                    ?: continue
                parseSongRenderer(renderer)?.let { results.add(it) }
            }
        }

        return results
    }

    private fun parseSongRenderer(renderer: JSONObject): YtSearchResult? {
        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null

        fun flexColumnRuns(index: Int): JSONArray? = flexColumns
            .optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        val titleRuns = flexColumnRuns(0) ?: return null
        val title = titleRuns.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() } ?: return null

        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: titleRuns.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitleRuns = flexColumnRuns(1)
        var artist: String? = null
        var duration: String? = null
        val durationPattern = Regex("""^\d{1,2}:\d{2}$""")
        if (subtitleRuns != null) {
            for (k in 0 until subtitleRuns.length()) {
                val text = subtitleRuns.optJSONObject(k)?.optString("text")?.trim() ?: continue
                if (text.isBlank() || text == "•") continue
                if (durationPattern.matches(text)) {
                    duration = text
                } else if (artist == null) {
                    artist = text
                }
            }
        }

        return YtSearchResult(
            videoId = videoId,
            title = title,
            artist = artist ?: "Unknown artist",
            duration = duration
        )
    }
}
