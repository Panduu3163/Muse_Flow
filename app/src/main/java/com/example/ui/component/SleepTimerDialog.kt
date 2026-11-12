package com.example.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import com.example.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val timerPresets = (1..8).map { it * 15 }

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

/** Shared by the Now Playing sleep button and the full player's overflow menu. */
@Composable
fun SleepTimerDialog(
    remainingMs: Long?,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(1) } // 30 minutes
    var sliderPosition by remember { mutableFloatStateOf(1f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    var customMode by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableIntStateOf(30) }
    val customValue = (customHours * 60 + customMinutes).takeIf { it in 1..720 }
    val selectedMinutes = if (customMode) customValue else timerPresets[sliderPosition.roundToInt().coerceIn(0, timerPresets.lastIndex)]
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
        title = { Text("Sleep timer") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (remainingMs != null) {
                    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
                    val accent = MaterialTheme.colorScheme.primary
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(
                                accent.copy(alpha = 0.34f), dark.copy(alpha = 0.92f),
                            )))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)) {
                                    Icon(Icons.Default.Bedtime, contentDescription = null,
                                        modifier = Modifier.padding(10.dp).size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface)
                                }
                                Column {
                                    Text("TIMER RUNNING", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold)
                                    Text(sleepTimerRemainingLabel(remainingMs),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("until playback pauses",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text("Playback pauses when the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!customMode) {
                    Text(durationLabel(timerPresets[sliderPosition.roundToInt().coerceIn(0, timerPresets.lastIndex)]),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = sliderPosition,
                        onValueChange = { settleJob?.cancel(); sliderPosition = it },
                        onValueChangeFinished = {
                            selectedIndex = sliderPosition.roundToInt().coerceIn(0, timerPresets.lastIndex)
                            if (reducedMotion) sliderPosition = selectedIndex.toFloat()
                            else settleJob = scope.launch {
                                Animatable(sliderPosition).animateTo(selectedIndex.toFloat(), spring()) {
                                    sliderPosition = value
                                }
                            }
                        },
                        valueRange = 0f..timerPresets.lastIndex.toFloat(),
                        steps = 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("15 min", style = MaterialTheme.typography.labelSmall)
                        Text("2 hr", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(if (customValue != null) durationLabel(customValue) else "Choose 1 min to 12 hr",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        DurationWheel("Hours", 0..12, customHours) { customHours = it }
                        Text(":", style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 8.dp))
                        DurationWheel("Minutes", 0..59, customMinutes) { customMinutes = it }
                    }
                    if (customValue == null) Text("Choose a duration from 1 minute to 12 hours.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { customMode = !customMode }) {
                    Text(if (customMode) "Use 15-minute slider" else "Set a custom duration")
                }
            }
        },
        confirmButton = {
            Button(enabled = selectedMinutes != null, onClick = { selectedMinutes?.let(onStart) }) {
                Text(if (remainingMs == null) "Start timer" else "Replace timer")
            }
        },
        dismissButton = {
            if (remainingMs != null) TextButton(onClick = onCancel) { Text("Stop timer") }
            else TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** Two snapping duration wheels, deliberately without time-of-day or AM/PM semantics. */
@Composable
private fun DurationWheel(
    label: String,
    range: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    val values = remember(range) { range.toList() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(range))
    val scope = rememberCoroutineScope()
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val latestSelection by androidx.compose.runtime.rememberUpdatedState(onSelected)
    androidx.compose.runtime.LaunchedEffect(state, values) {
        androidx.compose.runtime.snapshotFlow { state.firstVisibleItemIndex }.collect { index ->
            values.getOrNull(index)?.let(latestSelection)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            modifier = Modifier.width(98.dp).height(156.dp).clip(RoundedCornerShape(18.dp)),
            state = state,
            flingBehavior = fling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 52.dp),
        ) {
            items(values) { value ->
                val active = value == selected
                Box(Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent)
                    .clickable { scope.launch { state.animateScrollToItem(value - range.first) } },
                    contentAlignment = Alignment.Center) {
                    Text(value.toString().padStart(2, '0'),
                        style = if (active) MaterialTheme.typography.headlineMedium
                            else MaterialTheme.typography.titleMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
                }
            }
        }
    }
}

fun sleepTimerRemainingLabel(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val rest = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, rest)
        else "%02d:%02d".format(minutes, rest)
}
