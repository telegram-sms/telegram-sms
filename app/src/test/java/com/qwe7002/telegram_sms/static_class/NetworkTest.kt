package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tests for the custom Bot API server URL logic in [Network].
 *
 * The bare token + method here are placeholders; the only network call (the live
 * getMe check) reads a real token from the TELEGRAM_TEST_BOT_TOKEN environment
 * variable and is skipped when it is absent, so no secret is committed.
 */
class NetworkTest {

    private val token = "123456:ABC-DEF"

    // --- normalizeApiBase ----------------------------------------------------

    @Test
    fun normalize_plainHost_getsHttpsScheme() {
        assertEquals("https://api.telegram.org", Network.normalizeApiBase("api.telegram.org"))
    }

    @Test
    fun normalize_keepsExistingHttpsScheme_noDoubleScheme() {
        assertEquals(
            "https://botapi.qwe7002.dev",
            Network.normalizeApiBase("https://botapi.qwe7002.dev")
        )
    }

    @Test
    fun normalize_keepsExplicitHttpScheme() {
        assertEquals(
            "http://192.168.1.10:8081",
            Network.normalizeApiBase("http://192.168.1.10:8081")
        )
    }

    @Test
    fun normalize_stripsTrailingSlash() {
        assertEquals(
            "https://botapi.qwe7002.dev",
            Network.normalizeApiBase("botapi.qwe7002.dev/")
        )
    }

    @Test
    fun normalize_stripsTrailingSlash_withScheme() {
        assertEquals(
            "https://botapi.qwe7002.dev",
            Network.normalizeApiBase("https://botapi.qwe7002.dev/")
        )
    }

    @Test
    fun normalize_trimsWhitespace() {
        assertEquals(
            "https://api.telegram.org",
            Network.normalizeApiBase("  api.telegram.org  ")
        )
    }

    @Test
    fun normalize_keepsHostPort() {
        assertEquals(
            "https://botapi.qwe7002.dev:8443",
            Network.normalizeApiBase("botapi.qwe7002.dev:8443")
        )
    }

    @Test
    fun normalize_blankFallsBackToCloud() {
        assertEquals("https://api.telegram.org", Network.normalizeApiBase(""))
        assertEquals("https://api.telegram.org", Network.normalizeApiBase("   "))
    }

    // --- buildUrl ------------------------------------------------------------

    @Test
    fun buildUrl_default() {
        assertEquals(
            "https://api.telegram.org/bot$token/getMe",
            Network.buildUrl("api.telegram.org", token, "getMe")
        )
    }

    // Regression: a scheme-prefixed custom address used to produce https://https://...
    @Test
    fun buildUrl_schemePrefixedCustomServer_noDoubleScheme() {
        assertEquals(
            "https://botapi.qwe7002.dev/bot$token/sendMessage",
            Network.buildUrl("https://botapi.qwe7002.dev", token, "sendMessage")
        )
    }

    // Regression: a trailing slash used to produce a double slash before /bot.
    @Test
    fun buildUrl_trailingSlash_noDoubleSlash() {
        assertEquals(
            "https://botapi.qwe7002.dev/bot$token/getUpdates",
            Network.buildUrl("botapi.qwe7002.dev/", token, "getUpdates")
        )
    }

    // --- migrationForApiChange ----------------------------------------------

    @Test
    fun migration_cloudToCustom_logsOutOfCloud() {
        val m = Network.migrationForApiChange("api.telegram.org", "botapi.qwe7002.dev")
        assertEquals("https://api.telegram.org", m?.base)
        assertEquals("logOut", m?.method)
    }

    @Test
    fun migration_customToCloud_logsOutOfCustom() {
        val m = Network.migrationForApiChange("botapi.qwe7002.dev", "api.telegram.org")
        assertEquals("https://botapi.qwe7002.dev", m?.base)
        assertEquals("logOut", m?.method)
    }

    // Between two local servers the bot instance must be closed, not logged out.
    @Test
    fun migration_customToOtherCustom_closesOldCustom() {
        val m = Network.migrationForApiChange("a.example.com", "b.example.com")
        assertEquals("https://a.example.com", m?.base)
        assertEquals("close", m?.method)
    }

    @Test
    fun migration_sameServer_returnsNull() {
        assertNull(Network.migrationForApiChange("botapi.qwe7002.dev", "botapi.qwe7002.dev"))
    }

    // Cosmetic edits (adding a scheme / trailing slash) must not trigger a migration.
    @Test
    fun migration_sameServerAfterNormalization_returnsNull() {
        assertNull(Network.migrationForApiChange("api.telegram.org", "https://api.telegram.org/"))
    }

    // --- live validation (opt-in, reads token from the environment) ----------

    @Test
    fun liveGetMe_throughCustomServer_returnsOk() {
        val realToken = System.getenv("TELEGRAM_TEST_BOT_TOKEN")
            ?: System.getProperty("telegram.test.bot.token")
        assumeFalse(
            "set TELEGRAM_TEST_BOT_TOKEN to run the live getMe check",
            realToken.isNullOrBlank()
        )
        val apiAddress = System.getenv("TELEGRAM_TEST_API_ADDRESS")
            ?: System.getProperty("telegram.test.api.address")
            ?: "https://botapi.qwe7002.dev"

        val url = Network.buildUrl(apiAddress, realToken!!, "getMe")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        conn.disconnect()

        assertEquals("HTTP $code, body=$body", 200, code)
        assertTrue("expected ok:true from $apiAddress, got: $body", body.contains("\"ok\":true"))
    }
}
