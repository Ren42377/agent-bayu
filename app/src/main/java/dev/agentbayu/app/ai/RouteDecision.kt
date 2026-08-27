package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class RouteDecision(
    val channel: String,
    val strategy: String,
    val providerId: String,
    val providerLabel: String,
    val model: String,
    val connectionId: String,
    val connectionLabel: String,
    val tier: ProviderTier,
    val attempt: Int,
    val candidatesConsidered: Int,
    val reason: String = "",
    val firstTokenMillis: Long = 0L,
    val totalMillis: Long = 0L,
    val skipped: List<SkippedCandidate> = emptyList(),
    val degraded: Boolean = false
) {
    val label: String
        get() = providerLabel + " " + model
}

@Serializable
data class SkippedCandidate(
    val connectionLabel: String,
    val model: String,
    val reason: SkipReason,
    val detail: String? = null
)

@Serializable
enum class SkipReason {
    BREAKER_OPEN,
    COOLDOWN,
    MODEL_LOCKED,
    MISSING_KEY,
    CONTEXT_TOO_SMALL,
    FAILED
}
