package dev.agentbayu.app.ai

data class ScoreWeights(
    val breakerHealth: Double,
    val cooldown: Double,
    val latency: Double,
    val cost: Double,
    val tier: Double,
    val contextFit: Double
) {
    companion object {
        val BALANCED = ScoreWeights(0.30, 0.15, 0.20, 0.15, 0.10, 0.10)
        val FAST = ScoreWeights(0.25, 0.10, 0.45, 0.05, 0.05, 0.10)
        val CHEAP = ScoreWeights(0.20, 0.10, 0.10, 0.50, 0.05, 0.05)
    }
}

data class ScoreBreakdown(
    val breakerHealth: Double,
    val cooldown: Double,
    val latency: Double,
    val cost: Double,
    val tier: Double,
    val contextFit: Double,
    val total: Double
)

data class ScoredCandidate(
    val candidate: Candidate,
    val breakdown: ScoreBreakdown
)

class CandidateScorer(
    override val name: String,
    private val weights: ScoreWeights,
    private val freeOnly: Boolean = false
) : RoutingStrategy {

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        rank(candidates, context).map { it.candidate }

    fun rank(candidates: List<Candidate>, context: RoutingContext): List<ScoredCandidate> {
        val pool = if (freeOnly) {
            val free = candidates.filter { it.tier == ProviderTier.FREE || it.model.free }
            if (free.isEmpty()) candidates else free
        } else {
            candidates
        }
        return pool
            .map { ScoredCandidate(it, score(it, context)) }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.breakdown.total }
                    .thenBy { it.candidate.connection.priority }
                    .thenBy { it.candidate.model.id }
            )
    }

    fun score(candidate: Candidate, context: RoutingContext): ScoreBreakdown {
        val health = context.healthOf(candidate)
        val usage = context.usageOf(candidate)

        val breakerHealth = health.score
        val cooldown = 1.0 / (1.0 + health.cooldownRemainingMillis.toDouble() / 60_000.0)
        val latency = latencyScore(usage.firstTokenEwmaMillis)
        val cost = costScore(candidate)
        val tier = tierScore(candidate.tier)
        val contextFit = contextScore(candidate, context.estimatedInputTokens)

        val total = breakerHealth * weights.breakerHealth +
            cooldown * weights.cooldown +
            latency * weights.latency +
            cost * weights.cost +
            tier * weights.tier +
            contextFit * weights.contextFit

        return ScoreBreakdown(breakerHealth, cooldown, latency, cost, tier, contextFit, total)
    }

    private fun latencyScore(ewmaMillis: Double): Double {
        if (ewmaMillis <= 0.0) return NEUTRAL_LATENCY_SCORE
        return 1.0 / (1.0 + ewmaMillis / LATENCY_REFERENCE_MILLIS)
    }

    private fun costScore(candidate: Candidate): Double {
        if (candidate.model.free) return 1.0
        return 1.0 / (1.0 + candidate.blendedPricePerMillion / PRICE_REFERENCE)
    }

    private fun tierScore(tier: ProviderTier): Double = when (tier) {
        ProviderTier.SUBSCRIPTION -> 1.0
        ProviderTier.API_KEY -> 0.85
        ProviderTier.CHEAP -> 0.7
        ProviderTier.FREE -> 0.6
    }

    private fun contextScore(candidate: Candidate, estimatedInputTokens: Int): Double {
        if (estimatedInputTokens <= 0) return 1.0
        val needed = estimatedInputTokens + ResilienceGate.MAX_OUTPUT_RESERVE
        val ratio = candidate.model.contextLength.toDouble() / needed.toDouble()
        return ratio.coerceIn(0.0, 1.0)
    }

    companion object {
        const val LATENCY_REFERENCE_MILLIS = 2_000.0
        const val PRICE_REFERENCE = 5.0
        const val NEUTRAL_LATENCY_SCORE = 0.6

        fun forChannel(channel: String): CandidateScorer = when (channel) {
            AutoChannels.FAST -> CandidateScorer(channel, ScoreWeights.FAST)
            AutoChannels.CHEAP -> CandidateScorer(channel, ScoreWeights.CHEAP)
            AutoChannels.FREE -> CandidateScorer(channel, ScoreWeights.CHEAP, freeOnly = true)
            else -> CandidateScorer(AutoChannels.AUTO, ScoreWeights.BALANCED)
        }
    }
}

object AutoChannels {
    const val AUTO = "auto"
    const val FAST = "auto/fast"
    const val CHEAP = "auto/cheap"
    const val FREE = "auto/free"

    val all: List<String> = listOf(AUTO, FAST, CHEAP, FREE)

    fun isAuto(channel: String): Boolean = all.contains(channel)
}
