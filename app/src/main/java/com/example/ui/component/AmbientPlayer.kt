package com.example.ui.component

import com.example.ui.utils.MicroIconButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.PlayerViewModel

@Composable
fun AmbientPlayer(player: PlayerViewModel, onDismiss: () -> Unit) {
    val state by player.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(state.artworkUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(48.dp))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text(state.artist, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .75f))
                Spacer(Modifier.height(32.dp))
                Row {
                    MicroIconButton(player::previous) { Icon(Icons.Default.SkipPrevious, "Previous track", tint = Color.White) }
                    MicroIconButton(player::togglePlayPause) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White) }
                    MicroIconButton(player::next) { Icon(Icons.Default.SkipNext, "Next track", tint = Color.White) }
                }
            }
            MicroIconButton(onDismiss, Modifier.align(Alignment.TopEnd).statusBarsPadding()) { Icon(Icons.Default.Close, "Exit ambient mode", tint = Color.White) }
        }
    }
}
