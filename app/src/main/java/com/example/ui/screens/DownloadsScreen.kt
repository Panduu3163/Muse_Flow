package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.*
import com.example.ui.utils.MicroIconButton

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val model: DownloadsViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val active by model.active.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    DownloadsContent(state, active, busy, message, onBack, model::retry, model::cancel, model::remove, model::dismissMessage)
}

@Composable
internal fun DownloadsContent(
    state: DownloadsUiState, active: Map<String, Int>, busy: Set<String>, message: String?,
    onBack: () -> Unit, onRetry: (DownloadRequestEntity) -> Unit, onCancel: (DownloadRequestEntity) -> Unit,
    onRemove: (DownloadRequestEntity) -> Unit, onDismissMessage: () -> Unit,
) {
    var confirming by remember { mutableStateOf<DownloadRequestEntity?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("downloads_screen")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Downloads", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Interrupted downloads wait for you to retry. Each transfer may retry once; retries start from the beginning. Downloading can use mobile data.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
            if (state.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.loading && state.error == null && state.requests.isEmpty()) item {
                Text("No download requests yet. Download a song from its track menu to keep it available offline.", modifier = Modifier.padding(vertical = 24.dp))
            }
            items(state.requests, key = { it.key }) { request ->
                val working = request.key in active
                val cancelling = active[request.key] == -2
                val label = if (cancelling) "Cancelling download" else when (request.requestState) {
                    DownloadRequestState.QUEUED -> "Queued"
                    DownloadRequestState.RESOLVING -> "Finding audio"
                    DownloadRequestState.DOWNLOADING -> "Downloading"
                    DownloadRequestState.VERIFYING -> if (working) "Verifying saved audio" else "Saved audio needs recovery"
                    DownloadRequestState.COMPLETED -> "Available offline"
                    DownloadRequestState.CANCELLED -> "Cancelled"
                    DownloadRequestState.INTERRUPTED -> "Interrupted"
                    DownloadRequestState.MISSING -> "Audio missing"
                    DownloadRequestState.FAILED -> "Failed"
                }
                ElevatedCard(Modifier.fillMaxWidth().testTag("download_${request.key}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(request.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(request.artist, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        if (working && request.requestState == DownloadRequestState.DOWNLOADING) {
                            val percent = active[request.key] ?: -1
                            if (percent >= 0) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (request.downloadedBytes > 0) Text("${downloadSize(request.downloadedBytes)}" +
                            (request.totalBytes?.let { " / ${downloadSize(it)}" } ?: " transferred"), style = MaterialTheme.typography.bodySmall)
                        if (!working) request.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (request.key in busy) CircularProgressIndicator(Modifier.size(24.dp))
                        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (working) TextButton(onClick = { onCancel(request) }, enabled = !cancelling) { Text("Cancel download") }
                            else if (request.requestState != DownloadRequestState.COMPLETED) TextButton(onClick = { onRetry(request) }) {
                                Text(if (request.requestState == DownloadRequestState.VERIFYING) "Finish saving" else "Retry download")
                            }
                            TextButton(onClick = { confirming = request }) { Text("Remove download") }
                        }
                    }
                }
            }
        }
    }
    confirming?.let { request -> AlertDialog(
        onDismissRequest = { confirming = null }, title = { Text("Remove download?") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            Text("${request.title}\n\nCancels this request and deletes its downloaded audio and artwork from this device. Likes and playlists are kept. Removing audio that is playing may interrupt playback.")
        } },
        confirmButton = { TextButton(onClick = { confirming = null; onRemove(request) }, modifier = Modifier.testTag("download_remove_confirm")) { Text("Remove download") } },
        dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
    ) }
    message?.let { AlertDialog(onDismissRequest = onDismissMessage, title = { Text("Download storage") },
        text = { Text(it, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } }) }
}

private fun downloadSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}
