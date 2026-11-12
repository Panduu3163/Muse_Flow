package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BundledReleaseNotes
import com.example.BuildConfig
import com.example.ReleaseNoteBlock
import com.example.ReleaseNoteKind
import com.example.parseReleaseNotes

/** Shared offline reader. Content scrolls independently of its always-reachable close action. */
@Composable
internal fun ReleaseNotesDialog(
    onClose: () -> Unit,
    title: String = "What's new",
    actionLabel: String = "Continue",
    startExpanded: Boolean = false,
    publishedNotes: String? = null,
) {
    val context = LocalContext.current
    val bundled = remember { BundledReleaseNotes.load(context) }
    var full by remember { mutableStateOf(startExpanded) }
    // A late network result must not switch the text while somebody is reading the bundled notes.
    var usePublished by remember { mutableStateOf(startExpanded && !publishedNotes.isNullOrBlank()) }
    val body = if (usePublished) publishedNotes ?: bundled else bundled
    val blocks = remember(full, body) {
        if (full) parseReleaseNotes(body) else CURRENT_CHANGELOG.map { ReleaseNoteBlock(ReleaseNoteKind.Bullet, it) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("changelog_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.History, null, Modifier.size(28.dp), MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { full = !full }, modifier = Modifier.align(Alignment.End).testTag("changelog_full_toggle")) {
                        Text(if (full) "Show summary" else "Read full changelog")
                    }
                    if (full && !publishedNotes.isNullOrBlank()) {
                        Text(if (usePublished) "GitHub release notes" else "Included with this build",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { usePublished = !usePublished }, modifier = Modifier.testTag("changelog_source_toggle")) {
                            Text(if (usePublished) "Show included notes" else "Show GitHub release notes")
                        }
                    }
                    HorizontalDivider()
                    key(full, usePublished) {
                        val listState = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("release_notes_list"),
                            state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(blocks) { block -> ReleaseNoteItem(block) }
                        }
                    }
                    Button(onClick = onClose,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("changelog_continue")) {
                        Text(actionLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNoteItem(block: ReleaseNoteBlock) {
    when (block.kind) {
        ReleaseNoteKind.Title, ReleaseNoteKind.Heading -> Text(block.text,
            style = if (block.kind == ReleaseNoteKind.Title) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp))
        ReleaseNoteKind.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
        ReleaseNoteKind.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Text(block.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
