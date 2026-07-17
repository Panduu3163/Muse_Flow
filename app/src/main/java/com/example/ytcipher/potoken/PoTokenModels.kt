package com.example.ytcipher.potoken

/** A minted PoToken pair for one YouTube session. [playerRequestPoToken] is bound to the
 * session's `visitorData`, minted once and reused across every video in that session (sent in
 * the `/player` request). [streamingDataPoToken] is bound to a specific video id and minted per
 * video - it must be the one appended to the actual stream URL, since googlevideo.com only serves
 * the first ~1 MiB of a stream past that without a video-bound token. */
data class PoTokenResult(
    val playerRequestPoToken: String,
    val streamingDataPoToken: String
)

class PoTokenException(message: String) : Exception(message)

/** Thrown when the system WebView implementation itself can't run the BotGuard challenge (a
 * `SyntaxError` on our own static HTML, before any of Google's remotely-served JS has even run) -
 * distinct from [PoTokenException], which covers ordinary/transient failures once the WebView
 * engine is known to work. */
class BadWebViewException(message: String) : Exception(message)

fun poTokenExceptionFor(jsError: String): Exception =
    if ("SyntaxError" in jsError) BadWebViewException(jsError) else PoTokenException(jsError)
