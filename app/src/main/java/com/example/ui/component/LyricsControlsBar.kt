package com.example.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.LyricsProviderId
import com.example.LyricsResult

/** A quiet source/status label. All lyric actions live in Now Playing's overflow menu. */
@Composable
fun LyricsControlsBar(result: LyricsResult?, source: String?, modifier: Modifier = Modifier) {
    val type = when (result) {
        null -> "Finding lyrics…"
        is LyricsResult.Synced -> if (result.lines.any { !it.words.isNullOrEmpty() }) "Word synced" else "Line synced"
        is LyricsResult.PlainOnly -> "Unsynced lyrics"
        is LyricsResult.Instrumental -> "Instrumental"
        is LyricsResult.NotFound -> "No lyrics found"
        is LyricsResult.Error -> "Lyrics unavailable"
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)),
    ) {
        Text(
            listOfNotNull(source, type).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun LyricsSourceDialog(
    selected: LyricsProviderId?,
    onSelect: (LyricsProviderId?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics source") },
        text = {
            Column {
                (listOf<LyricsProviderId?>(null) + LyricsProviderId.entries).forEach { option ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == option, onClick = { onSelect(option); onDismiss() })
                        TextButton(onClick = { onSelect(option); onDismiss() }, modifier = Modifier.weight(1f)) {
                            Text(option?.label ?: "Auto · preferred order")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun LyricsTimingDialog(offsetMs: Int, onOffsetChange: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust lyric timing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Move the words until they match the music. This adjustment resets for the next song.")
                Text("Current adjustment: %+.2fs".format(offsetMs / 1000f),
                    style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOffsetChange((offsetMs - 250).coerceAtLeast(-5000)) }) { Text("Later 0.25s") }
                    TextButton(onClick = { onOffsetChange((offsetMs + 250).coerceAtMost(5000)) }) { Text("Earlier 0.25s") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { onOffsetChange(0) }) { Text("Reset") } },
    )
}
