package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import dev.agentbayu.app.ai.oauth.OAuthFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerTest {

    @Test
    fun `discovered ids lead the picker when the provider can list models`() {
        val provider = testProvider(
            id = "agy",
            modelsPath = "/models",
            models = listOf(
                ModelEntry(id = "gemini-3.8-flash"),
                ModelEntry(id = "gemini-3.7-flash")
            )
        )
        val connection = testConnection(
            providerId = "agy",
            model = "gemini-3.7-flash",
            discoveredModels = listOf("gemini-3.8-flash", "gemini-3.6-flash")
        )

        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.7-flash"),
            pickerModelIds(provider, connection)
        )
    }

    @Test
    fun `catalog models stay the fallback when discovery is empty`() {
        val provider = testProvider(
            id = "agy",
            modelsPath = "/models",
            models = listOf(
                ModelEntry(id = "live"),
                ModelEntry(id = "retired", deprecated = true)
            )
        )
        val connection = testConnection(providerId = "agy", model = "live")

        assertEquals(listOf("live"), pickerModelIds(provider, connection))
    }

    @Test
    fun `providers without discovery merge catalog and custom ids`() {
        val provider = testProvider(
            id = "codex",
            models = listOf(ModelEntry(id = "gpt-5.6-terra"))
        )
        val connection = testConnection(
            providerId = "codex",
            model = "gpt-5.6-terra",
            customModels = listOf("my-model", "gpt-5.6-terra")
        )

        assertEquals(
            listOf("gpt-5.6-terra", "my-model"),
            pickerModelIds(provider, connection)
        )
    }

    @Test
    fun `plan gated models hide behind an unmatched plan`() {
        val sol = ModelEntry(id = "gpt-5.6-sol", plans = listOf("plus", "pro"))
        val terra = ModelEntry(id = "gpt-5.6-terra")

        assertTrue(isModelAccessible(terra, "free"))
        assertTrue(isModelAccessible(sol, "pro"))
        assertFalse(isModelAccessible(sol, "free"))
        assertTrue(isModelAccessible(sol, null))
        assertTrue(isModelAccessible(sol, ""))
    }

    @Test
    fun `the picker drops models the plan cannot reach`() {
        val provider = testProvider(
            id = "codex",
            models = listOf(
                ModelEntry(id = "gpt-5.6-sol", plans = listOf("plus", "pro")),
                ModelEntry(id = "gpt-5.6-terra"),
                ModelEntry(id = "gpt-5.6-luna")
            )
        )
        val connection = testConnection(providerId = "codex", model = "gpt-5.6-terra")

        assertEquals(
            listOf("gpt-5.6-terra", "gpt-5.6-luna"),
            pickerModelIds(provider, connection, "free")
        )
        assertEquals(
            listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna"),
            pickerModelIds(provider, connection, "plus")
        )
        assertEquals(
            listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna"),
            pickerModelIds(provider, connection, null)
        )
    }

    @Test
    fun `the plan type comes from the oauth extras named by the provider`() {
        val provider = testProvider(
            id = "codex",
            oauth = OAuthConfig(
                flow = OAuthFlow.DEVICE_CODE,
                clientId = "app",
                tokenUrl = "https://auth.test/token",
                accountClaim = "https://api.openai.com/auth",
                planField = "chatgpt_plan_type"
            )
        )
        val tokens = Credential.OAuthTokens(
            accessToken = "a",
            extras = mapOf(
                "chatgpt_plan_type" to "Free",
                "chatgpt_account_id" to "acc-1"
            )
        )
        val keyOnly = Credential.ApiKey("k")

        assertEquals("free", planTypeOf(tokens, provider))
        assertNull(planTypeOf(keyOnly, provider))
        assertNull(planTypeOf(tokens, provider.copy(oauth = null)))
    }

    @Test
    fun `effort variants collapse into their family`() {
        val provider = testProvider(
            id = "agy",
            modelsPath = "/models",
            models = listOf(
                ModelEntry(id = "gemini-3.8-flash"),
                ModelEntry(id = "gemini-3.8-flash-high", deprecated = true),
                ModelEntry(id = "gemini-3.8-flash-low", deprecated = true)
            )
        )

        assertEquals(
            "gemini-3.8-flash",
            normalizeDiscoveredModelId(provider, "gemini-3.8-flash-low")
        )
        assertEquals(
            "gemini-3.8-flash",
            normalizeDiscoveredModelId(provider, "gemini-3.8-flash-high (High)")
        )
        assertEquals("gemini-2.5-pro", normalizeDiscoveredModelId(provider, "gemini-2.5-pro"))
    }

    @Test
    fun `the picker hides deprecated effort variants returned by discovery`() {
        val provider = testProvider(
            id = "agy",
            modelsPath = "/models",
            models = listOf(
                ModelEntry(id = "gemini-3.8-flash"),
                ModelEntry(id = "gemini-3.8-flash-high", deprecated = true),
                ModelEntry(id = "gemini-3.8-flash-low", deprecated = true),
                ModelEntry(id = "gemini-2.5-pro")
            )
        )
        val connection = testConnection(
            providerId = "agy",
            model = "gemini-3.8-flash",
            discoveredModels = listOf(
                "gemini-3.8-flash-high",
                "gemini-3.8-flash-low",
                "gemini-2.5-pro",
                "gemini-2.5-pro-low"
            )
        )

        assertEquals(
            listOf("gemini-3.8-flash", "gemini-2.5-pro"),
            pickerModelIds(provider, connection)
        )
    }

    @Test
    fun `a deprecated model already in use stays selectable`() {
        val provider = testProvider(
            id = "agy",
            modelsPath = "/models",
            models = listOf(
                ModelEntry(id = "gemini-3.8-flash"),
                ModelEntry(id = "gemini-3.8-flash-high", deprecated = true)
            )
        )
        val connection = testConnection(
            providerId = "agy",
            model = "gemini-3.8-flash-high",
            discoveredModels = listOf("gemini-3.8-flash-high", "gemini-3.8-flash")
        )

        assertEquals(
            listOf("gemini-3.8-flash-high", "gemini-3.8-flash"),
            pickerModelIds(provider, connection)
        )
    }

    @Test
    fun `custom models survive discovery`() {
        val provider = testProvider(
            id = "agy",
            modelsPath = "/models",
            models = listOf(ModelEntry(id = "gemini-3.8-flash"))
        )
        val connection = testConnection(
            providerId = "agy",
            model = "my-model",
            discoveredModels = listOf("gemini-3.8-flash"),
            customModels = listOf("my-model", "other-model")
        )

        assertEquals(
            listOf("gemini-3.8-flash", "my-model", "other-model"),
            pickerModelIds(provider, connection)
        )
    }

    @Test
    fun `the plan type falls back to the access token claim`() {
        val provider = testProvider(
            id = "codex",
            oauth = OAuthConfig(
                flow = OAuthFlow.DEVICE_CODE,
                clientId = "app",
                tokenUrl = "https://auth.test/token",
                accountClaim = "https://api.openai.com/auth",
                planField = "chatgpt_plan_type"
            )
        )

        assertEquals(
            "free",
            planTypeOf(Credential.OAuthTokens(accessToken = jwtWithPlan("free")), provider)
        )
        assertNull(
            planTypeOf(Credential.OAuthTokens(accessToken = "not-a-jwt"), provider)
        )
    }

    @Test
    fun `the account email comes from the stored extras`() {
        val tokens = Credential.OAuthTokens(
            accessToken = "a",
            extras = mapOf(Credential.EMAIL_EXTRA to " user@example.com ")
        )

        assertEquals("user@example.com", accountEmailOf(tokens))
        assertNull(accountEmailOf(Credential.OAuthTokens(accessToken = "a")))
        assertNull(accountEmailOf(Credential.ApiKey("k")))
    }
}

private fun jwtWithPlan(plan: String): String {
    val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
        "{\"https://api.openai.com/auth\":{\"chatgpt_plan_type\":\"$plan\"}}"
            .toByteArray(Charsets.UTF_8)
    )
    return "header.$payload.signature"
}
