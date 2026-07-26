package com.example.ytcipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [YtCipherConfigStore.parseConfigsJson] is the schema/trust boundary described in the class doc:
 * every entry (bundled or remote-fetched) is validated against a fixed regex before ever being
 * evaluated as JavaScript, so a malformed or malicious entry gets silently skipped rather than
 * injecting arbitrary JS. These tests exercise that validation directly.
 *
 * Runs under Robolectric (rather than plain JUnit) solely because it exercises real
 * `org.json.JSONObject` parsing - the plain android.jar on the unit-test classpath stubs that out.
 */
@RunWith(RobolectricTestRunner::class)
class YtCipherConfigStoreParseTest {

    private fun jsonWithOnePlayer(hash: String = "abc12345", entry: String): String = """
        {
          "schemaVersion": 1,
          "players": { "$hash": $entry }
        }
    """.trimIndent()

    @Test
    fun `a well-formed entry parses`() {
        val json = jsonWithOnePlayer(entry = """{"sig":"ab(1,2,INPUT)","nClass":"Xy","sts":12345}""")
        val result = YtCipherConfigStore.parseConfigsJson(json)
        assertEquals(
            PlayerCipherConfig(sigExpression = "ab(1,2,INPUT)", nClassName = "Xy", signatureTimestamp = 12345),
            result["abc12345"]
        )
    }

    @Test
    fun `an unsupported schema version yields nothing`() {
        val json = """{"schemaVersion": 2, "players": {}}"""
        assertTrue(YtCipherConfigStore.parseConfigsJson(json).isEmpty())
    }

    @Test
    fun `missing players object yields nothing`() {
        val json = """{"schemaVersion": 1}"""
        assertTrue(YtCipherConfigStore.parseConfigsJson(json).isEmpty())
    }

    @Test(expected = org.json.JSONException::class)
    fun `malformed json throws - callers are the ones responsible for the fails-safe fallback`() {
        // parseConfigsJson itself doesn't swallow parse errors; loadIfNeeded()/fetchRemoteConfigs()
        // wrap calls to it in runCatching { }.getOrDefault(emptyMap()) instead. Asserting the throw
        // here documents where that safety net actually lives.
        YtCipherConfigStore.parseConfigsJson("not json at all")
    }

    @Test
    fun `a hash not matching the 8-hex-char pattern is skipped`() {
        val json = jsonWithOnePlayer(hash = "not-a-hash", entry = """{"sig":"ab(1,2,INPUT)","nClass":"Xy","sts":1}""")
        assertTrue(YtCipherConfigStore.parseConfigsJson(json).isEmpty())
    }

    @Test
    fun `a sig expression that isn't a tight function-call shape is rejected`() {
        // This is the actual security boundary from the class doc - a sig value must match
        // SIG_EXPRESSION_PATTERN or it never reaches YtCipherWebView's JS evaluation.
        val malicious = """{"sig":"a(1,2,INPUT);alert(1)","nClass":"Xy","sts":1}"""
        val json = jsonWithOnePlayer(entry = malicious)
        assertNull(YtCipherConfigStore.parseConfigsJson(json)["abc12345"])
    }

    @Test
    fun `an nClass that isn't a bare identifier is rejected`() {
        val malicious = """{"sig":"ab(1,2,INPUT)","nClass":"Xy(1)","sts":1}"""
        val json = jsonWithOnePlayer(entry = malicious)
        assertNull(YtCipherConfigStore.parseConfigsJson(json)["abc12345"])
    }

    @Test
    fun `a non-positive signatureTimestamp is rejected`() {
        val json = jsonWithOnePlayer(entry = """{"sig":"ab(1,2,INPUT)","nClass":"Xy","sts":0}""")
        assertTrue(YtCipherConfigStore.parseConfigsJson(json).isEmpty())
    }

    @Test
    fun `aliases matching the hash pattern map to the same config`() {
        val entry = """{"sig":"ab(1,2,INPUT)","nClass":"Xy","sts":1,"aliases":["def45678","not-a-hash"]}"""
        val json = jsonWithOnePlayer(entry = entry)
        val result = YtCipherConfigStore.parseConfigsJson(json)

        val expected = PlayerCipherConfig(sigExpression = "ab(1,2,INPUT)", nClassName = "Xy", signatureTimestamp = 1)
        assertEquals(expected, result["abc12345"])
        assertEquals(expected, result["def45678"])
        // The malformed alias must not have been let in under a different key.
        assertNull(result["not-a-hash"])
        assertEquals(2, result.size)
    }
}
