package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MuseFlowTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Startup-only gate. A live restore must not destroy its own navigation/ViewModel scope. */
@Composable
internal fun SettingsRecoveryGate(repositoryOverride: BackupRepository? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = repositoryOverride ?: remember(context) { BackupRepository.getInstance(context) }
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirmKeep by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pending by repository.pendingSettingsRestore.collectAsStateWithLifecycle(false)
    val working by repository.settingsRestoreWorking.collectAsStateWithLifecycle()
    LaunchedEffect(attempt) {
        failed = false
        ready = repository.recoverPendingSettings()
        failed = !ready
    }
    if (ready) {
        content()
        if (pending) MuseFlowTheme(darkTheme = true) {
            AlertDialog(
                onDismissRequest = {}, title = { Text("Restoring saved settings") },
                text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (working) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Applying the saved settings. Your library is already saved.")
                    } else Text("The saved settings still need to finish. Retry, or keep the settings currently on this device. Your library is kept.")
                } },
                confirmButton = { if (!working) TextButton(onClick = { scope.launch { repository.recoverPendingSettings() } }) { Text("Retry") } },
                dismissButton = { if (!working) TextButton(onClick = { confirmKeep = true }) { Text("Keep current settings") } },
            )
        }
    } else MuseFlowTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                if (!failed) {
                    CircularProgressIndicator()
                    Text("Preparing MuseFlow")
                } else {
                    Text("Settings recovery needs attention", style = MaterialTheme.typography.titleLarge)
                    Text("Saved settings could not be prepared. Free some storage if needed, then retry. Your library has not been cleared.")
                    TextButton(onClick = { attempt++ }) { Text("Retry") }
                    TextButton(onClick = { confirmKeep = true }) { Text("Keep current settings") }
                }
            }
        }
    }
    if (confirmKeep) MuseFlowTheme(darkTheme = true) { AlertDialog(
            onDismissRequest = { confirmKeep = false }, title = { Text("Keep current settings?") },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("Library data is kept. Any settings already restored will stay; the remaining saved settings will not be applied.")
            } },
            confirmButton = { TextButton(onClick = {
                confirmKeep = false
                scope.launch { if (repository.keepCurrentSettings()) ready = true else failed = true }
            }, modifier = Modifier.testTag("settings_recovery_keep_confirm")) { Text("Keep current settings") } },
            dismissButton = { TextButton(onClick = { confirmKeep = false }) { Text("Cancel") } },
        )
    }
}
