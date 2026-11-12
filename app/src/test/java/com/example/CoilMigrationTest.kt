package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil3.imageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class CoilMigrationTest {
    @Test fun applicationLoaderFetchesArtworkAndHonorsCacheHeaders() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val png = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }.toByteArray()
        val requests = AtomicInteger()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true, name = "artwork-cache-test") {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume headers. */ }
                    requests.incrementAndGet()
                    val cache = if (path.contains("private.png")) "no-store" else "public, max-age=3600"
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${png.size}\r\nCache-Control: $cache\r\nConnection: close\r\n\r\n".toByteArray())
                        write(png); flush()
                    }
                }
            } catch (_: java.net.SocketException) { /* Closing the test server ends accept(). */ }
        }
        try {
            val loader = context.imageLoader
            assertSame(loader, context.imageLoader)
            assertEquals(250L * 1024 * 1024, loader.diskCache!!.maxSize)
            suspend fun load(path: String) {
                val result = loader.execute(ImageRequest.Builder(context)
                    .data("http://127.0.0.1:${server.localPort}/$path")
                    // Robolectric's native ImageDecoder is unsupported on Windows. Keep this
                    // request on BitmapFactory; production retains Coil's normal decoder choice.
                    .decoderFactory(BitmapFactoryDecoder.Factory())
                    .memoryCachePolicy(CachePolicy.DISABLED).allowHardware(false).size(16).build())
                assertTrue("Artwork must decode through the registered network fetcher: $result", result is SuccessResult)
                assertEquals(Color.RED, (result as SuccessResult).image.toBitmap(16, 16).getPixel(8, 8))
            }
            load("cover.png"); load("cover.png")
            assertEquals("Fresh artwork should reuse disk cache", 1, requests.get())
            load("private.png"); load("private.png")
            assertEquals("no-store artwork must not be kept in disk cache", 3, requests.get())
        } finally { server.close(); worker.join(5_000) }
    }
}
