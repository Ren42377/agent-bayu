package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val estimatedCostUsd: Double? = null,
    val estimated: Boolean = true
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens

    companion object {
        const val CHARS_PER_TOKEN = 4

        fun estimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            return (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
        }

        fun estimateTokensFromChars(chars: Int): Int {
            if (chars <= 0) return 0
            return (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
        }
    }
}
