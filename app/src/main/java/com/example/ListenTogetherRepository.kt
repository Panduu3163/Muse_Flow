package com.example

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** JSON protocol adapted from Echo Music's GPL-3.0 Listen Together Protocol.kt.
 * https://github.com/EchoMusicApp/Echo-Music
 * Sessions are explicit; opening the screen never connects or shares playback. */
data class TogetherState(
    val connecting: Boolean = false, val connected: Boolean = false,
    val room: String = "", val host: Boolean = false, val userId: String = "",
    val message: String = "", val requests: Map<String, String> = emptyMap(),
)

class ListenTogetherRepository private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("listen_together", Context.MODE_PRIVATE)
    val savedServer: String get() = preferences.getString("server", "wss://metroserverx.meowery.eu/ws")!!
    val savedUsername: String get() = preferences.getString("username", "")!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var generation = 0
    private var player: Player? = null
    private var syncJob: Job? = null
    private var timeoutJob: Job? = null
    private var lastTrack: String? = null
    private var lastQueue: String? = null
    private var guestPlaying = false
    private val mutableState = MutableStateFlow(TogetherState())
    val state = mutableState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (state.value.host && state.value.room.isNotBlank()) publish()
            else if (state.value.room.isNotBlank() && player.playbackState == Player.STATE_READY) {
                player.currentMediaItem?.mediaId?.let { send("buffer_ready", JSONObject().put("track_id", it)) }
            }
        }
    }

    fun attach(player: Player) {
        this.player?.removeListener(listener)
        this.player = player
        player.addListener(listener)
    }

    fun detach() { player?.removeListener(listener); player = null; disconnect() }

    fun connect(server: String, username: String, room: String?) {
        if (!server.startsWith("wss://") || username.trim().isEmpty() || (room != null && room.isBlank())) {
            mutableState.value = state.value.copy(message = "Enter a secure wss:// server, username, and room code when joining.")
            return
        }
        disconnect()
        val ticket = generation
        val request = runCatching { Request.Builder().url(server.trim()).build() }.getOrElse {
            mutableState.value = TogetherState(message = "Invalid server address")
            return
        }
        preferences.edit().putString("server", server.trim()).putString("username", username.trim()).apply()
        mutableState.value = TogetherState(connecting = true, message = "Connecting…")
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { scope.launch {
                if (ticket != generation) return@launch
                mutableState.value = TogetherState(connected = true, connecting = true, message = if (room == null) "Creating room…" else "Waiting for host approval…")
                send(if (room == null) "create_room" else "join_room", JSONObject().put("username", username.trim()).apply {
                    room?.let { put("room_code", it.trim().uppercase()) }
                })
            } }
            override fun onMessage(webSocket: WebSocket, text: String) { scope.launch {
                if (ticket == generation) runCatching { receive(JSONObject(text)) }.onFailure {
                    mutableState.value = state.value.copy(message = "The server sent an unsupported message.")
                }
            } }
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = onMessage(webSocket, bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { scope.launch {
                if (ticket == generation) { disconnect(); mutableState.value = TogetherState(message = "Connection failed: ${t.message ?: "server unavailable"}. Tap Create or Join to retry.") }
            } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { scope.launch {
                if (ticket == generation) { disconnect(); mutableState.value = TogetherState(message = "Disconnected. Tap Create or Join to reconnect.") }
            } }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        })
        timeoutJob = scope.launch {
            delay(60_000)
            if (ticket == generation && state.value.room.isBlank()) {
                disconnect(); mutableState.value = TogetherState(message = "The server or host did not respond. Try again.")
            }
        }
        syncJob = scope.launch {
            while (isActive) {
                delay(5_000)
                if (state.value.room.isNotBlank()) {
                    send("ping")
                    if (state.value.host) publish() else send("request_sync")
                }
            }
        }
    }

    fun disconnect() {
        generation++
        syncJob?.cancel(); timeoutJob?.cancel()
        socket?.close(1000, "Leaving room"); socket = null
        lastTrack = null
        lastQueue = null
        guestPlaying = false
        mutableState.value = TogetherState()
    }

    fun approve(id: String, approved: Boolean) {
        send(if (approved) "approve_join" else "reject_join", JSONObject().put("user_id", id))
        mutableState.value = state.value.copy(requests = state.value.requests - id)
    }

    private fun send(type: String, payload: JSONObject = JSONObject()) {
        socket?.send(JSONObject().put("type", type).put("payload", payload).toString())
    }

    private fun publish() {
        val player = player ?: return
        val item = player.currentMediaItem ?: return
        if (!item.mediaId.looksLikeYouTubeVideoId()) {
            mutableState.value = state.value.copy(message = "Local files cannot be shared. Play a YouTube track to sync.")
            return
        }
        val queue = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val queued = player.getMediaItemAt(index)
            if (!queued.mediaId.looksLikeYouTubeVideoId()) continue
            queue.put(JSONObject().put("id", queued.mediaId)
                .put("title", queued.mediaMetadata.title?.toString().orEmpty())
                .put("artist", queued.mediaMetadata.artist?.toString().orEmpty())
                .put("duration", 0))
        }
        val queueIds = (0 until queue.length()).joinToString(",") { queue.getJSONObject(it).getString("id") }
        if (queueIds != lastQueue) {
            lastQueue = queueIds
            send("playback_action", JSONObject().put("action", "sync_queue").put("queue", queue))
        }
        val track = JSONObject().put("id", item.mediaId).put("title", item.mediaMetadata.title.toString())
            .put("artist", item.mediaMetadata.artist.toString()).put("duration", player.duration.coerceAtLeast(0))
            .put("thumbnail", item.mediaMetadata.artworkUri?.toString()?.takeIf { it.startsWith("https://") })
        if (lastTrack != item.mediaId) {
            lastTrack = item.mediaId
            send("playback_action", JSONObject().put("action", "change_track").put("track_id", item.mediaId).put("track_info", track).put("position", player.currentPosition))
        }
        send("playback_action", JSONObject().put("action", if (player.playWhenReady) "play" else "pause").put("position", player.currentPosition))
    }

    private fun receive(message: JSONObject) {
        val data = message.optJSONObject("payload") ?: JSONObject()
        when (message.optString("type")) {
            "room_created", "join_approved" -> {
                timeoutJob?.cancel()
                val host = message.optString("type") == "room_created"
                mutableState.value = TogetherState(connected = true, room = data.getString("room_code"), host = host, userId = data.optString("user_id"), message = if (host) "Share your room code with a friend." else "Following the host's playback.")
                if (host) publish() else data.optJSONObject("state")?.let(::applyState)
            }
            "join_request" -> mutableState.value = state.value.copy(requests = state.value.requests + (data.getString("user_id") to data.getString("username")))
            "join_rejected", "kicked", "error" -> {
                val error = data.optString("message", data.optString("reason", "Server rejected the request"))
                disconnect(); mutableState.value = TogetherState(message = error)
            }
            "host_changed" -> {
                mutableState.value = state.value.copy(host = data.optString("new_host_id") == state.value.userId)
                lastTrack = null
                if (state.value.host) publish()
            }
            "sync_state" -> if (!state.value.host) applyState(data)
            "sync_playback" -> if (!state.value.host) {
                when (data.optString("action")) {
                    "change_track" -> data.optJSONObject("track_info")?.let { applyTrack(it, data.optLong("position", 0)) }
                    "play" -> { guestPlaying = true; player?.seekTo(data.optLong("position", player?.currentPosition ?: 0)); player?.play() }
                    "pause" -> { guestPlaying = false; player?.pause() }
                    "seek" -> player?.seekTo(data.optLong("position", 0))
                    "sync_queue" -> applyQueue(data.optJSONArray("queue"))
                    "skip_next", "skip_prev" -> send("request_sync")
                }
            }
            "buffer_wait" -> { if (!state.value.host) player?.pause() }
            "buffer_complete" -> { if (!state.value.host && guestPlaying) player?.play() }
        }
    }

    private fun applyTrack(track: JSONObject, position: Long) {
        val player = player ?: return
        val id = track.optString("id")
        if (!id.looksLikeYouTubeVideoId()) return
        if (player.currentMediaItem?.mediaId != id) {
            val queuedIndex = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id }
            if (queuedIndex != null) {
                player.seekTo(queuedIndex, position.coerceAtLeast(0))
                return
            }
            val item = MediaItem.Builder().setMediaId(id).setUri(youTubeResolvePlaceholderUri(id))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.optString("title"))
                    .setArtist(track.optString("artist")).build()).build()
            player.setMediaItem(item, position.coerceAtLeast(0)); player.prepare()
        } else if (kotlin.math.abs(player.currentPosition - position) > 1_500) player.seekTo(position.coerceAtLeast(0))
    }

    private fun applyQueue(queue: JSONArray?) {
        val player = player ?: return
        if (queue == null || queue.length() == 0) return
        val currentId = player.currentMediaItem?.mediaId
        val items = (0 until queue.length()).mapNotNull { index ->
            val entry = queue.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id").takeIf { it.looksLikeYouTubeVideoId() } ?: return@mapNotNull null
            MediaItem.Builder().setMediaId(id).setUri(youTubeResolvePlaceholderUri(id))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.optString("title"))
                    .setArtist(entry.optString("artist")).build()).build()
        }
        if (items.isEmpty() || items.map { it.mediaId } == (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }) return
        val previousIndex = items.indexOfFirst { it.mediaId == currentId }
        player.setMediaItems(items, previousIndex.coerceAtLeast(0),
            if (previousIndex < 0) 0 else player.currentPosition.coerceAtLeast(0))
        player.prepare()
    }

    private fun applyState(data: JSONObject) {
        guestPlaying = data.optBoolean("is_playing")
        applyQueue(data.optJSONArray("queue"))
        // Server timestamps and positions are milliseconds in Echo's protocol.
        val elapsed = if (guestPlaying) (System.currentTimeMillis() - data.optLong("last_update", System.currentTimeMillis())).coerceIn(0, 15_000) else 0
        data.optJSONObject("current_track")?.let { applyTrack(it, data.optLong("position") + elapsed) }
        player?.playWhenReady = guestPlaying
    }

    companion object {
        @Volatile private var instance: ListenTogetherRepository? = null
        fun getInstance(context: Context): ListenTogetherRepository = instance ?: synchronized(this) {
            instance ?: ListenTogetherRepository(context.applicationContext).also { instance = it }
        }
    }
}
