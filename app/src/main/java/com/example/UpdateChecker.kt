package com.example

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Best-effort, launch-time-only check against GitHub's public (unauthenticated, no API key)
 * releases API for a MuseFlow version newer than the one installed, following up with a real
 * system notification (see [UpdateNotificationHelper]) when one exists. Fired once per cold start
 * from [MuseFlowApplication.onCreate], the same "launch and forget" shape as
 * [DownloadRepository.backfillMissingCovers] there - no polling, no WorkManager, since a
 * once-per-launch check is all this needs.
 *
 * A fetched tag only triggers a notification when it's different from both the installed
 * `versionName` (nothing to say - already up to date) and the tag most recently persisted via
 * [UpdateCheckRepository] (already told the user about this exact release). Either way, once a
 * decision has been made for a tag, that tag is persisted as "last notified" so it can never
 * trigger a second notification, regardless of whether posting actually succeeded (e.g.
 * notification permission not granted).
 *
 * Every failure mode - offline, GitHub down/rate-limited, malformed JSON, missing fields, package
 * lookup failure - must never crash startup or surface an error; [checkForUpdate]'s caller is
 * expected to wrap this in `runCatching`, mirroring every other launch-time best-effort call in
 * `MuseFlowApplication.onCreate`.
 */
object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Panduu3163/Muse_Flow/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context) = withContext(Dispatchers.IO) {
        val (tagName, releaseUrl) = fetchLatestRelease() ?: return@withContext

        val installedVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName ?: return@withContext

        // Already running this version - nothing to announce.
        if (normalizeVersion(tagName) == normalizeVersion(installedVersion)) return@withContext

        val repository = UpdateCheckRepository.getInstance(context)
        // Already notified about this exact release on a previous launch - never re-notify for
        // the same tag.
        if (tagName == repository.getLastNotifiedTag()) return@withContext

        UpdateNotificationHelper.showUpdateAvailable(context, tagName, releaseUrl)
        repository.setLastNotifiedTag(tagName)
    }

    /** Returns (tag_name, html_url) from GitHub's "latest release" endpoint, or null on any
     * network/HTTP/parsing failure - callers treat null as "nothing to do this launch". */
    private fun fetchLatestRelease(): Pair<String, String>? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MuseFlow/1.0 (Android; +https://github.com/Panduu3163/Muse_Flow)")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
                val url = json.optString("html_url").takeIf { it.isNotBlank() } ?: return null
                tag to url
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Strips a leading "v"/"V" so GitHub's conventional tag ("v1.2.0") compares equal to
     * `versionName` from `build.gradle.kts`, which has no such prefix ("1.2.0"). */
    private fun normalizeVersion(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")
}
