package com.example.ytcipher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fetches YouTube's current `player_ias` JS - the file that actually contains the sig/n-transform
 * logic [YtCipherWebView] executes. Two steps: read the current player hash off the public
 * `iframe_api` bootstrap response, then download that hash's `base.js`. Cached in-memory only -
 * this is a standalone, isolated-test module, not wired into any persistent app state yet. */
object YtPlayerJsFetcher {
    private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
    private const val PLAYER_JS_URL_TEMPLATE = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_GB/base.js"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class PlayerJs(val hash: String, val source: String)

    @Volatile private var cached: PlayerJs? = null

    suspend fun getPlayerJs(forceRefresh: Boolean = false): PlayerJs? = withContext(Dispatchers.IO) {
        if (!forceRefresh) cached?.let { return@withContext it }

        val hash = fetchPlayerHash() ?: return@withContext null
        val source = downloadPlayerJs(hash) ?: return@withContext null
        PlayerJs(hash, source).also { cached = it }
    }

    private fun fetchPlayerHash(): String? {
        val request = Request.Builder().url(IFRAME_API_URL).header("User-Agent", USER_AGENT).build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        } ?: return null
        // The iframe_api bootstrap embeds the player path with escaped slashes, e.g.
        // "\/s\/player\/<hash>\/...", so the extractor's generic hash patterns won't match this
        // specific escaped form - handled with its own small pattern here.
        val match = Regex("""\\?/s\\?/player\\?/([a-zA-Z0-9_-]+)\\?/""").find(body) ?: return null
        return match.groupValues[1]
    }

    private fun downloadPlayerJs(hash: String): String? {
        val request = Request.Builder()
            .url(PLAYER_JS_URL_TEMPLATE.format(hash))
            .header("User-Agent", USER_AGENT)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }
}
