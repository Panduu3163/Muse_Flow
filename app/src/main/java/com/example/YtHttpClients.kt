package com.example

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * One shared [OkHttpClient] for every YouTube-facing call site (search, browse, stream
 * resolution, player.js fetch) - previously each of [YouTubeStreamResolver],
 * [YouTubeMusicProvider], and `YtPlayerJsFetcher` built its own client with defaults, which
 * meant no TCP/TLS connection reuse between them and no on-disk response cache. [init] must be
 * called once (from [MuseFlowApplication]) before [client] is read.
 */
object YtHttpClients {

    @Volatile
    private var _client: OkHttpClient? = null

    val client: OkHttpClient
        get() = _client ?: synchronized(this) {
            _client ?: buildClient(fallbackCacheDir = null).also { _client = it }
        }

    fun init(context: Context) {
        if (_client != null) return
        synchronized(this) {
            if (_client == null) {
                _client = buildClient(File(context.applicationContext.cacheDir, "http_cache"))
            }
        }
    }

    private fun buildClient(fallbackCacheDir: File?): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                fallbackCacheDir?.let { cache(Cache(it, CACHE_SIZE_BYTES)) }
            }
            .build()

    private const val CACHE_SIZE_BYTES = 32L * 1024 * 1024
}
