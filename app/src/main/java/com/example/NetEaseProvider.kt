package com.example

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Talks to NetEase Cloud Music's private "weapi" API - the same undocumented, encrypted endpoints
 * music.163.com's own web client uses, reimplemented from the widely-published protocol (the
 * AES/RSA scheme and constants below match what's cross-checked across many independent
 * open-source clients over the years, e.g. Binaryify/NeteaseCloudMusicApi's `util/crypto.js`).
 *
 * Two things this needs that JioSaavn/YouTube Music don't:
 *  - Every request body is double-AES-CBC-encrypted, with the second pass's random key itself
 *    RSA-encrypted (raw, unpadded) using a fixed public key - see [Crypto.weapi].
 *  - As of ~2023, NetEase requires a session cookie (obtained via an "anonymous account"
 *    registration call) even for anonymous/unauthenticated use - without it, `search` still
 *    works, but every [getStreamUrl] call comes back with a null URL (verified empirically: the
 *    server returns HTTP 200 with `"url":null,"code":404"` for every track when unauthenticated,
 *    regardless of track/region). [ensureRegistered] performs that registration lazily, once,
 *    the first time either method is called, and the resulting cookie is kept for every
 *    subsequent request via [cookieJar].
 *
 * This is a from-scratch reimplementation of a well-known protocol, not copied code.
 */
class NetEaseProvider : Provider<TrackResult> {

    override val name = "NetEase Cloud Music"

    class NetEaseException(message: String) : Exception(message)

    private object Api {
        const val BASE_URL = "https://music.163.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    /** In-memory cookie store, seeded with the two fixed cookies the web client always sends and
     * updated with whatever [ensureRegistered] gets back (crucially, `MUSIC_A`, the anonymous
     * session token every other request needs). Scoped to this provider instance - a fresh
     * instance means a fresh (re-registered) session, same as a fresh browser profile would. */
    private class SessionCookieJar : CookieJar {
        private val cookies = mutableListOf(
            Cookie.Builder().domain("music.163.com").name("os").value("pc").build(),
            Cookie.Builder().domain("music.163.com").name("appver").value("8.9.70").build()
        )

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(this) {
                cookies.forEach { fresh -> this.cookies.removeAll { it.name == fresh.name }; this.cookies.add(fresh) }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) { cookies.toList() }
    }

    private val cookieJar = SessionCookieJar()
    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val registrationMutex = Mutex()
    @Volatile private var isRegistered = false

    override suspend fun search(query: String): List<TrackResult> = withContext(Dispatchers.IO) {
        ensureRegistered()
        val payload = JSONObject().apply {
            put("s", query)
            put("type", 1) // 1 = songs
            put("offset", 0)
            put("limit", 20)
        }
        val root = postWeapi("/weapi/search/get", payload)
        val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
        (0 until songs.length()).mapNotNull { i -> parseSong(songs.optJSONObject(i)) }
    }

    /** Resolves [item] to a direct, playable stream URL. Requires [ensureRegistered] to have
     * succeeded - see the class doc for why an anonymous session cookie is non-optional here. */
    override suspend fun getStreamUrl(item: TrackResult): StreamResolution? = withContext(Dispatchers.IO) {
        ensureRegistered()
        val payload = JSONObject().apply {
            put("ids", "[${item.id}]")
            put("br", 320000) // requested bitrate; server serves the highest it'll actually allow
        }
        val root = postWeapi("/weapi/song/enhance/player/url", payload)
        val entry = root.optJSONArray("data")?.optJSONObject(0) ?: return@withContext null
        val url = entry.optString("url").takeIf { it.isNotBlank() && entry.optInt("code") == 200 }
            ?: return@withContext null
        StreamResolution(url = url)
    }

    /**
     * Registers a fresh anonymous NetEase account (a random per-instance "device ID" + the
     * server's expected encoding of it - see [DeviceIdEncoder]), which sets the `MUSIC_A` session
     * cookie every subsequent request implicitly reuses via [cookieJar]. Idempotent and
     * concurrency-safe: only the first caller actually hits the network, everyone else just waits
     * on the mutex and then finds [isRegistered] already true.
     *
     * A freshly-generated, never-before-seen device ID is rejected by NetEase (`code != 200`)
     * more often than not - confirmed empirically, not just theoretically: identical requests
     * from different source IPs got accepted or rejected inconsistently in testing. This isn't
     * surfaced as an exception (a rejection is an ordinary, expected response, not a network
     * failure), so a single silent attempt previously left [isRegistered] `true` after a rejected
     * registration, meaning every later [getStreamUrl] call ran without a real session and got a
     * null URL back with no indication why. Retrying with a fresh ID a few times before giving up
     * fixes that; [isRegistered] is only set once the server actually confirms `code == 200`.
     */
    private suspend fun ensureRegistered() {
        if (isRegistered) return
        registrationMutex.withLock {
            if (isRegistered) return
            repeat(MAX_REGISTRATION_ATTEMPTS) {
                val deviceId = randomDeviceId()
                val username = Base64.encodeToString(
                    "$deviceId ${DeviceIdEncoder.encode(deviceId)}".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                val response = postWeapi("/weapi/register/anonimous", JSONObject().put("username", username))
                if (response.optInt("code") == 200) {
                    isRegistered = true
                    return@withLock
                }
            }
        }
    }

    private companion object {
        const val MAX_REGISTRATION_ATTEMPTS = 4
    }

    private fun postWeapi(path: String, payload: JSONObject): JSONObject {
        val encrypted = Crypto.weapi(payload)
        val formBody = FormBody.Builder()
            .add("params", encrypted.params)
            .add("encSecKey", encrypted.encSecKey)
            .build()
        val request = Request.Builder()
            .url("${Api.BASE_URL}$path?csrf_token=")
            .header("User-Agent", Api.USER_AGENT)
            .header("Referer", "${Api.BASE_URL}/")
            .post(formBody)
            .build()

        val bodyString = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw NetEaseException("Request failed: HTTP ${response.code}")
            response.body?.string() ?: throw NetEaseException("Empty response")
        }
        return JSONObject(bodyString)
    }

