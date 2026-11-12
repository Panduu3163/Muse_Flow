package com.example

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/** Cancelling a song lookup cancels its network call, including a body still being read. */
internal suspend fun OkHttpClient.fetchLyricsJson(url: String): JSONObject? =
    fetchLyricsBody(url)?.let(::JSONObject)

internal suspend fun OkHttpClient.fetchLyricsBody(url: String): String? =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "MuseFlow/1.7 (Android)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        when (it.code) {
                            204, 404 -> null
                            401, 403 -> throw IOException("Lyrics source requires access authorization. Try another source.")
                            429 -> throw IOException("Lyrics source is busy. Try again shortly.")
                            503 -> throw IOException("Lyrics source is temporarily unavailable. Try another source or retry later.")
                            in 200..299 -> it.body.string().takeIf { body -> body.isNotBlank() }
                                ?: throw IOException("Empty lyrics response")
                            else -> throw IOException("Lyrics source unavailable (${it.code})")
                        }
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
