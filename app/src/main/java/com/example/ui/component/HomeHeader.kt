package com.example.ui.component

import com.example.ui.utils.MicroIconButton
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Original direct Home actions, without the removed Settings and overflow shortcuts. */
@Composable
internal fun HomeHeader(onHistory: () -> Unit, onTogether: () -> Unit, onStats: () -> Unit) {
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroIconButton(onClick = onHistory) { Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.onSurface) }
            MicroIconButton(onClick = onTogether) { Icon(Icons.Default.Group, "Listen together", tint = MaterialTheme.colorScheme.onSurface) }
            MicroIconButton(onClick = onStats) { Icon(Icons.Default.BarChart, "Stats", tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
    if (LocalDensity.current.fontScale >= 1.4f) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("home_header")) {
            Text("MuseFlow", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.align(Alignment.End)) { actions() }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("home_header"), verticalAlignment = Alignment.CenterVertically) {
            Text("MuseFlow", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            actions()
        }
    }
}
