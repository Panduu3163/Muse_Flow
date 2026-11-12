package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/** Avoid a one-frame loading flash, but keep sustained audio buffering visible and truthful. */
@Composable
internal fun PlayerBufferingIndicator(isBuffering: Boolean, recordingKey: String,
    modifier: Modifier = Modifier, allowDisplay: Boolean = true) {
    var visible by remember(recordingKey, isBuffering) { mutableStateOf(false) }
    LaunchedEffect(recordingKey, isBuffering) {
        if (isBuffering) { delay(350); visible = true }
    }
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(isBuffering && visible && allowDisplay, modifier,
        enter = fadeIn(tween(if (reduced) 0 else 120)), exit = fadeOut(tween(if (reduced) 0 else 100))) {
        Surface(color = Color.Black.copy(alpha = .48f), contentColor = Color.White,
            shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reduced) Icon(Icons.Default.HourglassEmpty, null, Modifier.size(18.dp))
                else CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Buffering", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