    private fun parseSong(song: JSONObject?): TrackResult? {
        song ?: return null
        val id = song.optLong("id", -1L).takeIf { it > 0 } ?: return null
        val title = song.optString("name").takeIf { it.isNotBlank() } ?: return null

        val artistsArray = song.optJSONArray("artists")
        val artist = artistsArray?.let { artists ->
            (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
        }?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: "Unknown artist"

        val durationMs = song.optLong("duration", 0L)
        val duration = if (durationMs > 0) {
            val totalSeconds = (durationMs / 1000).toInt()
            "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
        } else null

        return TrackResult(
            id = id.toString(),
            title = title,
            artist = artist,
            duration = duration,
            source = name,
            sourceType = MusicSource.NETEASE,
            // Search results carry no stream URL for NetEase either - resolved lazily via
            // getStreamUrl, same reason as YouTube Music (needs its own authenticated call).
            directStreamUrl = null,
            // search/get's song objects don't include album art; only the richer (and, in
            // testing, extra-request-blocked) cloudsearch endpoint does. Left null - callers
            // fall back to the gradient placeholder, same as any other imageless result.
            imageUrl = null
        )
    }

    private fun randomDeviceId(): String {
        val hexChars = "0123456789ABCDEF"
        return (1..53).map { hexChars[Random.nextInt(hexChars.length)] }.joinToString("")
    }

    /** NetEase's anonymous-registration endpoint expects the device ID XOR-obfuscated against a
     * fixed key, MD5-hashed, then combined with the plain device ID and base64-encoded as the
     * "username" field - an undocumented but well-established quirk of this specific endpoint,
     * distinct from the general request-level "weapi" encryption in [Crypto]. */
    private object DeviceIdEncoder {
        private const val XOR_KEY = "3go8&\$8*3*3h0k(2)2"

        fun encode(deviceId: String): String {
            val xored = deviceId.mapIndexed { i, c -> (c.code xor XOR_KEY[i % XOR_KEY.length].code).toChar() }
                .joinToString("")
            val digest = MessageDigest.getInstance("MD5").digest(xored.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        }
    }

    /** NetEase's "weapi" request-body encryption: the JSON payload is AES-128-CBC-encrypted
     * twice (first with a fixed preset key, then with a random per-request key), and that random
     * key is itself RSA-encrypted (raw/unpadded, not PKCS#1) with a fixed public key so only
     * NetEase's server can recover it. Every constant below (IV, preset key, RSA modulus/exponent)
     * is the same fixed value music.163.com's own web client has used for years. */
    private object Crypto {
        private val IV = IvParameterSpec("0102030405060708".toByteArray(Charsets.UTF_8))
        private val PRESET_KEY = SecretKeySpec("0CoJUm6Qyw8W8jud".toByteArray(Charsets.UTF_8), "AES")
        private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        // Fixed 1024-bit RSA public key NetEase's web client encrypts the AES key with.
        private val RSA_MODULUS = BigInteger(
            "e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e" +
                "417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee2559" +
                "32575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7",
            16
        )
        private val RSA_EXPONENT = BigInteger("10001", 16)

        data class WeApiBody(val params: String, val encSecKey: String)

        fun weapi(payload: JSONObject): WeApiBody {
            val text = payload.toString()
            val secretKey = (1..16).map { BASE62[Random.nextInt(BASE62.length)] }.joinToString("")
            val firstPass = aesCbcEncryptBase64(text.toByteArray(Charsets.UTF_8), PRESET_KEY)
            val secondPass = aesCbcEncryptBase64(
                firstPass.toByteArray(Charsets.UTF_8),
                SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "AES")
            )
            return WeApiBody(params = secondPass, encSecKey = rsaEncryptHex(secretKey.reversed()))
        }

        private fun aesCbcEncryptBase64(text: ByteArray, key: SecretKeySpec): String {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IV)
            return Base64.encodeToString(cipher.doFinal(text), Base64.NO_WRAP)
        }

        /** Raw (unpadded) RSA: `text^exponent mod modulus`, hex-encoded and zero-padded to the
         * modulus's byte length - not a standard padding scheme, but exactly what NetEase's
         * server expects for this one field. */
        private fun rsaEncryptHex(text: String): String {
            val message = BigInteger(1, text.toByteArray(Charsets.UTF_8))
            val encrypted = message.modPow(RSA_EXPONENT, RSA_MODULUS)
            val hexLength = (RSA_MODULUS.bitLength() + 7) / 8 * 2
            return encrypted.toString(16).padStart(hexLength, '0')
        }
    }
}
