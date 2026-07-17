package com.example.ytcipher

import android.content.Context
import java.security.MessageDigest

/**
 * Locates the signature-decipher and n-parameter-transform functions inside a raw player.js.
 * Prefers the verified [YtCipherConfigStore] table (see its doc comment for provenance); falls
 * back to regex heuristics against the raw JS for any player hash the table doesn't cover -
 * these heuristic patterns target the "classic" unminified-enough call shape YouTube's player has
 * used across many generations (`x&&(sig=FUNC(N,decodeURIComponent(x)))` for the signature call,
 * `.get("n"))&&(b=FUNC[i](c))` for the n-transform), informed by reading how zemer-cipher
 * (GPL-3.0, https://github.com/ZemerTeam/zemer-cipher) and public deobfuscation write-ups
 * (yt-dlp/NewPipe) describe this shape - reimplemented here, not copied line-for-line.
 *
 * Heuristics alone are not reliable against the *current* generation of player.js: YouTube's
 * newest builds dispatch through small VM-like opcode tables that don't match any fixed text
 * pattern, which is exactly why the config table exists and is checked first.
 */
object YtCipherFunctionExtractor {

    private val PLAYER_HASH_PATTERNS = listOf(
        Regex("""jsUrl['":\s]+[^"']*?/player/([a-f0-9]{8})/"""),
        Regex("""player_ias\.vflset/[^/]+/([a-f0-9]{8})/"""),
        Regex("""/s/player/([a-f0-9]{8})/""")
    )

    private val SIGNATURE_TIMESTAMP_PATTERN = Regex("""signatureTimestamp['":\s]+(\d+)""")

    private val SIG_FUNCTION_PATTERNS = listOf(
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)"""),
        Regex("""\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\bm=([a-zA-Z0-9$]{2,})\(decodeURIComponent\(h\.s\)\)"""),
    )

    private val N_FUNCTION_PATTERNS = listOf(
        Regex("""\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)"""),
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)"""),
    )

    /** Extracts the player hash from a jsUrl-carrying blob (e.g. the iframe_api response), or
     * falls back to an MD5-of-first-10000-bytes fingerprint of the player.js itself when none of
     * the URL-shaped patterns match - a stable-enough fallback key for config aliasing. */
    fun extractPlayerHash(source: String): String {
        for (pattern in PLAYER_HASH_PATTERNS) {
            pattern.find(source)?.let { return it.groupValues[1] }
        }
        val digest = MessageDigest.getInstance("MD5").digest(source.take(10000).toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    fun extractSignatureTimestamp(playerJs: String, context: Context, playerHash: String): Int? {
        SIGNATURE_TIMESTAMP_PATTERN.find(playerJs)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return YtCipherConfigStore.get(context, playerHash)?.signatureTimestamp
    }

    fun extractSigInfo(playerJs: String, context: Context, playerHash: String): SigCipherInfo? {
        YtCipherConfigStore.get(context, playerHash)?.let { config ->
            return SigCipherInfo(name = "_config_sig", isFromConfig = true, jsExpression = config.sigExpression)
        }
        for (pattern in SIG_FUNCTION_PATTERNS) {
            val match = pattern.find(playerJs) ?: continue
            val name = match.groupValues[1]
            val constArg = match.groupValues.getOrNull(2)?.toIntOrNull()
            return SigCipherInfo(name = name, constantArg = constArg, isFromConfig = false)
        }
        return null
    }

    fun extractNInfo(playerJs: String, context: Context, playerHash: String): NTransformInfo? {
        YtCipherConfigStore.get(context, playerHash)?.let { config ->
            return NTransformInfo(
                name = "_config_n",
                isFromConfig = true,
                jsExpression = YtCipherConfigStore.buildNTransformExpression(config.nClassName)
            )
        }
        for ((index, pattern) in N_FUNCTION_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs) ?: continue
            return when (index) {
                0 -> NTransformInfo(name = match.groupValues[1], arrayIndex = match.groupValues[2].toIntOrNull())
                else -> NTransformInfo(name = match.groupValues[2], arrayIndex = match.groupValues[3].toIntOrNull())
            }
        }
        return null
    }
}
