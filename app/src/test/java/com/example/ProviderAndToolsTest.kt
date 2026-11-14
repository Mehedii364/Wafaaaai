package com.example

import com.example.core.ai.ExternalApiCatalog
import com.example.core.ai.ProviderSeeds
import com.example.core.security.SecureCredentialVault
import com.example.core.tools.UtilityToolsEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProviderAndToolsTest {

    @Test
    fun `external catalog has exactly 75 documented integrations and xpiki is seeded`() {
        assertEquals(75, ExternalApiCatalog.integrations.size)
        val xpiki = ProviderSeeds.defaultProviders().firstOrNull { it.id == "xpiki" }
        assertNotNull(xpiki)
        assertEquals("https://api.xpiki.com/v1", xpiki!!.baseUrl)
        assertEquals("https://docs.xpiki.com/", xpiki.docsUrl)
    }

    @Test
    fun `secret redaction masks bearer and api keys`() {
        val raw = "Failed with Authorization: Bearer sk-1234567890abcdef123456 and key=AIzaSyAbCdEfGhIjKlMnOpQrStUv"
        val redacted = SecureCredentialVault.redactSecretsFromText(raw)
        assertFalse(redacted.contains("sk-1234567890abcdef123456"))
        assertFalse(redacted.contains("AIzaSyAbCdEfGhIjKlMnOpQrStUv"))
        assertTrue(redacted.contains("REDACTED"))
    }

    @Test
    fun `utility tools math, json, password, and unit conversions work accurately`() {
        assertEquals(20.0, UtilityToolsEngine.evaluateMath("(2 + 3) * 4").getOrThrow(), 0.001)
        assertTrue(UtilityToolsEngine.formatJson("""{"a":1}""").isSuccess)
        val pw = UtilityToolsEngine.generatePassword(16, true)
        assertEquals(16, pw.length)
        assertEquals(1000.0, UtilityToolsEngine.convertUnit(1.0, "Length", "km", "m"), 0.001)
        assertEquals(212.0, UtilityToolsEngine.convertUnit(100.0, "Temperature", "C", "F"), 0.001)
    }
}
