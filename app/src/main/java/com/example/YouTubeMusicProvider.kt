package com.example

import android.content.Context
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
 * Search uses the WEB_REMIX pretend "client" (matches music.youtube.com's own client, gets clean
 * song results). Stream resolution ([getStreamUrl]) also uses WEB_REMIX now, via
 * [YouTubeStreamResolver] - the fully authenticated (real `visitorData` + BotGuard PoToken +
 * signature/n-parameter deciphering) pipeline validated in isolation and now wired in for real.
 * WEB_REMIX requires all of that specifically because it's the client whose formats carry a
 * `signatureCipher` instead of a direct URL - unlike ANDROID_VR/IOS (this provider's previous
 * approach), which sidestepped needing a cipher/PoToken at all but is more prone to being blocked
 * and returns lower/inconsistent quality.
 *
 * This is a from-scratch reimplementation, not copied code, informed by reading Metrolist's
 * (AGPL-3.0) `innertube` module for the request/response shapes and client behavior.
 */
class YouTubeMusicProvider(context: Context) : Provider<TrackResult> {

    private val appContext = context.applicationContext

    override val name = "YouTube Music"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private object Client {
        const val ORIGIN = "https://music.youtube.com"
        const val API_BASE = "$ORIGIN/youtubei/v1/"

        // WEB_REMIX: music.youtube.com's own web client. Used for both search and stream
        // resolution.
        const val WEB_REMIX_NAME = "WEB_REMIX"
        const val WEB_REMIX_VERSION = "1.20260114.03.00"
        const val WEB_REMIX_CLIENT_ID = "67"
        const val WEB_REMIX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        // "Song" search filter chip (opaque protobuf token YouTube Music uses internally).
        const val SEARCH_FILTER_SONG = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
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
            TrackResult(
                id = it.videoId,
                title = it.title,
                artist = it.artist,
                duration = it.duration,
                source = name,
                sourceType = MusicSource.YOUTUBE_MUSIC
            )
        }
    }

    /**
     * Resolves [item] to a directly-playable stream URL via the real, authenticated WEB_REMIX
     * pipeline - see [YouTubeStreamResolver] for the full visitorData/PoToken/cipher/n-transform
     * chain. **Never cache this result** - confirmed by testing, a resolved URL 403s within
     * minutes (the PoToken-bound freshness window). Callers must call this fresh immediately
     * before every actual playback attempt, never reuse an earlier resolution.
     */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? =
        YouTubeStreamResolver.resolve(appContext, item.id)?.let { StreamResolution(url = it) }

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
