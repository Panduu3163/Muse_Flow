package com.example.ui.screens

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
) {
    val viewModel: BackupViewModel = viewModel()
    val autoBackup by viewModel.autoBackupEnabled.collectAsState()
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val outcome by viewModel.outcome.collectAsState()

    var confirmingRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val importViewModel: PlaylistImportViewModel = viewModel()
    val importState by importViewModel.state.collectAsState()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        // The file name becomes the playlist name, so an "Exportify - My Mix.csv" arrives as a
        // recognisable playlist rather than "Imported playlist".
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
    ) { uri -> uri?.let { confirmingRestore = it } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("backup_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("backup_back")) {
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
                NavigationPreference(
                    title = "Export backup",
                    subtitle = "Saves liked songs and playlists to a file you choose.",
                    icon = Icons.Default.Backup,
                    enabled = !busy,
                    onClick = { exportLauncher.launch(defaultBackupName()) },
                )
                NavigationPreference(
                    title = "Restore from backup",
                    subtitle = "Adds the contents of a backup file to this library.",
                    icon = Icons.Default.Restore,
                    enabled = !busy,
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                )
            }

            PreferenceGroup(title = "Import") {
                NavigationPreference(
                    title = "Import playlist from a file",
                    subtitle = "A CSV exported from Spotify or another service. Tracks are " +
                        "matched on YouTube Music.",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = importState !is ImportState.Matching,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
            }

            PreferenceGroup(title = "Automatic") {
                SwitchPreference(
                    title = "Auto backup",
                    subtitle = "Keeps a daily copy inside the app. Export it too - uninstalling " +
                        "deletes this one.",
                    icon = Icons.Default.Schedule,
                    checked = autoBackup,
                    onCheckedChange = viewModel::setAutoBackupEnabled,
                )
                Text(
                    text = lastBackupAt?.let { "Last automatic backup: ${formatTimestamp(it)}" }
                        ?: "No automatic backup has run yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }

            // What a backup deliberately leaves out, said up front rather than discovered after a
            // restore that didn't bring downloads back.
            Text(
                text = "Backups cover liked songs and playlists. Downloads aren't included - their " +
                    "files wouldn't be valid on another device - and neither is listening history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    confirmingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmingRestore = null },
            title = { Text("Restore this backup?") },
            // Says what restore actually does, because "restore" reads as "replace" to most
            // people and this one merges.
            text = {
                Text(
                    "Liked songs are merged in. Playlists you already have with the same songs " +
                        "are left alone; anything else is added. Nothing you have now is deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreFrom(uri)
                        confirmingRestore = null
                    },
                    modifier = Modifier.testTag("backup_restore_confirm"),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRestore = null }) { Text("Cancel") }
            },
        )
    }

    when (val import = importState) {
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
            title = { Text("Imported \"${import.playlistName}\"") },
            text = {
                Column {
                    Text("${import.imported} of ${import.imported + import.unmatched.size} tracks matched.")
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
                        is BackupOutcome.Restored -> "Restore complete"
                        is BackupOutcome.Failed -> "That didn't work"
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
                            // Otherwise restoring the same backup twice looks like it did nothing.
                            if (result.skipped > 0) {
                                append(" ${result.skipped} ")
                                append(if (result.skipped == 1) "playlist was" else "playlists were")
                                append(" already here and left alone.")
                            }
                        }
                        is BackupOutcome.Failed -> result.message
                    }
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
