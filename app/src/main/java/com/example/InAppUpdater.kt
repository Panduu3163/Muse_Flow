package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class UpdatePackageIdentity(val packageName: String, val version: Long, val signers: Set<String>)

enum class UpdateInstallStage { DOWNLOAD, VERIFICATION, INSTALLER }
sealed interface UpdateInstallOutcome {
    data object InstallerOpened : UpdateInstallOutcome
    data class Failed(val stage: UpdateInstallStage, val message: String) : UpdateInstallOutcome
    val userMessage: String get() = when (this) {
        InstallerOpened -> "Installer opened. Finish installation in Android."
        is Failed -> message
    }
}

internal fun updatePackageProblem(installed: UpdatePackageIdentity, candidate: UpdatePackageIdentity): String? = when {
    candidate.packageName != installed.packageName -> "This APK belongs to a different app variant. Choose the release for this app."
    candidate.version <= installed.version -> "This APK does not have a newer version code than the installed app."
    installed.signers.isEmpty() || candidate.signers != installed.signers -> "This APK does not match the installed app's signing certificate."
    else -> null
}
private class UpdatePackageRejected(message: String) : Exception(message)

/** Conservative same-flavor, newer-version, same-current-signers gate before Android's installer. */
internal fun canInstallUpdate(installed: UpdatePackageIdentity, candidate: UpdatePackageIdentity): Boolean =
    updatePackageProblem(installed, candidate) == null

/** A cancellable bounded transfer; success means installer handoff, never installation completed. */
object InAppUpdater {
    internal const val MAX_APK_BYTES = 128L * 1024 * 1024
    private val gate = Mutex()
    private val httpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()

    suspend fun downloadAndInstall(context: Context, update: AvailableUpdate): UpdateInstallOutcome {
        val apkUrl = update.apkDownloadUrl ?: return UpdateInstallOutcome.Failed(UpdateInstallStage.DOWNLOAD, "This release has no APK attached. Open the release page instead.")
        if (!gate.tryLock()) return UpdateInstallOutcome.Failed(UpdateInstallStage.DOWNLOAD, "Another update download is running. Wait for it to finish.")
        var file: File? = null
        var handedOff = false
        var stage = UpdateInstallStage.DOWNLOAD
        try {
            val ready = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "updates")
                check(directory.isDirectory || directory.mkdirs())
                // Release tags are untrusted display text, never file-system paths.
                val target = File.createTempFile("museflow-update-", ".apk", directory)
                file = target
                val call = httpClient.newCall(Request.Builder().url(apkUrl)
                    .header("Accept", "application/octet-stream").build())
                coroutineScope {
                    val transfer = async(Dispatchers.IO) {
                        try {
                            call.execute().use { response ->
                                check(response.isSuccessful)
                                val expected = response.body.contentLength()
                                check(expected in -1..MAX_APK_BYTES && expected != 0L)
                                copyUpdateApk(response.body.byteStream(), target, expected)
                            }
                        } catch (error: Exception) { currentCoroutineContext().ensureActive(); throw error }
                    }
                    try { transfer.await() } finally { call.cancel() }
                }
                currentCoroutineContext().ensureActive()
                stage = UpdateInstallStage.VERIFICATION
                val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else {
                    @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
                }
                @Suppress("DEPRECATION")
                val installed = context.packageManager.getPackageInfo(context.packageName, flags)
                @Suppress("DEPRECATION")
                val archive = context.packageManager.getPackageArchiveInfo(target.absolutePath, flags)
                    ?: error("Invalid APK")
                updatePackageProblem(installed.updateIdentity(), archive.updateIdentity())?.let { throw UpdatePackageRejected(it) }
                target
            }
            stage = UpdateInstallStage.INSTALLER
            withContext(Dispatchers.Main) {
                ensureActive()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                handedOff = true
            }
            return UpdateInstallOutcome.InstallerOpened
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: UpdatePackageRejected) { return UpdateInstallOutcome.Failed(UpdateInstallStage.VERIFICATION, error.message!!) }
        catch (_: Exception) { return UpdateInstallOutcome.Failed(stage, when (stage) {
            UpdateInstallStage.DOWNLOAD -> "Couldn't download the complete update. Check your connection and free storage, then retry."
            UpdateInstallStage.VERIFICATION -> "Couldn't verify this APK. Download the correct release for this app and retry."
            UpdateInstallStage.INSTALLER -> "Couldn't open Android's installer. Check installation permission and retry."
        }) }
        finally {
            if (!handedOff) file?.delete()
            gate.unlock()
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.updateIdentity(): UpdatePackageIdentity {
    val certificates = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
    return UpdatePackageIdentity(packageName.orEmpty(), PackageInfoCompat.getLongVersionCode(this),
        certificates.orEmpty().map { it.toCharsString() }.toSet())
}

internal suspend fun copyUpdateApk(input: java.io.InputStream, target: File, expected: Long) {
    check(expected in -1..InAppUpdater.MAX_APK_BYTES && expected != 0L)
    var count = 0L
    input.use { source -> target.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = source.read(buffer)
            if (size < 0) break
            count += size
            check(count <= InAppUpdater.MAX_APK_BYTES && (expected < 0 || count <= expected))
            output.write(buffer, 0, size)
        }
        check(count > 0 && (expected < 0 || count == expected))
        output.fd.sync()
    } }
}
