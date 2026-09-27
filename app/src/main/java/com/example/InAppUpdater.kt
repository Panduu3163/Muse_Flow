package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads an [AvailableUpdate]'s APK asset and hands it to the system package installer -
 * MuseFlow has no Play Store listing to self-update through (this is a sideloaded, GitHub-releases
 * distributed app), so this is the whole update mechanism: fetch the file this app's own GitHub
 * Releases page already published, then let Android's own installer UI take over. That installer
 * screen (signature/permissions review, an explicit "Install" tap) is the real confirmation gate
 * here - this only gets the file in front of it.
 *
 * The download itself is a plain synchronous OkHttp call, not [DownloadRepository]'s
 * chunked/resumable machinery - an APK download is a one-shot, one-file, few-tens-of-MB transfer
 * with nothing else competing for the same download slots, so reusing that machinery would add
 * complexity (progress tracking, cancellation, notification wiring) this doesn't need.
 */
object InAppUpdater {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Downloads [update]'s APK to this app's own cache dir and opens the system installer on it.
     * Returns false (nothing thrown) on any failure - network, disk, missing asset - so the caller
     * can tell the user it didn't work rather than crash. */
    suspend fun downloadAndInstall(context: Context, update: AvailableUpdate): Boolean {
        val apkUrl = update.apkDownloadUrl ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(apkUrl)
                    .header("Accept", "application/octet-stream")
                    .build()
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "museflow-${update.tagName}.apk")

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false
                    file.outputStream().use { output -> body.byteStream().copyTo(output) }
                }
                if (!file.isFile || file.length() == 0L) return@withContext false

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
                true
            }.getOrDefault(false)
        }
    }
}
