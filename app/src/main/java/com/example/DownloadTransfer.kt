package com.example

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class DownloadTransferFailure(message: String, val retryable: Boolean = false) : IOException(message)
internal data class DownloadTransferResult(val contentType: String?, val bytes: Long)
internal interface DownloadTransfer {
    suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult
    suspend fun artwork(url: String): ByteArray?
}

/** Restart from byte zero. Range resumption is deliberately unsupported without stable validators. */
internal class HttpDownloadTransfer(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(30, TimeUnit.MINUTES).build(),
    private val freeBytes: (File) -> Long = { it.usableSpace },
) : DownloadTransfer {
    override suspend fun fetch(stream: StreamResolution, target: File, progress: suspend (Long, Long?) -> Unit): DownloadTransferResult {
        val request = Request.Builder().url(stream.url).header("Range", "bytes=0-")
            .apply { stream.userAgent?.let { header("User-Agent", it) } }.build()
        return withCall(request) { response ->
            if (!response.isSuccessful) throw DownloadTransferFailure("Download server returned HTTP ${response.code}. Retry when available.",
                response.code in setOf(403, 408, 429) || response.code >= 500)
            val body = response.body
            val size = body.contentLength().takeIf { it >= 0 }
            validateCompleteDownloadRange(response.code, response.header("Content-Range"), size)
            val contentType = response.header("Content-Type")
            if (size == 0L || contentType?.startsWith("text/", ignoreCase = true) == true || contentType?.contains("json", ignoreCase = true) == true)
                throw DownloadTransferFailure("The server did not return an audio file. Retry later.")
            check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true) { "Could not prepare download storage" }
            checkSpace(target.parentFile!!, size ?: 0)
            var bytes = 0L
            var lastReport = 0L
            progress(0, size)
            body.byteStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    checkSpace(target.parentFile!!, count.toLong())
                    output.write(buffer, 0, count)
                    bytes = Math.addExact(bytes, count.toLong())
                    if (size != null && bytes > size) throw DownloadTransferFailure("The response size changed. Retry the download.")
                    val now = System.nanoTime()
                    if (now - lastReport >= TimeUnit.SECONDS.toNanos(1)) { progress(bytes, size); lastReport = now }
                }
                if (bytes <= 0 || (size != null && bytes != size)) throw DownloadTransferFailure("The audio transfer was incomplete. Retry the download.", true)
                output.fd.sync()
            } }
            progress(bytes, size)
            DownloadTransferResult(contentType, bytes)
        }
    }

    private fun checkSpace(directory: File, incoming: Long) {
        if (incoming > freeBytes(directory) - 16L * 1024 * 1024)
            throw DownloadTransferFailure("Not enough free storage. Free some space and retry.")
    }

    override suspend fun artwork(url: String): ByteArray? = try {
        withCall(Request.Builder().url(url).build(), timeoutSeconds = 10) { response ->
            val type = response.header("Content-Type")
            if (!response.isSuccessful || response.body.contentLength() > 4L * 1024 * 1024 ||
                type?.startsWith("text/", ignoreCase = true) == true || type?.contains("json", ignoreCase = true) == true) return@withCall null
            val bytes = ByteArrayOutputStream()
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 4 * 1024 * 1024 - bytes.size()) return@withCall null
                    bytes.write(buffer, 0, count)
                }
            }
            bytes.toByteArray().takeIf { it.isNotEmpty() }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    private suspend fun <T> withCall(request: Request, timeoutSeconds: Long? = null, block: suspend (Response) -> T): T = coroutineScope {
        val call: Call = client.newCall(request)
        timeoutSeconds?.let { call.timeout().timeout(it, TimeUnit.SECONDS) }
        val transfer = async(Dispatchers.IO) {
            try { call.execute().use { block(it) } }
            catch (error: Exception) { currentCoroutineContext().ensureActive(); throw error }
        }
        try { transfer.await() }
        catch (cancelled: CancellationException) { call.cancel(); throw cancelled }
    }
}

internal fun validateCompleteDownloadRange(code: Int, range: String?, length: Long?) {
    if (code != 206) return
    val match = range?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it.trim()) }
    val start = match?.groupValues?.get(1)?.toLongOrNull()
    val end = match?.groupValues?.get(2)?.toLongOrNull()
    val total = match?.groupValues?.get(3)?.toLongOrNull()
    if (start != 0L || end == null || total == null || total <= 0 || end != total - 1 || (length != null && length != total))
        throw DownloadTransferFailure("The server returned only part of this song. Retry later.")
}
