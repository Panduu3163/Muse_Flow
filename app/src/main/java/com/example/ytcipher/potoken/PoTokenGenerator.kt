package com.example.ytcipher.potoken

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "PoToken"

/**
 * Public entry point for PoToken generation. Self-dispatches to [Dispatchers.IO] (same convention
 * as `com.example.ytcipher.YtCipherDeobfuscator`'s entry points) so callers never need to think
 * about which thread to call from; the one WebView-specific call that must run on the main thread
 * ([PoTokenWebView.close]) is still explicitly switched there internally.
 *
 * A session's PoToken (bound to its `visitorData`, sent in the `/player` request) is minted once
 * and reused across every video played in that session; a fresh, video-id-bound PoToken is
 * minted per video (appended to the actual stream URL - see [PoTokenResult]).
 */
object PoTokenGenerator {

    private val mutex = Mutex()
    private var sessionId: String? = null
    private var sessionPoToken: String? = null
    private var webView: PoTokenWebView? = null

    private const val TOTAL_TIMEOUT_MS = 8_000L

    /**
     * Returns null (rather than throwing) if PoToken generation isn't viable on this device/
     * WebView build, or times out - callers should treat null as "proceed without a PoToken",
     * not as a hard failure severe enough to abort playback.
     */
    suspend fun generate(context: Context, videoId: String, sessionId: String): PoTokenResult? =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(TOTAL_TIMEOUT_MS) {
                    generateInternal(context, videoId, sessionId, forceRecreate = false)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "PoToken generation timed out after ${TOTAL_TIMEOUT_MS}ms")
                resetState()
                null
            } catch (e: BadWebViewException) {
                Log.e(TAG, "This device's WebView implementation can't run the BotGuard challenge", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "PoToken generation failed", e)
                null
            }
        }

    private suspend fun generateInternal(
        context: Context,
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean
    ): PoTokenResult {
        val (activeWebView, activeSessionPoToken, wasRecreated) = mutex.withLock {
            val needsRecreate = forceRecreate || webView == null || webView!!.isExpired ||
                webView!!.isDead || this.sessionId != sessionId

            if (needsRecreate) {
                closeWebViewLocked()
                val created = PoTokenWebView.create(context)
                // The session token must be minted exactly once, before any per-video token -
                // it's reused across every later video in this session.
                val mintedSessionToken = try {
                    created.generatePoToken(sessionId)
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { created.close() }
                    throw t
                }
                webView = created
                sessionPoToken = mintedSessionToken
                this.sessionId = sessionId
            }
            Triple(webView!!, sessionPoToken!!, needsRecreate)
        }

        val videoPoToken = try {
            activeWebView.generatePoToken(videoId)
        } catch (t: Throwable) {
            if (wasRecreated) throw t
            // The WebView might just be stale (e.g. the app was backgrounded and its render
            // process reclaimed) - one retry from a fresh instance before giving up.
            return generateInternal(context, videoId, sessionId, forceRecreate = true)
        }

        return PoTokenResult(playerRequestPoToken = activeSessionPoToken, streamingDataPoToken = videoPoToken)
    }

    private suspend fun closeWebViewLocked() {
        // WebView.destroy() must run on the main thread like every other WebView method - this
        // function always runs under Dispatchers.IO (via the public generate() entry point above),
        // so without this explicit switch, close() would run on IO and throw.
        webView?.let { stale -> withContext(Dispatchers.Main) { stale.close() } }
        webView = null
        sessionPoToken = null
        this.sessionId = null
    }

    private suspend fun resetState() {
        mutex.withLock { closeWebViewLocked() }
    }
}
