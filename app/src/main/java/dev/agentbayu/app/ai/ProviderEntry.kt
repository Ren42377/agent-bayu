package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import kotlinx.serialization.Serializable

@Serializable
data class ProviderEntry(
    val id: String,
    val label: String,
    val wireFormat: WireFormat,
    val baseUrl: String,
    val tier: ProviderTier,
    val authKind: AuthKind = AuthKind.API_KEY,
    val optionalKey: Boolean = false,
    val anonymousKey: String? = null,
    val minOutputTokens: Int? = null,
    val risk: RiskLevel = RiskLevel.NONE,
    val authHeader: AuthHeader = AuthHeader.BEARER,
    val authPrefix: String? = null,
    val modelsPath: String? = null,
    val modelIdFilter: String? = null,
    val supportsStreamUsage: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val keyUrl: String? = null,
    val editableBaseUrl: Boolean = false,
    val allowCustomModel: Boolean = false,
    val unsupportedParams: List<String> = emptyList(),
    val extraHeaders: Map<String, String> = emptyMap(),
    val oauth: OAuthConfig? = null,
    val models: List<ModelEntry> = emptyList()
) {
    val requiresKey: Boolean
        get() = authKind == AuthKind.API_KEY

    val requiresCredential: Boolean
        get() = authKind != AuthKind.NONE

    val acceptsKey: Boolean
        get() = requiresKey || optionalKey

    val deviceLogin: OAuthConfig?
        get() = oauth?.takeIf { authKind.isOAuth && it.isDeviceCode }

    fun model(modelId: String): ModelEntry? = models.firstOrNull { it.id == modelId }

    fun modelOrFallback(modelId: String): ModelEntry = model(modelId) ?: ModelEntry(id = modelId)

    fun clampOutputTokens(requested: Int?): Int? {
        val floor = minOutputTokens ?: return requested
        val value = requested ?: return null
        return if (value < floor) floor else value
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}
