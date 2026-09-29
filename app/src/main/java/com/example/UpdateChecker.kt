package com.example

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A GitHub release newer than the installed build - [changelog] is that release's own
 * description text (its GitHub Releases body, written when the release was published, not
 * anything this app generates), [apkDownloadUrl] is its first `.apk` asset, null if the release
 * has none (e.g. source-only tags) - [InAppUpdater] can't offer a direct install without one. */
data class AvailableUpdate(
    val tagName: String,
    val releaseUrl: String,
    val changelog: String,
    val apkDownloadUrl: String?,
)

/**
 * Best-effort, launch-time-only check against GitHub's public (unauthenticated, no API key)
 * releases API for a MuseFlow version newer than the one installed. Fired once per cold start from
 * [MuseFlowApplication.onCreate], the same "launch and forget" shape as
 * [DownloadRepository.backfillMissingCovers] there - no polling, no WorkManager, since a
 * once-per-launch check is all this needs.
 *
 * Two independent things happen when a newer release is found:
 * 1. A background notification via [UpdateNotificationHelper], gated by [UpdateCheckRepository]'s
 *    persisted "already notified about this tag" flag so it never repeats for the same release.
 * 2. [availableUpdate] is set, which is what drives the in-app popup ([MainActivity]'s own
 *    `UpdateAvailableDialog`). This is *not* gated by the same persisted flag - dismissing that
 *    popup with "Next time" only clears [dismissForNow] in memory, so it reappears on the very
 *    next cold start rather than being silenced until an actual newer tag ships. The persisted
 *    gate exists for the notification specifically, so that channel doesn't repeat itself; the
 *    popup is meant to keep gently asking until the user is actually on the new version.
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

    private val _availableUpdate = MutableStateFlow<AvailableUpdate?>(null)
    val availableUpdate: StateFlow<AvailableUpdate?> = _availableUpdate.asStateFlow()

    /** "Next time" on the popup - see this object's own doc for why this is in-memory only. */
    fun dismissForNow() {
        _availableUpdate.value = null
    }

    suspend fun checkForUpdate(context: Context) = withContext(Dispatchers.IO) {
        val release = fetchLatestRelease() ?: return@withContext

        val installedVersion = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName ?: return@withContext

        // Nothing to announce unless the release is actually newer - not merely different -
        // than what's installed, otherwise a locally-built/sideloaded version ahead of the last
        // published release (e.g. this very build, before its own tag exists on GitHub) would
        // wrongly show its own older release as an "update" forever.
        if (!isNewerVersion(release.tagName, installedVersion)) return@withContext

        val repository = UpdateCheckRepository.getInstance(context)
        // Already notified about this exact release on a previous launch - never re-notify for
        // the same tag. Only gates the background notification - see this object's own doc.
        if (release.tagName != repository.getLastNotifiedTag()) {
            UpdateNotificationHelper.showUpdateAvailable(context, release.tagName, release.releaseUrl)
            repository.setLastNotifiedTag(release.tagName)
        }

        _availableUpdate.value = release
    }

    /** Returns the parsed release, or null on any network/HTTP/parsing failure - callers treat
     * null as "nothing to do this launch". */
    private fun fetchLatestRelease(): AvailableUpdate? {
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
                val changelog = json.optString("body").takeIf { it.isNotBlank() }
                    ?: "See the release notes on GitHub for what's new."
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                            break
                        }
                    }
                }
                AvailableUpdate(tagName = tag, releaseUrl = url, changelog = changelog, apkDownloadUrl = apkUrl)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** First three numeric components; suffix numbers such as rc1 are compared separately. */
    private fun versionComponents(version: String): List<Int> =
        Regex("""\d+""").findAll(version).take(3).map { it.value.toInt() }.toList()

    private fun prereleaseRank(version: String): Int = when {
        Regex("(?i)(?:^|[-_])rc\\d*").containsMatchIn(version) -> 2
        Regex("(?i)(?:^|[-_])beta\\d*").containsMatchIn(version) -> 1
        else -> 3
    }

    private fun prereleaseNumber(version: String): Int =
        Regex("(?i)(?:^|[-_])(?:rc|beta)(\\d+)").find(version)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Numeric component comparison (e.g. "1.4.0" vs "1.10.0") rather than string equality - a
     * release tag only counts as an update if it's genuinely greater than what's installed, not
     * merely different from it. Missing components count as 0. */
    internal fun isNewerVersion(remoteTag: String, installedVersion: String): Boolean {
        val remote = versionComponents(remoteTag)
        val local = versionComponents(installedVersion)
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        val remoteRank = prereleaseRank(remoteTag)
        val localRank = prereleaseRank(installedVersion)
        return if (remoteRank != localRank) remoteRank > localRank
        else remoteRank < 3 && prereleaseNumber(remoteTag) > prereleaseNumber(installedVersion)
    }

    /** [checkNow]'s outcome - kept separate from [checkForUpdate]'s own [availableUpdate]/
     * notification side effects (see [checkNow]'s own doc on why). */
    sealed interface UpdateCheckResult {
        data class UpToDate(val installedVersion: String) : UpdateCheckResult
        data class UpdateAvailable(val update: AvailableUpdate) : UpdateCheckResult
        /** Network/GitHub/parsing failure, or the installed version couldn't be read. */
        data object Error : UpdateCheckResult
    }

    /**
     * A foreground, user-initiated check (Settings > Check for updates), returning the outcome
     * directly to the caller rather than going through [availableUpdate]/[UpdateNotificationHelper]
     * the way [checkForUpdate]'s own launch-time check does - tapping "Check for updates" while
     * already looking at a screen built to show that exact result shouldn't *also* pop the
     * launch-time update dialog on top of it or fire a duplicate system notification.
     */
    suspend fun checkNow(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installedVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: return@withContext UpdateCheckResult.Error
        val release = fetchLatestRelease() ?: return@withContext UpdateCheckResult.Error
        if (isNewerVersion(release.tagName, installedVersion)) {
            UpdateCheckResult.UpdateAvailable(release)
        } else {
            UpdateCheckResult.UpToDate(installedVersion)
        }
    }
}
