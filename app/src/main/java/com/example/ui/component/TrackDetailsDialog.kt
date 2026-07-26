package com.example.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.TrackResult

/**
 * Read-only info for a track - everything already available on [TrackResult] plus the two bits of
 * local state ([isLiked]/[isDownloaded]) the sheet already tracks, laid out as label/value rows.
 *
 * Deliberately has no actions of its own: it's a detail view, not another actions sheet - closing
 * it returns to wherever the "Details" row was tapped from.
 */
@Composable
fun TrackDetailsDialog(
    track: TrackResult,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(modifier = Modifier.testTag("track_details_dialog")) {
                DetailRow("Title", track.title)
                DetailRow("Artist", track.artist)
                track.duration?.let { DetailRow("Duration", it) }
                DetailRow("Source", track.source)
                DetailRow("Liked", if (isLiked) "Yes" else "No")
                DetailRow("Downloaded", if (isDownloaded) "Yes" else "No")
                DetailRow("Track ID", track.id)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
