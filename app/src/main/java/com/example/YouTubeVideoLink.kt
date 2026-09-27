package com.example

import java.net.URI
import java.net.URLDecoder

/** Accept only actual YouTube hosts, never lookalike domains or arbitrary URLs. */
internal fun videoIdFromLink(value: String): String? = runCatching {
    val uri = URI(value.trim())
    if (uri.scheme !in listOf("https", "http")) return null
    val host = uri.host?.lowercase() ?: return null
    val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    val id = when (host) {
        "youtu.be" -> parts.firstOrNull()
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
            if (parts.firstOrNull() in listOf("shorts", "embed", "live")) parts.getOrNull(1)
            else uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
        else -> null
    }
    id?.takeIf { it.looksLikeYouTubeVideoId() }
}.getOrNull()
