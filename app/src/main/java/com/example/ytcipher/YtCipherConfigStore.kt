package com.example.ytcipher

import android.content.Context
import org.json.JSONObject

/**
 * Loads the bundled `assets/yt_cipher_player_configs.json` table: one verified sig/n-transform
 * recipe per YouTube player.js generation (keyed by an 8-hex-char hash extracted from the
 * player.js URL), plus alias hashes for builds that are byte-identical apart from a cosmetic
 * rebuild. Table shape and the two source-file provenance/attribution notes below - this data
 * table itself, not the surrounding module, originates from the zemer-cipher project (GPL-3.0):
 * https://github.com/ZemerTeam/zemer-cipher, `library/src/main/assets/player_configs.json` -
 * bundled here as of this module's creation date; MuseFlow adopts GPL-3.0 (see repo root
 * LICENSE) specifically so this table can be used and kept up to date.
 *
 * Only a bare class name ([PlayerCipherConfig.nClassName]) and a tightly-shaped call expression
 * ([PlayerCipherConfig.sigExpression]) are ever read from this file - every value is validated
 * against a fixed regex before use (see [SIG_EXPRESSION_PATTERN]/[N_CLASS_NAME_PATTERN]) since
 * both eventually get evaluated as JavaScript inside [YtCipherWebView]; a malformed or malicious
 * entry can't inject arbitrary JS, only get silently skipped.
 */
object YtCipherConfigStore {
    private const val ASSET_PATH = "yt_cipher_player_configs.json"
    private const val SUPPORTED_SCHEMA_VERSION = 1

    private val HASH_PATTERN = Regex("""^[a-f0-9]{8}$""")
    private val SIG_EXPRESSION_PATTERN = Regex("""^[A-Za-z0-9${'$'}_]{1,8}\(\d+,\d+,INPUT\)$""")
    private val N_CLASS_NAME_PATTERN = Regex("""^[A-Za-z0-9${'$'}_]{1,8}$""")

    @Volatile
    private var configsByHash: Map<String, PlayerCipherConfig>? = null

    /** Looks up the verified recipe for [playerHash], loading + validating the bundled asset on
     * first use. Returns null if the asset is missing/malformed (fails safe: callers fall back
     * to regex heuristics) or if this specific hash isn't in the table. */
    fun get(context: Context, playerHash: String): PlayerCipherConfig? =
        loadIfNeeded(context)[playerHash]

    private fun loadIfNeeded(context: Context): Map<String, PlayerCipherConfig> {
        configsByHash?.let { return it }
        synchronized(this) {
            configsByHash?.let { return it }
            val loaded = runCatching { parseAsset(context) }.getOrDefault(emptyMap())
            configsByHash = loaded
            return loaded
        }
    }

    private fun parseAsset(context: Context): Map<String, PlayerCipherConfig> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        if (root.optInt("schemaVersion", -1) != SUPPORTED_SCHEMA_VERSION) return emptyMap()

        val players = root.optJSONObject("players") ?: return emptyMap()
        val result = mutableMapOf<String, PlayerCipherConfig>()

        val hashes = players.keys()
        while (hashes.hasNext()) {
            val hash = hashes.next()
            if (!HASH_PATTERN.matches(hash)) continue
            val entry = players.optJSONObject(hash) ?: continue

            val sig = entry.optString("sig")
            if (!SIG_EXPRESSION_PATTERN.matches(sig)) continue

            val nClass = entry.optString("nClass")
            if (!N_CLASS_NAME_PATTERN.matches(nClass)) continue

            val sts = entry.optInt("sts", -1)
            if (sts <= 0) continue

            val config = PlayerCipherConfig(sigExpression = sig, nClassName = nClass, signatureTimestamp = sts)
            result[hash] = config

            val aliases = entry.optJSONArray("aliases")
            if (aliases != null) {
                for (i in 0 until aliases.length()) {
                    val alias = aliases.optString(i)
                    if (HASH_PATTERN.matches(alias)) result[alias] = config
                }
            }
        }
        return result
    }

    /**
     * Builds the n-transform IIFE for a config's [PlayerCipherConfig.nClassName]. YouTube hides
     * the actual n-transform inside a URL-parsing class's `get()` method: constructing one with a
     * throwaway `videoplayback` URL carrying `n=<value>` and reading back the `n` param yields the
     * transformed value as a side effect. Built locally from a fixed template - the config file
     * only ever supplies the bare class name, never a JS expression.
     */
    fun buildNTransformExpression(nClassName: String): String =
        "(function(n){try{var u=new g.$nClassName('https://x.googlevideo.com/videoplayback?n='+n,true);" +
            "var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)"
}
