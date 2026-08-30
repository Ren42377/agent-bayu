package dev.agentbayu.app.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {

    private val catalog: ProviderCatalog = ProviderCatalog.parse(readAsset())

    @Test
    fun `bundled catalog parses and has no duplicate ids`() {
        val ids = catalog.providers.map { it.id }

        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `default provider exists and needs no credential`() {
        val provider = catalog.find(ProviderCatalog.DEFAULT_PROVIDER_ID)

        assertNotNull(provider)
        assertEquals(AuthKind.NONE, provider!!.authKind)
        assertFalse(provider.requiresKey)
        assertTrue(provider.models.isNotEmpty())
    }

    @Test
    fun `bundled catalog holds opencode and one openai compatible entry`() {
        assertEquals(listOf("opencode", "openai-compatible"), catalog.providers.map { it.id })
        assertEquals("opencode", ProviderCatalog.DEFAULT_PROVIDER_ID)
    }

    @Test
    fun `opencode stays keyless and filters models down to the free ones`() {
        val provider = catalog.find("opencode")!!

        assertEquals(AuthKind.NONE, provider.authKind)
        assertEquals(WireFormat.OPENAI, provider.wireFormat)
        assertEquals(ProviderTier.FREE, provider.tier)
        assertEquals(RiskLevel.FRAGILE, provider.risk)
        assertFalse(provider.requiresKey)
        assertTrue(provider.acceptsKey)
        assertNull(provider.anonymousKey)
        assertEquals("/models", provider.modelsPath)
        assertEquals("-free", provider.modelIdFilter)
        assertTrue(provider.supportsStreamUsage)
        assertTrue(provider.allowCustomModel)
        assertTrue(provider.baseUrl.startsWith("https://"))
        assertTrue(provider.models.isNotEmpty())
        provider.models.forEach { model ->
            assertTrue(model.id, model.id.endsWith("-free"))
            assertTrue(model.id, model.free)
            assertEquals(model.id, 0.0, model.costUsd(1_000, 1_000)!!, 0.0)
        }
    }

    @Test
    fun `openai compatible entry is the only door that takes a key`() {
        val keyed = catalog.providers.single { it.authKind == AuthKind.API_KEY }

        assertEquals("openai-compatible", keyed.id)
        assertEquals(ProviderTier.API_KEY, keyed.tier)
        assertTrue(keyed.requiresKey)
        assertTrue(keyed.editableBaseUrl)
        assertTrue(keyed.allowCustomModel)
        assertNull(keyed.modelIdFilter)
        assertTrue(keyed.models.isEmpty())
    }

    @Test
    fun `anonymous key stands in when no key is stored`() {
        val provider = testProvider(id = "anon", anonymousKey = "0000000000")
        val connection = testConnection(providerId = provider.id)

        assertEquals("0000000000", FakeKeys().secretFor(connection, provider))
        assertEquals(
            "own-key",
            FakeKeys(mapOf(connection.id to "own-key")).secretFor(connection, provider)
        )
    }

    @Test
    fun `output floor only lifts an explicit request`() {
        val provider = testProvider(minOutputTokens = 16)

        assertNull(provider.clampOutputTokens(null))
        assertEquals(16, provider.clampOutputTokens(8))
        assertEquals(16, provider.clampOutputTokens(16))
        assertEquals(512, provider.clampOutputTokens(512))
    }

    @Test
    fun `providers without a floor pass the request through`() {
        val provider = catalog.find("opencode")!!

        assertNull(provider.clampOutputTokens(null))
        assertEquals(8, provider.clampOutputTokens(8))
    }

    @Test
    fun `key providers require a key and expose where to get one`() {
        val keyed = catalog.providers.filter { it.authKind == AuthKind.API_KEY }

        assertTrue(keyed.isNotEmpty())
        keyed.forEach { provider ->
            assertTrue(provider.id, provider.requiresKey)
            assertTrue(provider.id, provider.acceptsKey)
            assertNull(provider.id, provider.anonymousKey)
            assertNotNull(provider.id, provider.keyUrl)
        }
    }

    @Test
    fun `tier order puts free providers first`() {
        val sorted = catalog.sortedByTier()

        assertEquals(ProviderTier.FREE, sorted.first().tier)
        assertEquals(ProviderTier.API_KEY, sorted.last().tier)
    }

    @Test
    fun `unknown provider and model resolve to null`() {
        assertNull(catalog.find("nope"))
        assertNull(catalog.model("opencode", "nope"))
    }

    @Test
    fun `model fallback keeps the requested id with default limits`() {
        val provider = catalog.find("opencode")!!

        val model = provider.modelOrFallback("mystery/model")

        assertEquals("mystery/model", model.id)
        assertEquals(ModelEntry.DEFAULT_CONTEXT_LENGTH, model.contextLength)
        assertEquals(ModelEntry.DEFAULT_MAX_OUTPUT_TOKENS, model.maxOutputTokens)
    }

    @Test
    fun `later duplicates replace earlier ones`() {
        val parsed = ProviderCatalog.parse(
            """
            {
              "version": 1,
              "providers": [
                {"id": "dup", "label": "First", "wireFormat": "openai",
                 "baseUrl": "https://a.test/v1", "tier": "free"},
                {"id": "dup", "label": "Second", "wireFormat": "openai",
                 "baseUrl": "https://b.test/v1", "tier": "free"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, parsed.providers.size)
        assertEquals("Second", parsed.find("dup")?.label)
    }

    @Test
    fun `unknown fields are ignored and auth kind defaults to api key`() {
        val parsed = ProviderCatalog.parse(
            """
            {
              "providers": [
                {"id": "legacy", "label": "Legacy", "wireFormat": "openai",
                 "baseUrl": "https://a.test/v1", "tier": "api_key", "somethingNew": 7}
              ]
            }
            """.trimIndent()
        )

        val provider = parsed.find("legacy")!!
        assertEquals(AuthKind.API_KEY, provider.authKind)
        assertTrue(provider.requiresKey)
        assertEquals(RiskLevel.NONE, provider.risk)
        assertEquals(ProviderEntry.DEFAULT_TIMEOUT_MILLIS, provider.timeoutMillis)
    }

    private fun readAsset(): String {
        val candidates = listOf(
            File("src/main/assets/providers.json"),
            File("app/src/main/assets/providers.json")
        )
        val file = candidates.firstOrNull { it.exists() }
        checkNotNull(file) { "providers.json not found" }
        return file.readText()
    }
}
