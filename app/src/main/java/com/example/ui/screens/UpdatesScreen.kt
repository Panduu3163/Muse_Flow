package com.example.ui.screens

import com.example.ui.utils.MicroIconButton
import androidx.core.net.toUri

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AvailableUpdate
import com.example.BuildConfig
import com.example.InAppUpdater
import com.example.InstalledReleaseNotes
import com.example.UpdateChecker
import com.example.UpdateInstallOutcome
import kotlinx.coroutines.launch

/** Where [checkState] currently stands - a plain local UI enum rather than reusing
 * [UpdateChecker.UpdateCheckResult] directly, since this screen also needs an [Idle]/[Checking]
 * state that result type has no reason to know about. */
private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class UpToDate(val installedVersion: String) : CheckState
    data class UpdateAvailable(val update: AvailableUpdate) : CheckState
    data object Error : CheckState
}

/**
 * Settings' "Check for updates" screen - a plain, on-demand counterpart to the launch-time
 * checker ([UpdateChecker.checkForUpdate]) and its popup: this one only runs when the user taps
 * the button here, never fires the background notification, and never pops the launch-time
 * dialog on top of itself (see [UpdateChecker.checkNow]'s own doc on why the two stay separate).
 *
 * The update check remains on demand. Read changelog shows the installed version's cached
 * GitHub Release body with the complete bundled notes always available in the same offline reader.
 */
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    checkForUpdate: suspend (android.content.Context) -> UpdateChecker.UpdateCheckResult = UpdateChecker::checkNow,
    downloadUpdate: suspend (android.content.Context, AvailableUpdate) -> UpdateInstallOutcome = InAppUpdater::downloadAndInstall,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadResult by remember { mutableStateOf<String?>(null) }
    var downloadGeneration by remember { mutableLongStateOf(0) }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var changelogExpanded by remember { mutableStateOf(false) }
    val fetchedNotes by InstalledReleaseNotes.body.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { InstalledReleaseNotes.prefetch(context) }

    if (changelogExpanded) ReleaseNotesDialog(
        onClose = { changelogExpanded = false }, title = "Changelog", actionLabel = "Done",
        startExpanded = true, publishedNotes = fetchedNotes ?: InstalledReleaseNotes.cached(context),
    )

    fun runCheck() {
        if (isDownloading) return
        checkState = CheckState.Checking
        downloadResult = null
        scope.launch {
            checkState = when (val result = checkForUpdate(context)) {
                is UpdateChecker.UpdateCheckResult.UpToDate -> CheckState.UpToDate(result.installedVersion)
                is UpdateChecker.UpdateCheckResult.UpdateAvailable -> CheckState.UpdateAvailable(result.update)
                is UpdateChecker.UpdateCheckResult.Error -> CheckState.Error
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("updates_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroIconButton(onClick = onBack, modifier = Modifier.testTag("updates_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Updates",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "You're on version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // "Check for updates" - a live GitHub lookup, run only on tap.
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("updates_check_card"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Check for updates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    when (val state = checkState) {
                        is CheckState.Idle -> Unit
                        is CheckState.Checking -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Checking for updates...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is CheckState.UpToDate -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "No newer release was found for your installed version.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("updates_up_to_date"),
                            )
                        }
                        is CheckState.UpdateAvailable -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Text(
                                    text = state.update.changelog.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            Button(
                                onClick = {
                                    val update = state.update
                                    if (update.apkDownloadUrl != null) {
                                        isDownloading = true
                                        downloadResult = null
                                        val request = ++downloadGeneration
                                        downloadJob = scope.launch {
                                            try {
                                                val result = downloadUpdate(context, update)
                                                if (request == downloadGeneration) downloadResult = result.userMessage
                                            } finally { if (request == downloadGeneration) isDownloading = false }
                                        }
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, (update.releaseUrl).toUri())
                                            )
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("updates_download_now"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(
                                        text = if (state.update.apkDownloadUrl != null) "Download now" else "Open on GitHub",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            downloadResult?.let { result ->
                                Text(result, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.testTag("updates_download_result"))
                            }
                            if (isDownloading) TextButton(onClick = {
                                downloadGeneration++
                                downloadJob?.cancel()
                                isDownloading = false
                                downloadResult = "Update download cancelled."
                            }, modifier = Modifier.testTag("updates_cancel_download")) { Text("Cancel download") }
                        }
                        is CheckState.Error -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Couldn't check for updates. Check your connection and try again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (checkState !is CheckState.Checking) {
                        TextButton(
                            onClick = ::runCheck,
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth().testTag("updates_check_button"),
                        ) {
                            Text(if (checkState is CheckState.Idle) "Check for updates" else "Check again")
                        }
                    }
                }
            }

            // Installed version's release body, cached after its first successful fetch.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { changelogExpanded = !changelogExpanded }
                    .testTag("updates_changelog_card"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Read changelog",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (changelogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                }
            }

            // Matches the 200dp bottom clearance every scrollable list in this app reserves for
            // the mini-player - this screen's own expanding "Read changelog" section could grow
            // long enough to land its last line right behind it otherwise, with no gap.
            Spacer(Modifier.height(200.dp))
        }
    }
}
