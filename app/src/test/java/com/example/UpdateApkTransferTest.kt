package com.example

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateApkTransferTest {
    @get:Rule val files = TemporaryFolder()
    private val installed = UpdatePackageIdentity("app.beta", 52, setOf("original-certificate"))

    @Test fun onlyNewerSamePackageAndSameSignerCanReachInstaller() {
        assertTrue(canInstallUpdate(installed, installed.copy(version = 53)))
        listOf(installed, installed.copy(version = 51), installed.copy(version = 53, packageName = "app.stable"),
            installed.copy(version = 53, signers = setOf("other")), installed.copy(version = 53, signers = emptySet()))
            .forEach { assertFalse(canInstallUpdate(installed, it)) }
        assertFalse(canInstallUpdate(installed.copy(signers = emptySet()), installed.copy(version = 53, signers = emptySet())))
    }

    @Test fun completeTransferIsSyncedAndUnknownLengthIsAllowed() = runTest {
        val payload = ByteArray(130_000) { (it % 251).toByte() }
        for (size in listOf(-1L, payload.size.toLong())) {
            val target = files.newFile()
            copyUpdateApk(ByteArrayInputStream(payload), target, size)
            assertArrayEquals(payload, target.readBytes())
        }
    }

    @Test fun truncatedEmptyOrOverlongTransfersCannotBeHandedOff() = runTest {
        for ((payload, expected) in listOf(byteArrayOf() to -1L, byteArrayOf(1) to 2L, byteArrayOf(1, 2) to 1L,
            byteArrayOf(1) to InAppUpdater.MAX_APK_BYTES + 1)) {
            assertTrue(runCatching { copyUpdateApk(ByteArrayInputStream(payload), files.newFile(), expected) }.isFailure)
        }
    }

    @Test fun cancellationStopsCopyAndClosesInputBeforeAnotherChunk() = runTest {
        var reads = 0; var closed = false
        val transfer = launch(start = CoroutineStart.LAZY) {
            val job = currentCoroutineContext().job
            val source = object : InputStream() {
                override fun read(): Int = error("Bulk read required")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    reads++; buffer[offset] = 7; job.cancel(); return 1
                }
                override fun close() { closed = true }
            }
            copyUpdateApk(source, files.newFile(), -1)
        }
        transfer.start(); transfer.join()
        assertTrue(transfer.isCancelled); assertTrue(closed); assertEquals(1, reads)
    }
}
