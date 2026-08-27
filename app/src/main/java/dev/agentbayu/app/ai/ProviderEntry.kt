package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class ProviderEntry(
    val id: String,
    val label: String,
    val wireFormat: WireFormat,
    val baseUrl: String,
    val tier: ProviderTier,
    val authType: AuthType = AuthType.API_KEY,
    val authHeader: AuthHeader = AuthHeader.BEARER,
    val authPrefix: String? = null,
    val modelsPath: String? = null,
    val supportsStreamUsage: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val keyUrl: String? = null,
    val editableBaseUrl: Boolean = false,
    val allowCustomModel: Boolean = false,
    val unsupportedParams: List<String> = emptyList(),
    val extraHeaders: Map<String, String> = emptyMap(),
    val models: List<ModelEntry> = emptyList()
) {
    val requiresKey: Boolean
        get() = authType == AuthType.API_KEY

    fun model(modelId: String): ModelEntry? = models.firstOrNull { it.id == modelId }

    fun modelOrFallback(modelId: String): ModelEntry = model(modelId) ?: ModelEntry(id = modelId)

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}
