package com.example

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DownloadTransferTest {
    @Test fun fullRangeIsRequiredForPartialResponses() {
        validateCompleteDownloadRange(206, "bytes 0-9/10", 10)
        listOf(null, "", "bytes 0-4/10", "bytes 2-9/10", "bytes 0-9/*", "bytes 0-999999999999999999999/10").forEach {
            try { validateCompleteDownloadRange(206, it, 10); fail("Incomplete range: $it") } catch (_: DownloadTransferFailure) { }
        }
        try { validateCompleteDownloadRange(206, "bytes 0-9/10", 5); fail("Length mismatch") } catch (_: DownloadTransferFailure) { }
    }

    @Test fun realHttpTransferCarriesResolverUserAgentAndVerifiesLength() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var agent: String? = null; var range: String? = null
        val payload = "audio payload".toByteArray()
        server.createContext("/audio") { exchange ->
            agent = exchange.requestHeaders.getFirst("User-Agent"); range = exchange.requestHeaders.getFirst("Range")
            exchange.responseHeaders.add("Content-Type", "audio/webm")
            exchange.responseHeaders.add("Content-Range", "bytes 0-${payload.lastIndex}/${payload.size}")
            exchange.sendResponseHeaders(206, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        val file = File.createTempFile("audio-transfer", ".part")
        try {
            var finalProgress = 0L
            val result = HttpDownloadTransfer(freeBytes = { Long.MAX_VALUE }).fetch(
                StreamResolution("http://127.0.0.1:${server.address.port}/audio", "Resolved-Agent"), file) { bytes, _ -> finalProgress = bytes }
            assertArrayEquals(payload, file.readBytes()); assertEquals(payload.size.toLong(), result.bytes)
            assertEquals(payload.size.toLong(), finalProgress)
            assertEquals("Resolved-Agent", agent); assertEquals("bytes=0-", range)
        } finally { server.stop(0); file.delete() }
    }

    @Test fun cancellationClosesABlockedSocketWithoutWaitingForReadTimeout() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newSingleThreadExecutor(); server.executor = executor
        val sending = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        server.createContext("/slow") { exchange ->
            try {
                exchange.responseHeaders.add("Content-Type", "audio/webm")
                exchange.sendResponseHeaders(200, 1_000_000)
                exchange.responseBody.write(byteArrayOf(1)); exchange.responseBody.flush()
                sending.complete(Unit); release.await()
            } finally { exchange.close() }
        }
        server.start()
        val file = File.createTempFile("cancel-transfer", ".part")
        try {
            val transfer = launch(Dispatchers.IO) { HttpDownloadTransfer(freeBytes = { Long.MAX_VALUE }).fetch(
                StreamResolution("http://127.0.0.1:${server.address.port}/slow"), file) { _, _ -> } }
            withTimeout(5_000) { sending.await() }
            withTimeout(2_000) { transfer.cancelAndJoin() }
            assertTrue(transfer.isCancelled)
        } finally { release.countDown(); server.stop(0); executor.shutdownNow(); file.delete() }
    }

    @Test fun insufficientSpaceAndHtmlResponsesNeverBecomeAnAudioTransfer() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/audio") { exchange ->
            exchange.responseHeaders.add("Content-Type", "audio/webm"); exchange.sendResponseHeaders(200, 3)
            exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3)) }
        }
        server.createContext("/html") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/html"); exchange.sendResponseHeaders(200, 3)
            exchange.responseBody.use { it.write("bad".toByteArray()) }
        }
        server.start()
        val file = File.createTempFile("rejected-transfer", ".part")
        try {
            for ((path, space) in listOf("audio" to 0L, "html" to Long.MAX_VALUE)) {
                try {
                    HttpDownloadTransfer(freeBytes = { space }).fetch(StreamResolution("http://127.0.0.1:${server.address.port}/$path"), file) { _, _ -> }
                    fail("Expected rejection")
                } catch (error: DownloadTransferFailure) { assertFalse(error.retryable) }
                assertEquals(0L, file.length())
            }
        } finally { server.stop(0); file.delete() }
    }
}
