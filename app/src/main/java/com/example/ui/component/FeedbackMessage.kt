package com.example.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.MuseFlowShapes
import com.example.ui.theme.MuseFlowSpacing

/** Shared readable empty/error treatment for the top-level browsing screens. */
@Composable
fun FeedbackMessage(message: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)
                .semantics { if (isError) liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(MuseFlowShapes.card),
            color = container,
            contentColor = content,
        ) {
            Row(
                modifier = Modifier.padding(MuseFlowSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MuseFlowSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
