package com.example

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafBackupDestinationTest {
    private lateinit var context: Context
    private lateinit var provider: BackupTestDocumentsProvider
    private lateinit var destination: SafBackupDestination
    private val tree = Uri.parse("content://com.example.backuptest/tree/root")
    private val json = """{"schemaVersion":1,"likedSongs":[],"playlists":[]}"""

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = BackupTestDocumentsProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "com.example.backuptest"; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission
        })
        ShadowContentResolver.registerProviderInternal("com.example.backuptest", provider)
        destination = SafBackupDestination(context)
    }
    @After fun tearDown() { provider.files.values.forEach { it.delete() } }

    @Test fun writesNewDocumentsAndVerifiesThroughTheProviderWithoutReplacingEarlierFiles() = runBlocking {
        assertEquals("Backups", destination.retain(tree))
        val first = destination.writeVerified(tree, json)
        val second = destination.writeVerified(tree, json)
        assertNotEquals(first, second)
        assertEquals(2, provider.files.size)
        assertTrue(provider.files.values.all { it.readText() == json })
        destination.release(tree)
        try { destination.writeVerified(tree, json); fail("Grant must be required") } catch (_: IllegalStateException) { }
        assertEquals(2, provider.files.size)
    }

    @Test fun unreadableOrTruncatedReadBackIsFailureAndOnlyNewDocumentIsRemoved() = runBlocking {
        destination.retain(tree)
        val first = destination.writeVerified(tree, json)
        provider.corruptReads = true
        try { destination.writeVerified(tree, json); fail("Corrupt copy must fail") } catch (_: IllegalStateException) { }
        assertEquals(setOf(DocumentsContract.getDocumentId(first)), provider.files.keys)
        assertEquals(json, provider.files.values.single().readText())
    }

    @Test fun folderWithoutCreateSupportIsRejectedBeforeAnyFileIsCreated() = runBlocking {
        provider.writable = false
        try { destination.retain(tree); fail("Read-only folder must fail") } catch (_: IllegalStateException) { }
        assertTrue(provider.files.isEmpty())
    }

    @Test @Config(sdk = [24]) fun oldestSupportedAndroidCanWriteAndVerifyACopy() = runBlocking {
        destination.retain(tree)
        val saved = destination.writeVerified(tree, json)
        assertEquals(json, provider.files.getValue(DocumentsContract.getDocumentId(saved)).readText())
    }
}

class BackupTestDocumentsProvider : DocumentsProvider() {
    val files = linkedMapOf<String, File>()
    var corruptReads = false
    var writable = true
    private var sequence = 0
    override fun onCreate() = true
    override fun isChildDocument(parentDocumentId: String, documentId: String) = parentDocumentId == "root" && files.containsKey(documentId)
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id"))
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        return MatrixCursor(columns).apply { addRow(columns.map { column -> when (column) {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> "Backups"
            DocumentsContract.Document.COLUMN_MIME_TYPE -> if (documentId == "root") DocumentsContract.Document.MIME_TYPE_DIR else "application/json"
            DocumentsContract.Document.COLUMN_FLAGS -> if (writable) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0
            else -> null
        } }) }
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        check(writable)
        val id = "copy-${++sequence}"
        files[id] = File.createTempFile("backup-document", ".json", context!!.cacheDir)
        return id
    }
    override fun deleteDocument(documentId: String) { files.remove(documentId)?.delete() }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = files.getValue(documentId)
        if (mode == "r" && corruptReads) {
            // A different provider view simulates failed durability without altering an older copy.
            val corrupt = File(context!!.cacheDir, "corrupt-backup-view.json").apply { writeText("{}") }
            return ParcelFileDescriptor.open(corrupt, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }
}
