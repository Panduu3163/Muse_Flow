package com.example

import android.content.Context
import android.util.Log
import com.example.ytcipher.potoken.PoTokenGenerator
import com.example.ytcipher.potoken.PoTokenResult
import kotlin.random.Random

private const val TAG = "PoToken"

/**
 * TEST-ONLY glue for proving the `com.example.ytcipher.potoken` module out in isolation - NOT
 * part of that module, and not wired into any real playback path. This step only proves
 * generation itself works (runs the BotGuard challenge, mints a token, gets back a non-empty,
 * plausible-looking string); it deliberately does NOT validate the token against a real request -
 * that's the next step, once it's combined with real `/player` calls.
 *
 * Delete this file once the potoken module either gets wired into real playback or is dropped.
 */
object PoTokenIsolationTest {

    data class Result(
        val videoId: String,
        val sessionId: String,
        val playerRequestPoToken: String,
        val streamingDataPoToken: String,
        val looksPlausible: Boolean
    )

    /** A real InnerTube session normally carries a server-issued `visitorData` string as the
     * session identifier this binds to; for this isolated proof any sufficiently random, stable
     * string plays that role - it's just the identifier this device's session token binds to. */
    private fun randomSessionId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return "TEST_" + (1..24).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun run(context: Context, videoId: String): Result {
        val sessionId = randomSessionId()
        Log.d(TAG, "Generating PoToken for videoId=$videoId, sessionId=$sessionId")

        val poTokenResult: PoTokenResult = PoTokenGenerator.generate(context, videoId, sessionId)
            ?: error("PoTokenGenerator.generate() returned null - see logcat tag \"$TAG\" for why")

        Log.d(TAG, "playerRequestPoToken=${poTokenResult.playerRequestPoToken}")
        Log.d(TAG, "streamingDataPoToken=${poTokenResult.streamingDataPoToken}")

        // A real poToken is a substantial (typically 100+ char) URL-safe-base64 string. Not a
        // guarantee of validity (that needs a real request - the next step), just a sanity check
        // that generation produced something structurally plausible rather than garbage/empty.
        fun looksPlausible(token: String) =
            token.length >= 80 && token.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' }

        return Result(
            videoId = videoId,
            sessionId = sessionId,
            playerRequestPoToken = poTokenResult.playerRequestPoToken,
            streamingDataPoToken = poTokenResult.streamingDataPoToken,
            looksPlausible = looksPlausible(poTokenResult.playerRequestPoToken) &&
                looksPlausible(poTokenResult.streamingDataPoToken)
        )
    }
}
