package com.example

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** The published GitHub release body for the version installed on this device. */
object InstalledReleaseNotes {
    private const val API = "https://api.github.com/repos/Panduu3163/Muse_Flow/releases/tags/"
    private const val PREFS = "installed_release_notes"
    private const val VERSION = "version"
    private const val BODY = "body"
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val _body = MutableStateFlow<String?>(null)
    val body: StateFlow<String?> = _body.asStateFlow()

    fun cached(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(BODY, null)?.takeIf {
            prefs.getString(VERSION, null) == BuildConfig.VERSION_NAME && it.isNotBlank()
        }
    }

    /** Successful fetches are saved once per installed version; offline/unpublished versions retry. */
    suspend fun prefetch(context: Context) = mutex.withLock {
        cached(context)?.let { _body.value = it; return@withLock }
        val version = BuildConfig.VERSION_NAME
        val tags = listOf(version, "v$version", version.removeSuffix("-beta"),
            "v${version.removeSuffix("-beta")}").distinct()
        val releaseBody = withContext(Dispatchers.IO) {
            tags.firstNotNullOfOrNull { tag ->
                runCatching {
                    val request = Request.Builder().url(API + tag)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "MuseFlow/$version (Android)").build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val json = JSONObject(response.body?.string() ?: return@use null)
                        if (json.optString("tag_name") != tag) return@use null
                        json.optString("body").trim().takeIf { it.isNotBlank() }
                    }
                }.getOrNull()
            }
        } ?: return@withLock
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VERSION, version).putString(BODY, releaseBody).apply()
        _body.value = releaseBody
    }
}
