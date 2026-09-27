package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ListenTogetherRepository
import com.example.ui.component.liquidSurface

@Composable
fun ListenTogetherComingSoonScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Listen Together", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxSize().weight(1f).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(96.dp).liquidSurface(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Coming soon", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Stay tuned!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ListenTogetherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ListenTogetherRepository.getInstance(context) }
    val state by repository.state.collectAsState()
    var create by rememberSaveable { mutableStateOf(true) }
    var username by rememberSaveable { mutableStateOf(repository.savedUsername) }
    var server by rememberSaveable { mutableStateOf(repository.savedServer) }
    var code by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 180.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Listen Together", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.connected || state.connecting) TextButton(onClick = repository::disconnect) { Text("Disconnect") }
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (state.room.isBlank()) {
                Row(Modifier.fillMaxWidth().liquidSurface().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(create, { create = true }, { Text("Create") }, enabled = !state.connecting, modifier = Modifier.weight(1f))
                    FilterChip(!create, { create = false }, { Text("Join") }, enabled = !state.connecting, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(username, { username = it.take(40) }, label = { Text("Username") }, singleLine = true, shape = CircleShape, modifier = Modifier.fillMaxWidth())
                if (!create) OutlinedTextField(code, { code = it.trim().uppercase().take(32) }, label = { Text("Room code") }, singleLine = true, shape = CircleShape, modifier = Modifier.fillMaxWidth())
                Button(onClick = { repository.connect(server, username, if (create) null else code) }, enabled = !state.connecting && username.isNotBlank() && (create || code.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (state.connecting) "Connecting…" else if (create) "Create room" else "Join room")
                }
            } else {
                Column(Modifier.fillMaxWidth().liquidSurface().padding(24.dp)) {
                    Text(if (state.host) "Your room" else "Connected to room", style = MaterialTheme.typography.labelLarge)
                    Text(state.room, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MuseFlow room", state.room))
                    }) { Text("Copy room code") }
                }
                state.requests.forEach { (id, name) ->
                    Column(Modifier.fillMaxWidth().liquidSurface().padding(16.dp)) {
                        Text("$name wants to join")
                        Row {
                            TextButton({ repository.approve(id, true) }) { Text("Approve") }
                            TextButton({ repository.approve(id, false) }) { Text("Decline") }
                        }
                    }
                }
            }
            if (state.message.isNotBlank()) Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { settings = !settings }, modifier = Modifier.fillMaxWidth()) { Text("Server settings") }
            if (settings) OutlinedTextField(server, { server = it }, label = { Text("Secure WebSocket server") }, supportingText = { Text("Use an Echo / Metrolist compatible server. Changes apply on the next connection.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().liquidSurface().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleLarge)
                Text("1   Create a room and share its code.")
                Text("2   Your friend joins using the same server.")
                Text("3   Approve their request, then play music. The host controls playback; each device streams its own audio.")
            }
        }
    }
}
