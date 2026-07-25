package com.example.ytcipher

import android.content.Context
import android.util.Log
import com.example.YtHttpClients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject

private const val TAG = "YtCipherConfigStore"

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
 * On top of the bundled table, [get] opportunistically kicks off a background refresh (see
 * [maybeTriggerBackgroundRefresh]) that fetches the same-shaped table straight from zemer-cipher's
 * own repo and overlays it (remote wins on a hash collision) - the bundled table is frozen at this
 * app build's compile time, but zemer-cipher's upstream table keeps gaining entries for new
 * player.js rotations. A refresh is only ever attempted at most once per [REFRESH_TTL_MS] (6h) or,
 * after a failed attempt, [FAILED_REFRESH_COOLDOWN_MS] (5min) - and never blocks the caller that
 * triggered it: a miss on this exact call still falls back to the regex heuristics in
 * [YtCipherFunctionExtractor] the same as before, and only a *later* call benefits from whatever
 * the refresh found.
 *
 * Only a bare class name ([PlayerCipherConfig.nClassName]) and a tightly-shaped call expression
 * ([PlayerCipherConfig.sigExpression]) are ever read from either the bundled or the remote file -
 * every value is validated against a fixed regex before use (see [SIG_EXPRESSION_PATTERN]/
 * [N_CLASS_NAME_PATTERN]) since both eventually get evaluated as JavaScript inside
 * [YtCipherWebView]; a malformed or malicious entry (bundled or remote) can't inject arbitrary JS,
 * only get silently skipped.
 */
object YtCipherConfigStore {
    private const val ASSET_PATH = "yt_cipher_player_configs.json"
    private const val SUPPORTED_SCHEMA_VERSION = 1

    private const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    private const val REFRESH_TTL_MS = 6 * 60 * 60 * 1000L
    private const val FAILED_REFRESH_COOLDOWN_MS = 5 * 60 * 1000L

    private val HASH_PATTERN = Regex("""^[a-f0-9]{8}$""")
    private val SIG_EXPRESSION_PATTERN = Regex("""^[A-Za-z0-9${'$'}_]{1,8}\(\d+,\d+,INPUT\)$""")
    private val N_CLASS_NAME_PATTERN = Regex("""^[A-Za-z0-9${'$'}_]{1,8}$""")

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshGate = RefreshCooldownGate(REFRESH_TTL_MS, FAILED_REFRESH_COOLDOWN_MS)

    @Volatile
    private var configsByHash: Map<String, PlayerCipherConfig>? = null

    /** Looks up the verified recipe for [playerHash], loading + validating the bundled asset on
     * first use, and opportunistically overlaid with a remote-refreshed table when one has been
     * fetched (see class doc). Returns null if the asset is missing/malformed (fails safe: callers
     * fall back to regex heuristics) or if this specific hash isn't in the table. */
    fun get(context: Context, playerHash: String): PlayerCipherConfig? {
        val config = loadIfNeeded(context)[playerHash]
        if (config == null) maybeTriggerBackgroundRefresh(context)
        return config
    }

    private fun loadIfNeeded(context: Context): Map<String, PlayerCipherConfig> {
        configsByHash?.let { return it }
        synchronized(this) {
            configsByHash?.let { return it }
            val loaded = runCatching { parseAssetConfigs(context) }.getOrDefault(emptyMap())
            configsByHash = loaded
            return loaded
        }
    }

    private fun maybeTriggerBackgroundRefresh(context: Context) {
        if (!refreshGate.tryAcquire()) return
        val appContext = context.applicationContext
        refreshScope.launch {
            val remote = runCatching { fetchRemoteConfigs() }
                .onFailure { Log.w(TAG, "remote cipher config refresh failed", it) }
                .getOrNull()
            if (remote.isNullOrEmpty()) return@launch

            synchronized(this@YtCipherConfigStore) {
                val bundled = configsByHash ?: runCatching { parseAssetConfigs(appContext) }.getOrDefault(emptyMap())
                // Remote wins on a hash collision - it's the freshest known-good recipe for that
                // player.js generation.
                configsByHash = bundled + remote
            }
            refreshGate.onSuccess()
            Log.d(TAG, "merged ${remote.size} remote cipher config entries")
        }
    }

    private fun fetchRemoteConfigs(): Map<String, PlayerCipherConfig> {
        val request = Request.Builder().url(REMOTE_CONFIG_URL).build()
        val body = YtHttpClients.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            response.body?.string()
        } ?: return emptyMap()
        return parseConfigsJson(body)
    }

    private fun parseAssetConfigs(context: Context): Map<String, PlayerCipherConfig> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return parseConfigsJson(text)
    }

    /** Shared, schema-validating parse for both the bundled asset and the remote-fetched table -
     * same shape, same trust boundary (both end up evaluated as JS, see class doc). Internal
     * (rather than private) so its validation logic is directly unit-testable. */
    internal fun parseConfigsJson(text: String): Map<String, PlayerCipherConfig> {
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
