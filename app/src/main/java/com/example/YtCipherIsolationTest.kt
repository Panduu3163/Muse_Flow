package com.example

import android.content.Context
import com.example.ytcipher.YtCipherDeobfuscator
import com.example.ytcipher.YtCipherFunctionExtractor
import com.example.ytcipher.YtPlayerJsFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TEST-ONLY glue for proving the `com.example.ytcipher` module out in isolation - NOT part of
 * that module, and not wired into any real playback path. [YouTubeMusicProvider] deliberately
 * never requests a `signatureCipher`-bearing format (it uses ANDROID_VR/IOS clients specifically
 * to avoid needing a cipher solver at all - see its class doc comment), so getting a genuine
 * ciphered sample to test against requires a raw WEB_REMIX `/player` call this file makes itself,
 * once, purely for this isolated proof.
 *
 * Delete this file once the ytcipher module either gets wired into real playback or is dropped.
 */
object YtCipherIsolationTest {

    data class Result(
        val videoId: String,
        val rawSignatureCipher: String,
        val obfuscatedSig: String,
        val decipheredUrl: String,
        val looksValid: Boolean,
        val differsFromInput: Boolean
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches a real `signatureCipher` for [videoId] via a raw WEB_REMIX `/player` call (WEB
     * clients are the ones that actually cipher their stream URLs - unlike the ANDROID_VR/IOS
     * clients this app streams from in production), runs it through [YtCipherDeobfuscator], and
     * reports whether the output looks like a genuinely different, valid stream URL.
     */
    suspend fun run(context: Context, videoId: String): Result = withContext(Dispatchers.IO) {
        YtCipherDeobfuscator.initialize(context)

        val playerJs = YtPlayerJsFetcher.getPlayerJs()
            ?: error("Could not fetch player.js")
        val signatureTimestamp = YtCipherFunctionExtractor.extractSignatureTimestamp(
            playerJs.source, context, playerJs.hash
        ) ?: error("Could not determine signatureTimestamp")

        val signatureCipher = fetchSignatureCipher(videoId, signatureTimestamp)
            ?: error("No signatureCipher-bearing format found for $videoId")

        val obfuscatedSig = Regex("""[?&]?s=([^&]+)""").find(signatureCipher)?.groupValues?.get(1)
            ?: error("Could not parse 's' param out of signatureCipher")

        val decipheredUrl = YtCipherDeobfuscator.deobfuscateStreamUrl(signatureCipher)
            ?: error("deobfuscateStreamUrl returned null")

        val looksValid = decipheredUrl.startsWith("https://") &&
            decipheredUrl.contains("googlevideo.com") &&
            (decipheredUrl.contains("sig=") || decipheredUrl.contains("signature="))

        Result(
            videoId = videoId,
            rawSignatureCipher = signatureCipher,
            obfuscatedSig = obfuscatedSig,
            decipheredUrl = decipheredUrl,
            looksValid = looksValid,
            differsFromInput = decipheredUrl != signatureCipher && !decipheredUrl.contains(obfuscatedSig)
        )
    }

    private fun fetchSignatureCipher(videoId: String, signatureTimestamp: Int): String? {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20260114.03.00")
                    put("gl", "US")
                    put("hl", "en")
                })
                put("request", JSONObject().apply {
                    put("internalExperimentFlags", JSONArray())
                    put("useSsl", true)
                })
                put("user", JSONObject().apply { put("lockedSafetyMode", false) })
            })
            put("videoId", videoId)
            put("playlistId", JSONObject.NULL)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("signatureTimestamp", signatureTimestamp)
                })
            })
        }

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20260114.03.00")
            .header("X-Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
            )
            .build()

        val root = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string() ?: return null)
        }

        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            val cipher = format.optString("signatureCipher").takeIf { it.isNotBlank() }
            if (cipher != null) return cipher
        }
        return null
    }
}
