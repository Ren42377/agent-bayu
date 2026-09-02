package dev.agentbayu.app.ai

data class Candidate(
    val connection: Connection,
    val provider: ProviderEntry,
    val model: ModelEntry
) {
    val key: String
        get() = connection.id + "|" + model.id

    val tier: ProviderTier
        get() = provider.tier

    val wireFormat: WireFormat
        get() = model.wireFormat ?: provider.wireFormat

    val baseUrl: String
        get() = connection.baseUrlOverride?.takeIf { it.isNotBlank() } ?: provider.baseUrl

    val controlBaseUrl: String
        get() = connection.baseUrlOverride?.takeIf { it.isNotBlank() } ?: provider.controlUrl

    val isLocal: Boolean
        get() = LOOPBACK_HOSTS.any { baseUrl.contains(it, ignoreCase = true) }

    val inputPricePerMillion: Double
        get() = model.inputPricePerMillion ?: ModelEntry.UNKNOWN_PRICE_PER_MILLION

    val outputPricePerMillion: Double
        get() = model.outputPricePerMillion ?: ModelEntry.UNKNOWN_PRICE_PER_MILLION

    val blendedPricePerMillion: Double
        get() = (inputPricePerMillion + outputPricePerMillion * 3.0) / 4.0

    val efforts: List<ReasoningEffort>
        get() = availableEfforts(provider, model.id, connection.discoveredModels)

    val effort: ReasoningEffort?
        get() = resolveEffort(efforts, connection.effort, model.id)

    companion object {
        private val LOOPBACK_HOSTS = listOf("127.0.0.1", "localhost", "10.0.2.2", "[::1]")
    }
}
