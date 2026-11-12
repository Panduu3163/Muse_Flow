package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal interface ExternalBackupDestination {
    suspend fun retain(tree: Uri): String
    suspend fun release(tree: Uri)
    suspend fun writeVerified(tree: Uri, json: String): Uri
}

/** Each attempt creates a new document. A failed provider write never overwrites an older copy. */
internal class SafBackupDestination(context: Context) : ExternalBackupDestination {
    private val resolver = context.applicationContext.contentResolver
    private val access = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    override suspend fun retain(tree: Uri): String = withContext(Dispatchers.IO) {
        require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree)) { "Choose a backup folder" }
        resolver.takePersistableUriPermission(tree, access)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS)
        val cursor = if (android.os.Build.VERSION.SDK_INT >= 26) resolver.query(root, columns, android.os.Bundle.EMPTY, null)
            else resolver.query(root, columns, null, null, null)
        cursor?.use { row ->
            check(row.moveToFirst() && row.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR &&
                row.getInt(2) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "This folder does not allow backups" }
            row.getString(0)?.take(256) ?: "Selected folder"
        } ?: error("The selected folder is unavailable")
    }

    override suspend fun release(tree: Uri) = withContext(Dispatchers.IO) {
        resolver.releasePersistableUriPermission(tree, access)
    }

    override suspend fun writeVerified(tree: Uri, json: String): Uri = withContext(Dispatchers.IO) {
        check(resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }) {
            "Select the backup folder again to restore access"
        }
        parseBackupDocument(json)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val document = DocumentsContract.createDocument(resolver, root, "application/json",
            "museflow-backup-$stamp-${UUID.randomUUID()}.json") ?: error("Could not create a backup file")
        try {
            coroutineContext.ensureActive()
            resolver.openOutputStream(document, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: error("Could not write the backup file")
            coroutineContext.ensureActive()
            val readBack = resolver.openInputStream(document)?.use { readBackupDocument(it) }
                ?: error("Could not verify the backup file")
            check(readBack == json) { "The saved backup could not be verified" }
            parseBackupDocument(readBack)
            coroutineContext.ensureActive()
            document
        } catch (error: Exception) {
            // Only this newly-created document is eligible for cleanup, never existing folder contents.
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw error
        }
    }
}

data class AutomaticBackupDestinationState(
    val treeUri: String? = null,
    val label: String? = null,
    val lastExternalBackupAt: Long? = null,
    val error: String? = null,
)
