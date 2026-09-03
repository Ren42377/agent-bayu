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
    fun `agy streams from the daily host and discovers from the control host`() {
        val provider = catalog.find("agy")!!

        assertEquals("https://daily-cloudcode-pa.googleapis.com", provider.baseUrl)
        assertEquals("https://cloudcode-pa.googleapis.com", provider.controlUrl)
        assertEquals("/v1internal:fetchAvailableModels", provider.modelsPath)
        assertEquals(
            "antigravity/ide/2.1.1 darwin/arm64",
            provider.extraHeaders["User-Agent"]
        )
        assertEquals(1, provider.extraHeaders.size)
        assertEquals(120_000L, provider.timeoutMillis)
    }

    @Test
    fun `agy tiered models carry the parenthesized upstream id`() {
        val provider = catalog.find("agy")!!

        assertEquals(
            "gemini-3.7-flash-tiered(high)",
            provider.model("gemini-3.7-flash-high")?.wireId
        )
        assertEquals(
            "gemini-3.7-flash-tiered(medium)",
            provider.model("gemini-3.7-flash-medium")?.wireId
        )
        assertEquals(
            "gemini-3.6-flash-tiered(low)",
            provider.model("gemini-3.6-flash-low")?.wireId
        )
        assertEquals("gemini-pro-agent", provider.model("gemini-pro-agent")?.wireId)
        provider.models.forEach { model ->
            assertEquals(model.id, WireFormat.ANTIGRAVITY, provider.wireFormat)
            assertNull(model.id, model.wireFormat)
        }
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
        assertEquals("public", provider.anonymousKey)
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
    fun `opencode sends the desktop client headers`() {
        val headers = catalog.find("opencode")!!.extraHeaders

        assertEquals("opencode", headers["User-Agent"])
        assertEquals("desktop", headers["x-opencode-client"])
        assertEquals("global", headers["x-opencode-project"])
        assertEquals("ses_{session}", headers["x-opencode-session"])
        assertEquals("msg_{request}", headers["x-opencode-request"])
    }

    @Test
    fun `only the muse model speaks the responses wire inside opencode`() {
        val provider = catalog.find("opencode")!!

        assertEquals(
            WireFormat.OPENAI_RESPONSES,
            provider.model("muse-spark-1.2-contributor-free")?.wireFormat
        )
        provider.models
            .filter { it.id != "muse-spark-1.2-contributor-free" }
            .forEach { model -> assertNull(model.id, model.wireFormat) }
    }

    @Test
    fun `codex sends the cli headers with a per request session id`() {
        val headers = catalog.find("codex")!!.extraHeaders

        assertEquals("0.149.0", headers["Version"])
        assertEquals("responses=experimental", headers["Openai-Beta"])
        assertEquals("codex_cli_rs", headers["originator"])
        assertEquals("{uuid}", headers["session_id"])
        assertTrue(headers["User-Agent"].orEmpty().startsWith("codex-cli/"))
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
    fun `agy keeps the level inside the model id and offers no effort ladder`() {
        val provider = catalog.find("agy")!!

        assertEquals(EffortMode.NONE, provider.effortMode)
        provider.models.forEach { model ->
            assertTrue(model.id, model.efforts.isEmpty())
            assertTrue(model.id, availableEfforts(provider, model.id).isEmpty())
        }
        listOf(
            "gemini-3.7-flash-high",
            "gemini-3.7-flash-medium",
            "gemini-3.7-flash-low",
            "gemini-3.6-flash-high",
            "claude-opus-4-6-thinking",
            "gpt-oss-120b-medium"
        ).forEach { id ->
            assertNotNull(id, provider.model(id))
        }
        assertNull(provider.model("gemini-3.7-flash-tiered"))
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
    fun `multimodal models declare vision and the rest stay text only`() {
        val vision = catalog.providers
            .flatMap { provider -> provider.models.map { provider.id to it } }
            .filter { it.second.vision }
            .map { it.first + "/" + it.second.id }

        assertEquals(
            listOf(
                "codex/gpt-5.6-sol",
                "codex/gpt-5.6-terra",
                "codex/gpt-5.6-luna",
                "agy/gemini-3.7-flash-high",
                "agy/gemini-3.7-flash-medium",
                "agy/gemini-3.7-flash-low",
                "agy/gemini-3.6-flash-high",
                "agy/gemini-3.6-flash-medium",
                "agy/gemini-3.6-flash-low",
                "agy/gemini-3.5-flash-high",
                "agy/gemini-3-flash-agent",
                "agy/gemini-3.5-flash-low",
                "agy/gemini-3.5-flash-extra-low",
                "agy/gemini-3-flash",
                "agy/gemini-pro-agent",
                "agy/gemini-3.1-pro-low",
                "agy/claude-opus-4-6-thinking",
                "agy/claude-sonnet-4-6"
            ),
            vision
        )
    }

    @Test
    fun `no provider claims vision for every model it hosts`() {
        catalog.providers.forEach { provider -> assertFalse(provider.id, provider.vision) }
    }

    @Test
    fun `vision follows the model and the provider default`() {
        val provider = testProvider(
            id = "eyes",
            models = listOf(ModelEntry(id = "seeing", vision = true), ModelEntry(id = "blind"))
        )
        val seeing = Candidate(
            connection = testConnection(providerId = provider.id, model = "seeing"),
            provider = provider,
            model = provider.modelOrFallback("seeing")
        )
        val blind = seeing.copy(model = provider.modelOrFallback("blind"))
        val custom = Candidate(
            connection = testConnection(providerId = provider.id, model = "unlisted"),
            provider = provider.copy(vision = true),
            model = provider.modelOrFallback("unlisted")
        )

        assertTrue(seeing.supportsVision)
        assertFalse(blind.supportsVision)
        assertTrue(custom.supportsVision)
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
