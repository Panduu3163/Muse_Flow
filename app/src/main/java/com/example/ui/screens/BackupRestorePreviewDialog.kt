package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BackupRestorePreview

@Composable
internal fun BackupRestorePreviewDialog(
    preview: BackupRestorePreview,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("backup_restore_preview"),
        onDismissRequest = onCancel,
        title = { Text("Review backup") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    .testTag("backup_restore_preview_body"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(preview.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge)
                Text("Liked songs: ${preview.newLikedSongs} new, ${preview.existingLikedSongs} already saved.")
                Text("Playlists: ${preview.newPlaylists} to add, ${preview.skippedPlaylists} matching playlists to skip.")
                if (preview.includesArtists) {
                    Text("Followed artists: ${preview.newArtists} to add, ${preview.skippedArtists} already followed.")
                } else {
                    Text("Followed artists are not included in this older backup.")
                }
                if (preview.includesHistory) {
                    Text("History tracks: ${preview.newHistoryTracks} new, ${preview.existingHistoryTracks} already present.")
                    Text("Playback events: ${preview.newHistoryEvents} to add, ${preview.skippedHistoryEvents} already present.")
                    Text("Recorded sessions: ${preview.newListeningSessions} to add, ${preview.updatedListeningSessions} to update, ${preview.skippedListeningSessions} to skip.")
                    Text("History totals are merged conservatively rather than added together. Recorded sessions keep the most recent 2,000 entries; older sessions can be dropped.")
                }
                if (preview.includesSettings) {
                    Text("App settings: ${preview.settingsCount} saved values across appearance, player/lyrics/audio, EQ and Library layout.")
                    Text("These settings replace the corresponding settings on this device. Unset included options return to defaults. Device privacy, accounts, permissions, queue and backup choices are kept.")
                }
                Text("Liked-song details may be refreshed. Matching playlists and existing followed artists are left unchanged. " +
                    if (preview.includesHistory || preview.includesSettings) "Your saved library is kept." else "Nothing is deleted.")
                Text("Not included: " + buildList {
                    if (!preview.includesSettings) add("settings")
                    if (!preview.includesHistory) add("listening history")
                    add("downloaded audio"); add("custom cover images")
                }.joinToString(", ") + ".")
                Text("Counts are checked again when you restore.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore, modifier = Modifier.testTag("backup_restore_confirm")) {
                Text(if (preview.includesSettings) "Restore backup" else "Restore library")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("backup_restore_cancel")) { Text("Cancel") }
        },
    )
}
