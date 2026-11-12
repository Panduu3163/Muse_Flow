package com.example.ui.screens

import com.example.ui.utils.MicroIconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BackupOutcome
import com.example.BackupViewModel
import com.example.ImportState
import com.example.PlaylistImportViewModel
import com.example.ui.component.NavigationPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SwitchPreference
import java.text.DateFormat
import java.util.Date

/**
 * Export the library to a file the user chooses, and restore it back.
 *
 * Both sides go through the Storage Access Framework, so the backup lands somewhere the user can
 * actually see it in their Files app and hand to another device - the point of a backup is that it
 * outlives this app's private storage, which an uninstall takes with it.
 */
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backupViewModel: BackupViewModel = viewModel(),
) {
    val viewModel = backupViewModel
    val autoBackup by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val includeHistory by viewModel.includeHistory.collectAsStateWithLifecycle()
    val includeSettings by viewModel.includeSettings.collectAsStateWithLifecycle()
    val pendingSettings by viewModel.pendingSettings.collectAsStateWithLifecycle()
    val destination by viewModel.automaticDestination.collectAsStateWithLifecycle()
    val hasInternalBackup by viewModel.hasInternalBackup.collectAsStateWithLifecycle()
    val hasPreviousInternalBackup by viewModel.hasPreviousInternalBackup.collectAsStateWithLifecycle()
    var confirmKeepSettings by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()

    val importViewModel: PlaylistImportViewModel = viewModel()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val operationsAvailable = !busy && !pendingSettings && restorePreview == null && !importState.isWorking
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        // The file name becomes the playlist name, so an "Exportify - My Mix.csv" arrives as a
        // recognisable playlist rather than "Imported playlist".
        uri?.let { importViewModel.importFrom(it, displayNameOf(context, it)) }
    }

    // A separate launcher rather than reusing importLauncher: the two need different MIME
    // filters, even though both end up calling the same importFrom, which tells CSV and M3U
    // apart by file name.
    val importM3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importViewModel.importFrom(it, displayNameOf(context, it)) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        // A dated default name, because the second backup is the one where an overwritten first
        // one hurts.
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }

    val restoreLauncher = rememberLauncherForActivityResult(
        // Not filtered to application/json alone: plenty of file providers hand JSON back as
        // octet-stream or text/plain, and a picker that greys out the user's own backup is worse
        // than one that lets them pick the wrong file and get told so.
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::previewRestoreFrom) }
    val destinationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setAutomaticDestination)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("backup_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroIconButton(onClick = onBack, modifier = Modifier.testTag("backup_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Backup & restore",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        // Matches Settings' own 16dp horizontal inset and group spacing, so arriving here doesn't
        // look like a different app.
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Manual") {
                SwitchPreference(
                    title = "Include app settings",
                    subtitle = "Includes appearance, player, lyrics, audio, EQ and Library layout. Restoring replaces these settings.",
                    checked = includeSettings, enabled = operationsAvailable,
                    onCheckedChange = viewModel::setIncludeSettingsEnabled,
                )
                SwitchPreference(
                    title = "Include listening history",
                    subtitle = "Adds playback history and recorded listening sessions to manual and automatic backups.",
                    checked = includeHistory,
                    enabled = operationsAvailable,
                    onCheckedChange = viewModel::setIncludeHistoryEnabled,
                )
                NavigationPreference(
                    title = "Export backup",
                    subtitle = "Saves liked songs, playlists and followed artists to a file you choose.",
                    icon = Icons.Default.Backup,
                    enabled = operationsAvailable,
                    onClick = { exportLauncher.launch(defaultBackupName()) },
                )
                NavigationPreference(
                    title = "Restore from backup",
                    subtitle = "Review the contents before adding them to your library.",
                    icon = Icons.Default.Restore,
                    enabled = operationsAvailable,
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                )
            }

            if (pendingSettings) PreferenceGroup(title = "Settings restore pending") {
                Text("Your library is saved. Finish the pending settings restore before making or importing another backup.")
                NavigationPreference(title = "Retry settings restore", enabled = !busy,
                    onClick = viewModel::retryPendingSettings)
                NavigationPreference(title = "Keep current settings", enabled = !busy,
                    onClick = { confirmKeepSettings = true })
            }

            PreferenceGroup(title = "Import") {
                NavigationPreference(
                    title = "Import playlist from a file",
                    subtitle = "A CSV exported from Spotify or another service. Tracks are " +
                        "matched on YouTube Music.",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = operationsAvailable,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
                NavigationPreference(
                    title = "Import M3U playlist",
                    subtitle = "An M3U or M3U8 playlist file. Tracks are matched on YouTube " +
                        "Music.",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = operationsAvailable,
                    onClick = { importM3uLauncher.launch(arrayOf("*/*")) },
                )
            }

            PreferenceGroup(title = "Automatic") {
                SwitchPreference(
                    title = "Auto backup",
                    subtitle = "Runs about once a day when storage is available. Keeps an internal recovery copy and, if selected, a copy in your external folder.",
                    icon = Icons.Default.Schedule,
                    checked = autoBackup,
                    enabled = operationsAvailable,
                    onCheckedChange = viewModel::setAutoBackupEnabled,
                )
                NavigationPreference(title = "Automatic backup folder",
                    subtitle = destination.label ?: "Not selected. Internal copies are deleted by uninstall or clearing app data.",
                    enabled = operationsAvailable,
                    onClick = { destinationLauncher.launch(destination.treeUri?.let(android.net.Uri::parse)) })
                if (destination.treeUri != null) {
                    NavigationPreference(title = "Use internal copies only", subtitle = "Stops writing to this folder. Existing backup files are kept.",
                        enabled = operationsAvailable, onClick = { viewModel.setAutomaticDestination(null) })
                }
                NavigationPreference(title = "Back up now", subtitle = "Save using the current history and settings choices.",
                    enabled = operationsAvailable, onClick = viewModel::backupNow)
                if (hasInternalBackup) NavigationPreference(title = "Restore internal recovery copy",
                    subtitle = "Review its contents before restoring. Also available for copies returned by Android system backup.",
                    enabled = operationsAvailable, onClick = { viewModel.previewInternalBackup() })
                if (hasPreviousInternalBackup) NavigationPreference(title = "Restore previous internal copy",
                    subtitle = "Review the older generation if the latest copy is not the one you need.",
                    enabled = operationsAvailable, onClick = { viewModel.previewInternalBackup(previous = true) })
                Text(
                    text = (lastBackupAt?.let { "Last internal copy: ${formatTimestamp(it)}" } ?: "No internal copy saved yet.") +
                        if (destination.treeUri != null) "\n" + (destination.lastExternalBackupAt?.let { "Last verified external copy: ${formatTimestamp(it)}" }
                            ?: "No verified copy saved to this folder yet.") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
                destination.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
                Text("External backups use a new file each time. Older external copies are kept; remove unwanted files in your Files app. " +
                    "MuseFlow keeps the latest and one previous internal copy. Android system backup can include the latest internal copy, subject to device settings and quota; " +
                    "restore it here after reinstalling. It does not transfer downloaded audio or raw app databases.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }

            // What a backup deliberately leaves out, said up front rather than discovered after a
            // restore that didn't bring downloads back.
            Text(
                text = "Backups include liked songs, playlists, playlist pins and followed artists. " +
                    (if (includeHistory) "Listening history is included. " else "Listening history is optional and currently off. ") +
                    (if (includeSettings) "Portable app settings are included. " else "App settings are optional and currently off. ") +
                    "Downloaded audio, custom cover images, accounts, permissions and device privacy controls aren't included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    restorePreview?.let { preview ->
        BackupRestorePreviewDialog(preview, viewModel::confirmRestore, viewModel::cancelRestorePreview)
    }

    if (confirmKeepSettings) AlertDialog(
        onDismissRequest = { confirmKeepSettings = false }, title = { Text("Keep current settings?") },
        text = { Text("Library data is kept. Any settings already restored will stay; the remaining saved settings will not be applied.") },
        confirmButton = { TextButton(onClick = { confirmKeepSettings = false; viewModel.keepCurrentSettings() }) { Text("Keep current settings") } },
        dismissButton = { TextButton(onClick = { confirmKeepSettings = false }) { Text("Cancel") } },
    )

    when (val import = importState) {
        ImportState.Reading -> AlertDialog(
            onDismissRequest = importViewModel::cancel,
            title = { Text("Reading playlist") },
            text = { Text("Reading and checking this file. No playlist has been created.") },
            confirmButton = { TextButton(onClick = importViewModel::cancel) { Text("Cancel") } },
        )
        ImportState.Saving -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Saving playlist") },
            text = { Text("Saving the matched songs together. This final step finishes even if you leave this screen.") },
            confirmButton = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
        )
        is ImportState.Matching -> AlertDialog(
            // Not dismissible by tapping away: the work continues either way, and a dialog that
            // vanishes mid-import looks like it stopped.
            onDismissRequest = { },
            title = { Text("Importing playlist") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { (import.done + 1f) / import.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Matching ${import.done + 1} of ${import.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = import.current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = importViewModel::cancel) { Text("Cancel") }
            },
        )

        is ImportState.Done -> AlertDialog(
            onDismissRequest = importViewModel::dismiss,
            title = { Text(if (import.playlistCreated) "Playlist imported" else "No matching tracks") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(import.playlistName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${import.imported} unique tracks imported; ${import.unmatched.size} unmatched.")
                    if (import.duplicates > 0) Text("Skipped ${import.duplicates} duplicate matches.")
                    if (import.invalidRows > 0) Text("Skipped ${import.invalidRows} CSV rows with a missing title or artist. Correct those rows and import them separately.")
                    if (!import.playlistCreated) Text("No playlist was created.")
                    if (import.unmatched.isNotEmpty()) {
                        Text(
                            text = "Couldn't find these - add them by hand if you want them:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        // Capped and scrollable: a 200-track import that matched nothing would
                        // otherwise produce a dialog taller than the screen.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            import.unmatched.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = importViewModel::dismiss) { Text("Done") }
            },
        )

        is ImportState.Failed -> AlertDialog(
            onDismissRequest = importViewModel::dismiss,
            title = { Text("Couldn't import that file") },
            text = { Text(import.message) },
            confirmButton = {
                TextButton(onClick = importViewModel::dismiss) { Text("OK") }
            },
        )

        ImportState.Idle -> Unit
    }

    outcome?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOutcome,
            title = {
                Text(
                    when (result) {
                        is BackupOutcome.Exported -> "Backup saved"
                        is BackupOutcome.Restored -> if (result.settingsPending) "Library restored; settings pending" else "Restore complete"
                        is BackupOutcome.Failed -> "That didn't work"
                        is BackupOutcome.Notice -> result.title
                    }
                )
            },
            text = {
                Text(
                    when (result) {
                        is BackupOutcome.Exported -> "Written to ${result.location}."
                        is BackupOutcome.Restored -> buildString {
                            append("${result.likedSongs} liked ")
                            append(if (result.likedSongs == 1) "song" else "songs")
                            append(" and ${result.playlists} ")
                            append(if (result.playlists == 1) "playlist" else "playlists")
                            append(" restored.")
                            if (result.includesArtists) {
                                append(" Followed artists added: ${result.artists}; already followed: ${result.artistsSkipped}.")
                            }
                            if (result.includesHistory) {
                                append(" History tracks merged: ${result.historyTracks}; new playback events: ${result.historyEvents}; " +
                                    "recorded sessions added or updated: ${result.listeningSessions}. Session history keeps the most recent 2,000 entries.")
                            }
                            // Otherwise restoring the same backup twice looks like it did nothing.
                            if (result.skipped > 0) {
                                append(" ${result.skipped} ")
                                append(if (result.skipped == 1) "playlist was" else "playlists were")
                                append(" already here and left alone.")
                            }
                            if (result.includesSettings) append(if (result.settingsPending)
                                " Your library is saved, but settings still need recovery. Use Retry settings restore."
                                else " Included app settings were restored.")
                        }
                        is BackupOutcome.Failed -> result.message
                        is BackupOutcome.Notice -> result.message
                    },
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissOutcome,
                    modifier = Modifier.testTag("backup_outcome_ok"),
                ) { Text("OK") }
            },
        )
    }
}

/** The picked file's user-visible name, for naming the imported playlist after it. */
private fun displayNameOf(context: android.content.Context, uri: android.net.Uri): String =
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: "Imported playlist"

/** `museflow-backup-2026-07-25.json` - sorts chronologically and says what it is. */
private fun defaultBackupName(): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())
    return "museflow-backup-$date.json"
}

private fun formatTimestamp(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
