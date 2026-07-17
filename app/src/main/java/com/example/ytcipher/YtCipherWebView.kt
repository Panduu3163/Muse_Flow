package com.example.ytcipher

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "YtCipher"

/**
 * Runs the actual signature/n-transform deciphering by loading player.js into a real (headless,
 * network-disabled) [WebView] and calling into it via `evaluateJavascript` - YouTube's player.js
 * changes its internal obfuscation shape often enough that executing the code YouTube itself
 * shipped is far more robust than trying to reimplement its string operations in Kotlin.
 *
 * Approach (fetch → locate → execute in a real JS engine) is the same one zemer-cipher
 * (GPL-3.0, https://github.com/ZemerTeam/zemer-cipher) uses; this is an independent
 * implementation written for this module, not a line-for-line port.
 */
class YtCipherWebView private constructor(
    context: Context,
    private val sigInfo: SigCipherInfo?,
    private val nInfo: NTransformInfo?,
    initContinuation: Continuation<YtCipherWebView>,
) {
    private val webView = WebView(context)
    private var initContinuation: Continuation<YtCipherWebView>? = initContinuation

    // Single-shot slots so a late JS-bridge callback can never resume the wrong (or an already-
    // completed) continuation. Each holds a request id so a stale callback from a superseded
    // call is ignored rather than resuming the next call's continuation with garbage.
    private val sigSlot = RequestSlot<String>()
    private val nSlot = RequestSlot<String>()

    private class RequestSlot<T> {
        private var continuation: Continuation<T>? = null
        private var requestId = 0

        @Synchronized
        fun arm(cont: Continuation<T>): Int {
            continuation = cont
            return ++requestId
        }

        @Synchronized
        fun takeIfCurrent(id: Int): Continuation<T>? =
            if (id == requestId) continuation.also { continuation = null } else null
    }

    @Volatile private var destroyed = false

    init {
        val settings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        // This WebView only ever evaluates a locally-supplied player.js file - it never
        // navigates anywhere over the network, so that access is unneeded and disabled.
        settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, JS_INTERFACE)
    }

    /**
     * Loads the (already export-augmented, see [buildExportAugmentedPlayerJs]) player.js written
     * to [cacheDir] by [create], via a `file://` `<script src>` rather than inlining it into the
     * HTML payload directly. player.js is ~2.8 MB; `loadDataWithBaseURL`'s HTML string is passed
     * to the render process over a single Binder transaction, which silently truncates well
     * before that size (the classic ~1 MB IPC limit) - inlining it looked simpler but actually
     * loaded a truncated, syntactically-broken script, which is why every export function called
     * afterward returned null with no real error to surface.
     */
    private fun loadPlayerJs(cacheDir: File) {
        val html = """
            <!DOCTYPE html><html><head><script src="player.js"
                onload="YtCipherBridge.onPageReady()"
                onerror="YtCipherBridge.onPageError('failed to load player.js from file')">
            </script></head><body></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("file://${cacheDir.absolutePath}/", html, "text/html", "utf-8", null)
    }

    @JavascriptInterface
    fun onPageReady() {
        Log.d(TAG, "onPageReady")
        takeInitContinuation()?.let { cont -> runCatching { cont.resume(this) } }
    }

    @JavascriptInterface
    fun onPageError(error: String) {
        Log.e(TAG, "onPageError: $error")
        takeInitContinuation()?.let { cont ->
            runCatching { cont.resumeWithException(YtCipherException("player.js failed to evaluate: $error")) }
        }
    }

    /** Logged separately from [onPageError] - a sig/n export throwing at CALL time (as opposed to
     * player.js failing to evaluate at LOAD time) happens well after the init continuation is
     * already consumed, so routing it through onPageError would silently do nothing. */
    @JavascriptInterface
    fun onCipherError(where: String, error: String) {
        Log.e(TAG, "onCipherError[$where]: $error")
    }

    @Synchronized
    private fun takeInitContinuation(): Continuation<YtCipherWebView>? =
        initContinuation.also { initContinuation = null }

    suspend fun deobfuscateSignature(obfuscatedSig: String): String {
        val info = sigInfo ?: throw YtCipherException("No signature-decipher info available for this player")
        return try {
            withTimeout(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val requestId = sigSlot.arm(cont)
                        webView.evaluateJavascript("window.__cipherSig('${escapeJs(obfuscatedSig)}')") { rawResult ->
                            deliverSigResult(requestId, rawResult)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw YtCipherException("Signature deobfuscation timed out after ${EVAL_TIMEOUT_MS}ms")
        }
    }

    private fun deliverSigResult(requestId: Int, rawJsonResult: String?) {
        Log.d(TAG, "deliverSigResult raw=$rawJsonResult")
        val cont = sigSlot.takeIfCurrent(requestId) ?: return
        val value = unquoteJsEvalResult(rawJsonResult)
        if (value == null) {
            runCatching { cont.resumeWithException(YtCipherException("Signature function returned null/undefined (raw=$rawJsonResult)")) }
        } else {
            runCatching { cont.resume(value) }
        }
    }

    suspend fun transformN(nValue: String): String {
        val info = nInfo ?: return nValue
        return try {
            withTimeout(EVAL_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val requestId = nSlot.arm(cont)
                        webView.evaluateJavascript("window.__nTransform('${escapeJs(nValue)}')") { rawResult ->
                            deliverNResult(requestId, rawResult, nValue)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            nValue
        }
    }

    private fun deliverNResult(requestId: Int, rawJsonResult: String?, fallback: String) {
        val cont = nSlot.takeIfCurrent(requestId) ?: return
        val value = unquoteJsEvalResult(rawJsonResult) ?: fallback
        runCatching { cont.resume(value) }
    }

    /** `evaluateJavascript`'s callback result is a JSON-encoded string (or the literal "null") -
     * unwrap it back to a plain Kotlin string. */
    private fun unquoteJsEvalResult(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { org.json.JSONTokener(raw).nextValue() as? String }.getOrNull()
    }

    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    fun close() {
        if (destroyed) return
        destroyed = true
        runCatching {
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    companion object {
        private const val JS_INTERFACE = "YtCipherBridge"
        private const val CREATE_TIMEOUT_MS = 30_000L
        private const val EVAL_TIMEOUT_MS = 15_000L

        /**
         * Augments the raw player.js with two small window-level exports - `window.__cipherSig`
         * and `window.__nTransform` - built from [sigInfo]/[nInfo], then signals readiness via
         * the JS bridge. Injected right before player.js's closing `})(_yt_player);` so the
         * exports run inside the same closure and can see its internal function/class names.
         */
        private fun buildExportAugmentedPlayerJs(
            playerJs: String,
            sigInfo: SigCipherInfo?,
            nInfo: NTransformInfo?,
        ): String {
            val sigExport = when {
                sigInfo?.jsExpression != null ->
                    "window.__cipherSig = function(sig) { try { return ${sigInfo.jsExpression.replace("INPUT", "sig")}; } " +
                        "catch(e) { YtCipherBridge.onCipherError('sig', String(e)); return null; } };"
                sigInfo != null -> {
                    val call = if (sigInfo.constantArg != null) {
                        "${sigInfo.name}(${sigInfo.constantArg}, sig)"
                    } else {
                        "${sigInfo.name}(sig)"
                    }
                    "window.__cipherSig = function(sig) { try { return $call; } " +
                        "catch(e) { YtCipherBridge.onCipherError('sig', String(e)); return null; } };"
                }
                else -> "window.__cipherSig = function(sig) { return null; };"
            }

            val nExport = when {
                nInfo?.jsExpression != null ->
                    "window.__nTransform = function(n) { try { return ${nInfo.jsExpression.replace("INPUT", "n")}; } " +
                        "catch(e) { YtCipherBridge.onCipherError('n', String(e)); return n; } };"
                nInfo != null -> {
                    val ref = if (nInfo.arrayIndex != null) "${nInfo.name}[${nInfo.arrayIndex}]" else nInfo.name
                    "window.__nTransform = function(n) { try { return ($ref)(n); } " +
                        "catch(e) { YtCipherBridge.onCipherError('n', String(e)); return n; } };"
                }
                else -> "window.__nTransform = function(n) { return n; };"
            }

            val exportStatement = "; $sigExport $nExport"
            val injectionPoint = "})(_yt_player);"
            val modified = playerJs.replaceFirst(injectionPoint, "$exportStatement $injectionPoint")
            val result = if (modified != playerJs) modified else "$playerJs\n$exportStatement"
            Log.d(TAG, "export injection point found=${modified != playerJs}, augmented size=${result.length}")
            return result
        }

        suspend fun create(
            context: Context,
            playerJs: String,
            sigInfo: SigCipherInfo?,
            nInfo: NTransformInfo?,
        ): YtCipherWebView {
            // Writing ~2.8 MB to disk and loading it as a file:// script (rather than inlining it
            // into the WebView's HTML payload) must run off the main thread.
            val cacheDir = withContext(Dispatchers.IO) {
                val augmentedJs = buildExportAugmentedPlayerJs(playerJs, sigInfo, nInfo)
                val dir = File(context.cacheDir, "ytcipher")
                dir.mkdirs()
                File(dir, "player.js").writeText(augmentedJs)
                dir
            }
            return withTimeout(CREATE_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val instance = YtCipherWebView(context, sigInfo, nInfo, cont)
                        instance.loadPlayerJs(cacheDir)
                    }
                }
            }
        }
    }
}

class YtCipherException(message: String) : Exception(message)
