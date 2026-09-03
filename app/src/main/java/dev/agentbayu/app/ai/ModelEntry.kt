package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class ModelEntry(
    val id: String,
    val label: String = id,
    val upstreamId: String? = null,
    val wireFormat: WireFormat? = null,
    val contextLength: Int = DEFAULT_CONTEXT_LENGTH,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val inputPricePerMillion: Double? = null,
    val outputPricePerMillion: Double? = null,
    val unsupportedParams: List<String> = emptyList(),
    val efforts: List<ReasoningEffort> = emptyList(),
    val free: Boolean = false,
    val vision: Boolean = false
) {
    val wireId: String
        get() = upstreamId?.takeIf { it.isNotBlank() } ?: id

    val hasKnownPrice: Boolean
        get() = inputPricePerMillion != null && outputPricePerMillion != null

    fun costUsd(inputTokens: Int, outputTokens: Int): Double? {
        val input = inputPricePerMillion ?: return null
        val output = outputPricePerMillion ?: return null
        return (inputTokens * input + outputTokens * output) / 1_000_000.0
    }

    companion object {
        const val DEFAULT_CONTEXT_LENGTH = 32_768
        const val DEFAULT_MAX_OUTPUT_TOKENS = 4_096
        const val UNKNOWN_PRICE_PER_MILLION = 30.0
    }
}
