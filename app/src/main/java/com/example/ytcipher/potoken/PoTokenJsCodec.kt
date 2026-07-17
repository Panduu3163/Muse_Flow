package com.example.ytcipher.potoken

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes the raw BotGuard "Create"/"GenerateIT" response bodies into the shapes
 * `po_token_botguard.html`'s JS expects, and encodes/decodes the various YouTube-flavored base64
 * payloads exchanged with it. These shapes are dictated entirely by Google's own (undocumented)
 * BotGuard wire format, not by any particular client's implementation choices; reimplemented here
 * informed by reading zemer-cipher's equivalent (GPL-3.0, https://github.com/ZemerTeam/zemer-cipher).
 */
object PoTokenJsCodec {

    /** Parses the raw "Create" response body into a JS object-literal string ready to embed
     * directly in an `evaluateJavascript` call (valid JSON is valid JS object-literal syntax). */
    fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = JSONArray(rawChallengeData)
        val second = scrambled.opt(1)

        // Google sometimes returns the real challenge array scrambled (base64 + a per-byte
        // shift) inside element [1] instead of directly as element [0] - detected by whether
        // [1] is present and is a string.
        val challengeData: JSONArray = if (scrambled.length() > 1 && second is String) {
            JSONArray(descramble(second))
        } else {
            scrambled.getJSONArray(0)
        }

        val messageId = challengeData.optString(0)
        val interpreterHash = challengeData.optString(3)
        val program = challengeData.optString(4)
        val globalName = challengeData.optString(5)
        val clientExperimentsStateBlob = challengeData.optString(7)

        val safeScriptValue = findFirstString(challengeData.optJSONArray(1))
        val trustedResourceUrlValue = findFirstString(challengeData.optJSONArray(2))

        val result = JSONObject().apply {
            put("messageId", messageId)
            put(
                "interpreterJavascript",
                JSONObject().apply {
                    put("privateDoNotAccessOrElseSafeScriptWrappedValue", safeScriptValue ?: JSONObject.NULL)
                    put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", trustedResourceUrlValue ?: JSONObject.NULL)
                }
            )
            put("interpreterHash", interpreterHash)
            put("program", program)
            put("globalName", globalName)
            put("clientExperimentsStateBlob", clientExperimentsStateBlob)
        }
        return result.toString()
    }

    /** Parses the raw "GenerateIT" response body into (integrity token, as a JS `Uint8Array(...)`
     * literal ready to embed in JS source; token lifetime in seconds). */
    fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
        val array = JSONArray(rawIntegrityTokenData)
        val tokenBytes = base64ToBytes(array.getString(0))
        return jsUint8ArrayLiteral(tokenBytes) to array.getLong(1)
    }

    /** A JS `Uint8Array(...)` literal for [text]'s UTF-8 bytes - used to pass the per-call
     * identifier (video id, or a session's visitorData) into `obtainPoToken()`. */
    fun stringToJsUint8Array(text: String): String = jsUint8ArrayLiteral(text.toByteArray(Charsets.UTF_8))

    /** Takes a poToken as a comma-separated sequence of byte values (the output of a JS
     * `Uint8Array.join(",")`) and encodes it as YouTube's URL-safe-base64 poToken string. */
    fun bytesCsvToPoToken(bytesCsv: String): String {
        val bytes = bytesCsv.split(",").map { it.trim().toInt().toByte() }.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
            .replace("+", "-")
            .replace("/", "_")
    }

    /** Scrambled challenge payloads are base64, with every byte then shifted +97 (mod 256, via
     * plain [Byte] overflow) - an obfuscation layer, not real encryption. */
    private fun descramble(scrambled: String): String {
        val bytes = base64ToBytes(scrambled).map { (it + 97).toByte() }.toByteArray()
        return String(bytes, Charsets.UTF_8)
    }

    private fun jsUint8ArrayLiteral(bytes: ByteArray): String =
        "new Uint8Array([" + bytes.joinToString(",") { (it.toInt() and 0xFF).toString() } + "])"

    /** YouTube's base64 flavor: URL-safe alphabet, `.` in place of `=` padding. */
    private fun base64ToBytes(base64: String): ByteArray {
        val normalized = base64.replace('-', '+').replace('_', '/').replace('.', '=')
        return Base64.decode(normalized, Base64.DEFAULT)
    }

    private fun findFirstString(array: JSONArray?): String? {
        array ?: return null
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            if (value is String) return value
        }
        return null
    }
}
