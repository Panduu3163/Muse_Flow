package com.example.ui.screens

import com.example.ui.utils.MicroIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.MusicSearchRouter
import com.example.PlaylistRepository
import com.example.PlaylistShareCodec
import com.example.Track
import com.example.TrackResult
import com.example.extractYouTubePlaylistId
import com.example.toPlayableTrack
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableLongStateOf
import com.example.MAX_PLAYLIST_SHARE_CODE_CHARS

private sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    /** [remoteId] is set only for a playlist resolved from a YouTube link/id (not a MuseFlow
     * code) - carried through so "Add to my library" can pass it on to
     * [PlaylistRepository.importOnlinePlaylist], which is what lets a later Share on this same
     * playlist correctly go back out as a YouTube link instead of a MuseFlow code (see
     * `PlaylistShareCodec.sharePlaylist`'s own [remoteId] branch). */
    data class Preview(val name: String, val tracks: List<Track>, val remoteId: String? = null) : ImportState
    data object InvalidCode : ImportState
    data object FetchError : ImportState
    data class SaveError(val preview: Preview) : ImportState
    data class Imported(val name: String) : ImportState
}

/**
 * Settings/Library's "Import shared playlist" screen - the receiving half of both playlist-share
 * paths this app has: a MuseFlow code from [PlaylistShareCodec] (a local playlist someone
 * encoded), or a plain YouTube playlist link/id ([extractYouTubePlaylistId]) shared straight from
 * [RemotePlaylistScreen] or a saved-from-YouTube library playlist. Either way, this ends the same
 * way "Add to Library" on a remote playlist's own screen does: a call to
 * [PlaylistRepository.importOnlinePlaylist].
 */
@Composable
fun ImportSharedPlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fetchPlaylist: (suspend (String) -> List<TrackResult>)? = null,
    savePlaylist: (suspend (String, List<Track>, String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    var isImporting by remember { mutableStateOf(false) }
    var previewGeneration by remember { mutableLongStateOf(0) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            // The pasted text (or the track-count once previewed) can run taller than the screen
            // - this used to have no scroll at all, so a long paste shoved the Preview button and
            // everything below it off-screen with no way to reach them.
            .verticalScroll(rememberScrollState())
            .testTag("import_shared_playlist_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroIconButton(onClick = onBack, modifier = Modifier.testTag("import_playlist_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Import shared playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Paste a MuseFlow playlist code, or a YouTube/YouTube Music playlist link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = code,
                onValueChange = {
                    previewGeneration++
                    previewJob?.cancel()
                    code = it.take(MAX_PLAYLIST_SHARE_CODE_CHARS + 1)
                    state = ImportState.Idle
                },
                enabled = !isImporting,
                placeholder = { Text("museflow-playlist:v1:... or a playlist link") },
                modifier = Modifier
                    .fillMaxWidth()
                    // Capped, rather than left to grow with the pasted text - a long paste (the
                    // whole shared message, codes and all) used to stretch this field to match,
                    // pushing the Preview button far down the page. It scrolls internally past
                    // this height instead.
                    .heightIn(max = 140.dp)
                    .testTag("import_playlist_code_field"),
                minLines = 3,
            )

            Button(
                onClick = {
                    if (code.length > MAX_PLAYLIST_SHARE_CODE_CHARS) {
                        state = ImportState.InvalidCode
                        return@Button
                    }
                    val request = ++previewGeneration
                    val decoded = PlaylistShareCodec.decode(code)
                    val youTubePlaylistId = extractYouTubePlaylistId(code)
                    when {
                        decoded != null -> state = ImportState.Preview(decoded.name, decoded.tracks)
                        youTubePlaylistId != null -> {
                            state = ImportState.Loading
                            previewJob = scope.launch {
                                val tracks = try { fetchPlaylist?.invoke(youTubePlaylistId) ?: router.getPlaylistTracks(youTubePlaylistId) }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { null }
                                if (request != previewGeneration) return@launch
                                state = if (tracks != null) {
                                    ImportState.Preview(
                                        name = "Imported YouTube playlist",
                                        tracks = tracks.map { it.toPlayableTrack(it.id.hashCode()) },
                                        remoteId = youTubePlaylistId,
                                    )
                                } else {
                                    ImportState.FetchError
                                }
                            }
                        }
                        else -> state = ImportState.InvalidCode
                    }
                },
                enabled = code.isNotBlank() && state !is ImportState.Loading && !isImporting,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("import_playlist_decode_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Preview playlist", fontWeight = FontWeight.Bold)
            }

            when (val current = state) {
                is ImportState.SaveError -> Column {
                    Text("Couldn't save this playlist. Check free storage, then retry.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { state = current.preview }) { Text("Return to preview") }
                }
                is ImportState.Idle -> Unit

                is ImportState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Loading playlist...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ImportState.InvalidCode -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = "That doesn't look like a MuseFlow playlist code or a YouTube playlist link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is ImportState.FetchError -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = "Couldn't load that YouTube playlist. Check your connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is ImportState.Imported -> Text(
                    text = "Added \"${current.name}\" to your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("import_playlist_success"),
                )

                is ImportState.Preview -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = current.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${current.tracks.size} ${if (current.tracks.size == 1) "song" else "songs"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            LazyColumn(modifier = Modifier.height(220.dp)) {
                                // Position-keyed, not title+artist: a playlist can legitimately
                                // contain the same title/artist twice (a duplicate track, or two
                                // different songs whose title+artist happen to concatenate the
                                // same way with no separator between them) - either one crashed
                                // with "Key ... was already used" under a text-concatenated key,
                                // exactly the reason every other duplicate-content-risk list in
                                // this app (Home's shelves, Search) already keys by position.
                                itemsIndexed(current.tracks, key = { index, _ -> index }) { _, track ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            if (track.imageUrl != null) {
                                                AsyncImage(
                                                    model = track.imageUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.clip(CircleShape),
                                                )
                                            } else {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Column(modifier = Modifier.padding(start = 12.dp)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = track.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            isImporting = true
                            scope.launch {
                                try {
                                  withContext(NonCancellable) { if (savePlaylist != null) savePlaylist(current.name, current.tracks, current.remoteId)
                                    else PlaylistRepository.getInstance(context).importOnlinePlaylist(
                                    name = current.name,
                                    coverImageUrl = current.tracks.firstOrNull()?.imageUrl,
                                    tracks = current.tracks,
                                    remoteId = current.remoteId,
                                  ) }
                                  state = ImportState.Imported(current.name)
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { state = ImportState.SaveError(current) }
                                finally { isImporting = false }
                            }
                        },
                        enabled = !isImporting && current.tracks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("import_playlist_confirm"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Add to my library", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isImporting) Text("Saving the playlist together. This step finishes even if you leave this screen.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            // Clears the mini-player from ever covering the last bit of content - matches the
            // 200dp bottom clearance every other scrollable screen in this app reserves.
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(200.dp))
        }
    }
}
