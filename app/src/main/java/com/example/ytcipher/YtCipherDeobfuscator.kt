package com.example.ytcipher

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "YtCipher"

/**
 * Public entry point for this standalone module: takes a raw, ciphered YouTube stream URL
 * (either a `signatureCipher` query string carrying an `s=`/`sp=`/`url=` triple, or a plain URL
 * with an unthrottled `n=` parameter) and returns the deciphered, directly-playable URL.
 *
 * Not wired into any real playback path yet - see the isolated test in `ProviderTestScreen`.
 * Pipeline: [YtPlayerJsFetcher] downloads the current player.js -> [YtCipherFunctionExtractor]
 * locates (via the verified [YtCipherConfigStore] table, falling back to regex heuristics) the
 * signature-decipher and n-transform recipes for that specific build -> [YtCipherWebView]
 * actually executes them by loading player.js into a real, headless WebView.
 */
object YtCipherDeobfuscator {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    // YtCipherWebView has single-shot continuation slots - concurrent calls would clobber each
    // other's pending request, so every decipher/transform goes through this one at a time.
    private val mutex = Mutex()

    private var webView: YtCipherWebView? = null
    private var webViewPlayerHash: String? = null

    /**
     * Deciphers a `signatureCipher` query string (the `sp=`/`s=`/`url=` triple YouTube's WEB/
     * WEB_REMIX clients embed in place of a direct `url` for any format that needs a signature)
     * into the final, playable URL. Returns null if any step fails.
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val params = parseQueryParams(signatureCipher)
            val obfuscatedSig = params["s"] ?: run { Log.w(TAG, "no 's' param in signatureCipher"); return@withLock null }
            val sigParamName = params["sp"] ?: "signature"
            val baseUrl = params["url"] ?: run { Log.w(TAG, "no 'url' param in signatureCipher"); return@withLock null }

            val cipherWebView = getOrCreateWebView() ?: run { Log.w(TAG, "getOrCreateWebView() returned null"); return@withLock null }
            val deobfuscatedSig = runCatching { cipherWebView.deobfuscateSignature(obfuscatedSig) }
                .onFailure { Log.e(TAG, "deobfuscateSignature threw", it) }
                .getOrNull()
                ?: run { Log.w(TAG, "deobfuscateSignature returned null"); return@withLock null }

            Log.d(TAG, "deobfuscated sig: ${deobfuscatedSig.take(30)}...")
            val separator = if ("?" in baseUrl) "&" else "?"
            "$baseUrl$separator$sigParamName=${Uri.encode(deobfuscatedSig)}"
        }
    }

    /** Transforms the `n` query parameter of [url] in place (throttling avoidance), returning
     * [url] unchanged if there's no `n` param or the transform can't be performed. */
    suspend fun transformNParamInUrl(url: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val nMatch = Regex("""[?&]n=([^&]+)""").find(url) ?: return@withLock url
            val nValue = Uri.decode(nMatch.groupValues[1])

            val cipherWebView = getOrCreateWebView() ?: return@withLock url
            val transformed = runCatching { cipherWebView.transformN(nValue) }.getOrDefault(nValue)

            url.replaceFirst(Regex("""([?&])n=[^&]+"""), "$1n=${Uri.encode(transformed)}")
        }
    }

    private suspend fun getOrCreateWebView(): YtCipherWebView? {
        val playerJs = YtPlayerJsFetcher.getPlayerJs() ?: run { Log.w(TAG, "getPlayerJs() returned null"); return null }
        Log.d(TAG, "playerJs hash=${playerJs.hash} size=${playerJs.source.length}")

        if (webView != null && webViewPlayerHash == playerJs.hash) return webView

        // WebView.destroy() (called by close()) must run on the main thread like every other
        // WebView method. This function itself now always runs on Dispatchers.IO (see the public
        // entry points above), so without this explicit switch back, close() would run on IO and
        // throw the first time a player.js rotation mid-session actually triggers a rebuild.
        webView?.let { stale -> withContext(Dispatchers.Main) { stale.close() } }
        webView = null

        val sigInfo = YtCipherFunctionExtractor.extractSigInfo(playerJs.source, appContext, playerJs.hash)
        val nInfo = YtCipherFunctionExtractor.extractNInfo(playerJs.source, appContext, playerJs.hash)
        Log.d(TAG, "sigInfo=$sigInfo nInfo=$nInfo")
        if (sigInfo == null && nInfo == null) {
            Log.w(TAG, "both sigInfo and nInfo are null - nothing to build a WebView with")
            return null
        }

        val created = runCatching {
            YtCipherWebView.create(appContext, playerJs.source, sigInfo, nInfo)
        }.onFailure { Log.e(TAG, "YtCipherWebView.create threw", it) }.getOrNull() ?: return null

        webView = created
        webViewPlayerHash = playerJs.hash
        return created
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                result[Uri.decode(pair.substring(0, idx))] = Uri.decode(pair.substring(idx + 1))
            }
        }
        return result
    }
}
