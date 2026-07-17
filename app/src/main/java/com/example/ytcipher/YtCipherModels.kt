package com.example.ytcipher

/**
 * A located signature-decipher recipe for one player.js build. Either [jsExpression] (a verified
 * call expression from the bundled config table, e.g. `"OI(34,7320,INPUT)"`, with `INPUT` standing
 * in for the ciphered signature argument) or [name] + [constantArg] (a bare function name found by
 * regex heuristics against the raw player.js, called as `name(constantArg, sig)` or `name(sig)`).
 */
data class SigCipherInfo(
    val name: String,
    val constantArg: Int? = null,
    val isFromConfig: Boolean = false,
    val jsExpression: String? = null
)

/**
 * A located n-parameter transform recipe. YouTube's n-transform throttles playback unless the `n`
 * query parameter is run through this function first. Either [jsExpression] (built from a verified
 * config's class name - see [YtCipherConfigStore.buildNTransformExpression]) or [name] +
 * [arrayIndex] (a bare function - possibly array-indexed - found by regex heuristics).
 */
data class NTransformInfo(
    val name: String,
    val arrayIndex: Int? = null,
    val isFromConfig: Boolean = false,
    val jsExpression: String? = null
)

/** A verified-working cipher recipe for one specific player.js build, keyed by its 8-hex-char
 * hash. YouTube rotates player.js often enough, and with heavy enough per-generation obfuscation,
 * that plain regex heuristics against the raw JS routinely fail against the current live player -
 * a bundled table of already-verified recipes (refreshed periodically) is what actually keeps
 * deciphering working, with regex heuristics only as a fallback for a hash the table doesn't
 * cover yet. */
data class PlayerCipherConfig(
    val sigExpression: String,
    val nClassName: String,
    val signatureTimestamp: Int
)
