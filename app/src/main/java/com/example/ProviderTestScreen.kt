package com.example

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TEMPORARY debug-only screen for testing a [Provider] in isolation before it's wired into real
 * search - lets you flip between JioSaavn / YouTube Music / NetEase, search each independently,
 * and play a tapped result through the app's real player wiring, without touching
 * [SearchScreen]'s actual UI/merge logic at all. Delete this file (and its Settings entry point
 * and [Routes.PROVIDER_TEST] route) once NetEase (or whichever provider prompted adding this) is
 * either wired into real search or dropped.
 */
@Composable
fun ProviderTestScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSource by remember { mutableStateOf(MusicSource.JIOSAAVN) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TrackResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // id of the row currently resolving a stream URL before playback - only one at a time.
    var resolvingId by remember { mutableStateOf<String?>(null) }

    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider() }
    val netEaseProvider = remember { NetEaseProvider() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun providerFor(source: MusicSource): Provider<TrackResult> = when (source) {
        MusicSource.JIOSAAVN -> jioSaavnProvider
        MusicSource.YOUTUBE_MUSIC -> youTubeProvider
        MusicSource.NETEASE -> netEaseProvider
    }

    LaunchedEffect(query, selectedSource) {
        if (query.isBlank()) {
            results = emptyList()
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        delay(400)
        try {
            results = providerFor(selectedSource).search(query)
        } catch (e: Exception) {
            results = emptyList()
            errorMessage = e.message ?: "Search failed"
        } finally {
            isLoading = false
        }
    }

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("provider_test_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Provider Test", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Temporary debug screen - not wired into real search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicSource.entries.forEach { source ->
                    FilterChip(
                        selected = selectedSource == source,
                        onClick = {
                            selectedSource = source
                            results = emptyList()
                            errorMessage = null
                        },
                        label = { Text(source.name) },
                        modifier = Modifier.testTag("provider_test_source_${source.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search ${selectedSource.name}") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_test_search_field")
            )

            if (selectedSource == MusicSource.YOUTUBE_MUSIC && results.isNotEmpty()) {
                YtCipherTestSection(videoId = results.first().id)
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Text(
                    text = "Error: $errorMessage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("provider_test_error")
                )
                query.isBlank() -> Text(
                    "Type a query to search $selectedSource in isolation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                results.isEmpty() -> Text("No results.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("provider_test_results"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { result ->
                        val isResolving = resolvingId == result.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = resolvingId == null) {
                                    coroutineScope.launch {
                                        val streamUrl = result.directStreamUrl ?: run {
                                            resolvingId = result.id
                                            val resolution = runCatching {
                                                providerFor(selectedSource).getStreamUrl(result)
                                            }.getOrNull()
                                            resolvingId = null
                                            resolution?.url
                                        }
                                        if (streamUrl == null) {
                                            Toast.makeText(
                                                context,
                                                "Couldn't resolve a playable stream for this track",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@launch
                                        }
                                        val playableTrack = result.copy(directStreamUrl = streamUrl)
                                            .toPlayableTrack(gradientIndex = 0)
                                        onPlayTrack(playableTrack, listOf(playableTrack))
                                    }
                                }
                                .padding(vertical = 10.dp)
                                .testTag("provider_test_row_${result.title.lowercase().replace(" ", "_")}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = buildString {
                                        append(result.artist)
                                        append(" • ")
                                        append(result.source)
                                        result.duration?.let { append(" • $it") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TEMPORARY - proves out the `com.example.ytcipher` module (see [YtCipherIsolationTest]) against
 * a real, live-fetched ciphered stream URL for [videoId]. Isolated from playback entirely: this
 * only shows whether deciphering itself produced a genuinely different, plausible-looking URL -
 * it never attempts to play the result.
 */
@Composable
private fun YtCipherTestSection(videoId: String) {
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<YtCipherIsolationTest.Result>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Button(
            onClick = {
                isRunning = true
                coroutineScope.launch {
                    result = runCatching { YtCipherIsolationTest.run(context, videoId) }
                    isRunning = false
                }
            },
            enabled = !isRunning,
            modifier = Modifier.testTag("provider_test_yt_cipher_button")
        ) {
            Text(if (isRunning) "Testing cipher..." else "Test YT Cipher (isolated) - $videoId")
        }

        result?.getOrNull()?.let { r ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (r.differsFromInput && r.looksValid) "✅ PASS - deciphered URL differs and looks valid" else "⚠️ Check output below",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("provider_test_yt_cipher_result")
            )
            Text("Obfuscated s= (input): ${r.obfuscatedSig.take(60)}...", style = MaterialTheme.typography.bodySmall)
            Text("Deciphered URL (output): ${r.decipheredUrl.take(120)}...", style = MaterialTheme.typography.bodySmall)
            Text("Differs from input: ${r.differsFromInput}, looks valid: ${r.looksValid}", style = MaterialTheme.typography.bodySmall)
        }
        result?.exceptionOrNull()?.let { e ->
            Spacer(Modifier.height(8.dp))
            Text("❌ ${e.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}
