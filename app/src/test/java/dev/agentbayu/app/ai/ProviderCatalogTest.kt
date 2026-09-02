package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthFlow
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
    fun `bundled catalog holds opencode codex agy and one openai compatible entry`() {
        assertEquals(
            listOf("opencode", "codex", "agy", "openai-compatible"),
            catalog.providers.map { it.id }
        )
        assertEquals("opencode", ProviderCatalog.DEFAULT_PROVIDER_ID)
    }

    @Test
    fun `agy entry uses antigravity wire with browser login and bootstrap`() {
        val provider = catalog.find("agy")!!

        assertEquals(WireFormat.ANTIGRAVITY, provider.wireFormat)
        assertEquals(AuthKind.OAUTH_PKCE, provider.authKind)
        assertEquals(ProviderTier.SUBSCRIPTION, provider.tier)
        assertEquals(RiskLevel.TOS_GRAY, provider.risk)
        assertNotNull(provider.browserLogin)
        assertTrue(provider.needsProjectBootstrap)
        assertTrue(provider.modelsUsePost)
        assertTrue(provider.models.isNotEmpty())
        assertFalse(provider.browserLogin!!.clientSecretMasked.isNullOrBlank())
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
    fun `codex logs in with a device code and speaks the responses wire`() {
        val provider = catalog.find("codex")!!

        assertEquals(AuthKind.OAUTH_DEVICE, provider.authKind)
        assertEquals(WireFormat.OPENAI_RESPONSES, provider.wireFormat)
        assertEquals(ProviderTier.SUBSCRIPTION, provider.tier)
        assertEquals(RiskLevel.TOS_GRAY, provider.risk)
        assertFalse(provider.requiresKey)
        assertFalse(provider.acceptsKey)
        assertTrue(provider.requiresCredential)
        assertNull(provider.anonymousKey)
        assertNull(provider.modelsPath)
        assertTrue(provider.allowCustomModel)
        assertEquals(120_000L, provider.timeoutMillis)
        assertTrue(provider.models.isNotEmpty())

        val config = provider.deviceLogin!!
        assertEquals(OAuthFlow.DEVICE_CODE, config.flow)
        assertEquals("app_EMoamEEZ73f0CkXaXp7hrann", config.clientId)
        assertEquals("chatgpt-account-id", config.accountHeader)
        assertEquals("chatgpt_account_id", config.accountField)
        listOf(
            config.tokenUrl,
            config.userCodeUrl,
            config.pollUrl,
            config.verificationUrl,
            config.redirectUri
        ).forEach { url ->
            assertTrue(url.orEmpty(), url.orEmpty().startsWith("https://"))
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
    fun `opencode carries the reasoning ladder as a request field`() {
        val provider = catalog.find("opencode")!!

        assertEquals(EffortMode.REQUEST_FIELD, provider.effortMode)
        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            availableEfforts(provider, "laguna-s-2.1-free")
        )
        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX),
            availableEfforts(provider, "deepseek-v4-flash-free")
        )
        assertEquals(
            listOf(
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
                ReasoningEffort.XHIGH
            ),
            availableEfforts(provider, "muse-spark-1.2-contributor-free")
        )
        assertTrue(availableEfforts(provider, "mimo-v2.5-free").isEmpty())
    }

    @Test
    fun `codex offers every level on each model`() {
        val provider = catalog.find("codex")!!

        assertEquals(EffortMode.REQUEST_FIELD, provider.effortMode)
        provider.models.forEach { model ->
            assertEquals(model.id, ReasoningEffort.entries.toList(), model.efforts)
        }
    }

    @Test
    fun `agy reads the ladder from model id suffixes`() {
        val provider = catalog.find("agy")!!

        assertEquals(EffortMode.MODEL_SUFFIX, provider.effortMode)
        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            availableEfforts(provider, "gemini-3.7-flash-medium")
        )
        assertTrue(availableEfforts(provider, "gemini-3.7-flash-tiered").isEmpty())
        assertTrue(availableEfforts(provider, "gemini-3.1-pro-low").isEmpty())
        assertTrue(availableEfforts(provider, "gemini-3.1-flash-lite").isEmpty())
    }

    @Test
    fun `every bundled model declares its own limits`() {
        val models = catalog.providers.flatMap { it.models }

        assertTrue(models.isNotEmpty())
        models.forEach { model ->
            assertTrue(model.id, model.contextLength != ModelEntry.DEFAULT_CONTEXT_LENGTH)
            assertTrue(model.id, model.maxOutputTokens != ModelEntry.DEFAULT_MAX_OUTPUT_TOKENS)
            assertTrue(model.id, model.maxOutputTokens <= model.contextLength)
            assertTrue(model.id, inputTokenBudget(model) > MIN_INPUT_BUDGET)
        }
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
