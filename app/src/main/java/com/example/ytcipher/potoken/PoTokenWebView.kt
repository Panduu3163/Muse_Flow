package com.example.ytcipher.potoken

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PoToken"

/**
 * Runs Google's BotGuard attestation challenge in a real, headless WebView - created here, never
 * attached to any view hierarchy, so nothing is ever visible - to mint YouTube PoTokens. The
 * WebView only ever executes `po_token_botguard.html`'s orchestration JS plus whatever BotGuard
 * VM code that challenge embeds; it makes no network requests itself (`blockNetworkLoads`), the
 * two BotGuard HTTP round trips (Create, GenerateIT) happen from Kotlin via OkHttp, with their
 * responses injected back into the page via `evaluateJavascript`.
 *
 * Approach follows zemer-cipher's PoTokenWebView (GPL-3.0,
 * https://github.com/ZemerTeam/zemer-cipher), itself based on BgUtils
 * (https://github.com/LuanRT/BgUtils, MIT) - independently written for this module, not a
 * line-for-line port.
 */
class PoTokenWebView private constructor(
    context: Context,
    private var initContinuation: Continuation<PoTokenWebView>?,
) {
    private val webView = WebView(context)
    private val scope = MainScope()
    private val initResumed = AtomicBoolean(false)

    @Volatile private var closed = false

    /** Set once the renderer died or a generate() call timed out - callers must discard this
     * instance and create a fresh one rather than keep using it. */
    @Volatile
    var isDead: Boolean = false
        private set

    private val pendingTokenRequests = ConcurrentHashMap<String, Continuation<String>>()
    private val requestCounter = AtomicLong()
    private val exceptionHandler = CoroutineExceptionHandler { _, t -> failInitialization(t) }
    private lateinit var expirationInstant: Instant

    init {
        val settings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        settings.userAgentString = USER_AGENT
        // Every network call this module makes (BotGuard Create/GenerateIT) happens from Kotlin
        // via OkHttp - the page itself never needs to reach the network.
        settings.blockNetworkLoads = true

        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, "JS: $text")
                    ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, "JS: $text")
                    else -> Log.d(TAG, "JS: $text")
                }
                if ("Uncaught" in text) {
                    val detail = "\"$text\" (${message.sourceId()}:${message.lineNumber()})"
                    if (initResumed.get()) {
                        // Post-init: our own static HTML already ran fine, so this is Google's
                        // remotely-served BotGuard JS failing - transient, not a broken engine.
                        isDead = true
                        val exception = PoTokenException(detail)
                        close()
                        failAllPending(exception)
                    } else {
                        failInitialization(BadWebViewException(detail))
                    }
                }
                return super.onConsoleMessage(message)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                isDead = true
                val exception = PoTokenException(
                    "PoToken WebView render process gone (didCrash=${runCatching { detail.didCrash() }.getOrNull()})"
                )
                failInitialization(exception)
                failAllPending(exception)
                return true // consume - don't let the framework kill the app process
            }
        }
    }

    private fun loadHtmlAndStartChallenge() {
        scope.launch(exceptionHandler) {
            val html = withContext(Dispatchers.IO) {
                webView.context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            }
            val augmented = html.replaceFirst("</script>", "\n$JS_INTERFACE.startChallenge()</script>")
            webView.loadDataWithBaseURL("https://www.youtube.com", augmented, "text/html", "utf-8", null)
        }
    }

    @JavascriptInterface
    fun startChallenge() {
        requestBotguard(CREATE_URL, "[ \"$REQUEST_KEY\" ]") { responseBody ->
            val challenge = PoTokenJsCodec.parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $challenge
                    runBotGuard(data).then(function (result) {
                        window.__webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onInitError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onInitError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    @JavascriptInterface
    fun onInitError(error: String) {
        failInitialization(poTokenExceptionFor(error))
    }

    @JavascriptInterface
    fun onBotguardResult(botguardResponse: String) {
        requestBotguard(GENERATE_IT_URL, "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]") { responseBody ->
            try {
                val (integrityTokenLiteral, lifetimeSeconds) = PoTokenJsCodec.parseIntegrityTokenData(responseBody)
                // Ten minutes of margin so a mint doesn't race the token's real expiry.
                expirationInstant = Instant.now().plusSeconds(lifetimeSeconds).minus(10, ChronoUnit.MINUTES)

                webView.evaluateJavascript(
                    """try {
                        createPoTokenMinter(window.__webPoSignalOutput, $integrityTokenLiteral).then(function() {
                            $JS_INTERFACE.onMinterReady()
                        }).catch(function(error) {
                            $JS_INTERFACE.onInitError(error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onInitError(error + "\n" + error.stack)
                    }""",
                    null
                )
            } catch (e: Exception) {
                failInitialization(PoTokenException("Failed to parse integrity token data: ${e.message}"))
            }
        }
    }

    @JavascriptInterface
    fun onMinterReady() {
        if (initResumed.compareAndSet(false, true)) {
            initContinuation?.let { runCatching { it.resume(this) } }
            initContinuation = null
        }
    }

    /** Mints a PoToken bound to [identifier] (a video id, or a session's visitorData for the
     * once-per-session token). */
    suspend fun generatePoToken(identifier: String): String {
        if (isDead || closed) {
            throw PoTokenException("PoToken WebView is dead/closed - instance must be recreated")
        }
        // Keyed per-call, not by the raw identifier: two concurrent calls for the same identifier
        // must not clobber each other's pending continuation.
        val requestKey = "$identifier#${requestCounter.incrementAndGet()}"
        return try {
            withTimeout(GENERATE_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        pendingTokenRequests[requestKey] = cont
                        webView.evaluateJavascript(
                            """(function() {
                                var requestKey = "$requestKey"
                                try {
                                    var identifierBytes = ${PoTokenJsCodec.stringToJsUint8Array(identifier)}
                                    obtainPoToken(identifierBytes).then(function(tokenBytes) {
                                        $JS_INTERFACE.onTokenResult(requestKey, tokenBytes.join(","))
                                    }).catch(function(error) {
                                        $JS_INTERFACE.onTokenError(requestKey, error + "\n" + (error.stack || ''))
                                    })
                                } catch (error) {
                                    $JS_INTERFACE.onTokenError(requestKey, error + "\n" + error.stack)
                                }
                            })()""",
                            null
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            isDead = true
            pendingTokenRequests.remove(requestKey)
            throw PoTokenException("PoToken generation timed out after ${GENERATE_TIMEOUT_MS}ms")
        }
    }

    @JavascriptInterface
    fun onTokenResult(requestKey: String, tokenBytesCsv: String) {
        val cont = pendingTokenRequests.remove(requestKey) ?: return
        val token = try {
            PoTokenJsCodec.bytesCsvToPoToken(tokenBytesCsv)
        } catch (t: Throwable) {
            runCatching { cont.resumeWithException(t) }
            return
        }
        runCatching { cont.resume(token) }
    }

    @JavascriptInterface
    fun onTokenError(requestKey: String, error: String) {
        pendingTokenRequests.remove(requestKey)?.let { cont ->
            runCatching { cont.resumeWithException(PoTokenException(error)) }
        }
    }

    val isExpired: Boolean get() = Instant.now().isAfter(expirationInstant)

    private fun requestBotguard(url: String, requestBodyJson: String, onSuccess: (String) -> Unit) {
        scope.launch(exceptionHandler) {
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toRequestBody())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json+protobuf")
                .header("x-goog-api-key", GOOGLE_API_KEY)
                .header("x-user-agent", "grpc-web-javascript/0.1")
                .build()

            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    if (response.code != 200) null else response.body?.string()
                }
            }
            if (body.isNullOrEmpty()) {
                failInitialization(PoTokenException("Invalid BotGuard response from $url"))
            } else {
                onSuccess(body)
            }
        }
    }

    private fun failInitialization(error: Throwable) {
        close()
        if (initResumed.compareAndSet(false, true)) {
            initContinuation?.let { runCatching { it.resumeWithException(error) } }
            initContinuation = null
        }
    }

    private fun failAllPending(error: Throwable) {
        val pending = pendingTokenRequests.toMap()
        pendingTokenRequests.clear()
        pending.values.forEach { cont -> runCatching { cont.resumeWithException(error) } }
    }

    /** Tears the WebView down. Safe to call from any thread - the actual `WebView.destroy()`
     * call is always posted to the main thread regardless of the caller's, same requirement
     * `YtCipherWebView.close()` has. */
    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyWebView()
        } else {
            Handler(Looper.getMainLooper()).post { destroyWebView() }
        }
    }

    private fun destroyWebView() {
        runCatching {
            webView.loadUrl("about:blank")
            webView.destroy()
        }.onFailure { Log.w(TAG, "PoToken WebView teardown threw: $it") }
    }

    companion object {
        private const val ASSET_PATH = "po_token_botguard.html"
        private const val JS_INTERFACE = "PoTokenBridge"
        private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"

        // Public web-client constants embedded in every youtube.com page's own client-side JS -
        // not secrets, and not specific to any one open-source project.
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        // Init does two network round trips (Create, GenerateIT) plus JS execution; a healthy
        // cold start is a few seconds, this is a safety net for a wedged/dead renderer.
        private const val INIT_TIMEOUT_MS = 45_000L

        // A live renderer mints a token in well under a second.
        private const val GENERATE_TIMEOUT_MS = 15_000L

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        suspend fun create(context: Context): PoTokenWebView {
            var created: PoTokenWebView? = null
            return try {
                withTimeout(INIT_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val instance = PoTokenWebView(context, cont)
                            created = instance
                            instance.loadHtmlAndStartChallenge()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                created?.let { instance ->
                    instance.initResumed.set(true)
                    instance.close()
                }
                throw PoTokenException("PoToken WebView init timed out after ${INIT_TIMEOUT_MS}ms")
            }
        }
    }
}
