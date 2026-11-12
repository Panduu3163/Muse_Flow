package com.example.ui.component

import com.example.ui.utils.MotionLabel
import com.example.ui.utils.MotionIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
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
fun FeedbackMessage(message: String, modifier: Modifier = Modifier, isError: Boolean = false, onRetry: (() -> Unit)? = null) {
    val duration = if (com.example.ui.theme.LocalReducedMotion.current) 0 else 160
    val container by animateColorAsState(if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainer, tween(duration), label = "feedback_container")
    val content by animateColorAsState(if (isError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurfaceVariant, tween(duration), label = "feedback_content")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .semantics { if (isError) liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(MuseFlowShapes.card),
            color = container,
            contentColor = content,
        ) {
            Column {
            Row(
                modifier = Modifier.padding(MuseFlowSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MuseFlowSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MotionIcon(
                    imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                MotionLabel(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) { Text("Retry") }
            }
        }
    }
}
