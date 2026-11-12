package com.example.ui.screens

import androidx.compose.runtime.Composable

/** Once-per-update highlights with complete offline notes in the same reader. */
@Composable
fun ChangelogDialog(onContinue: () -> Unit) {
    ReleaseNotesDialog(onClose = onContinue)
}

internal val CURRENT_CHANGELOG = listOf(
    "Development checkpoint: backup, downloads, outside playback controls and utility improvements are included; the full 1.8 release is still in progress.",
    "Preview library restores, optionally include history and settings, and recover interrupted settings without importing the library twice.",
    "Choose an external automatic-backup folder, verify saved copies, and review the latest or previous internal recovery copy.",
    "Manage durable download requests with retry, cancel, missing-file status and local recovery of completed file saving.",
    "Downloads wait for an explicit retry after restart. Temporary failures retry once from the beginning and may use mobile data.",
    "Storage cleanup waits for active transfers and keeps likes, playlists and backup copies.",
    "Restore a paused queue with saved shuffle/repeat, use the compact home-screen widget, or pause/open the player from Quick Settings.",
    "Long settings choices, track details and timer content scroll at large text sizes. Update failures are visible; installer handoff is not installation success.",
    "Phone and final release acceptance remain pending. Keep an exported backup before testing this development APK.",
)
