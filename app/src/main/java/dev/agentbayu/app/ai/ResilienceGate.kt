package dev.agentbayu.app.ai

data class CandidateHealth(
    val breaker: BreakerState = BreakerState.CLOSED,
    val breakerOpenRemainingMillis: Long = 0L,
    val cooldownRemainingMillis: Long = 0L,
    val modelLockRemainingMillis: Long = 0L
) {
    val available: Boolean
        get() = breaker != BreakerState.OPEN &&
            cooldownRemainingMillis <= 0L &&
            modelLockRemainingMillis <= 0L

    val score: Double
        get() = when {
            !available -> 0.0
            breaker == BreakerState.HALF_OPEN -> 0.5
            else -> 1.0
        }
}

data class GateResult(
    val allowed: List<Candidate>,
    val skipped: List<SkippedCandidate>
)

class ResilienceGate(
    val breaker: CircuitBreaker,
    val cooldown: CooldownRegistry,
    val lockout: ModelLockout
) {

    fun health(candidate: Candidate): CandidateHealth {
        val snapshot = breaker.snapshot(candidate.provider.id)
        return CandidateHealth(
            breaker = snapshot.state,
            breakerOpenRemainingMillis = snapshot.openRemainingMillis,
            cooldownRemainingMillis = cooldown.remainingMillis(candidate.connection.id),
            modelLockRemainingMillis = lockout.remainingMillis(candidate.provider.id, candidate.model.id)
        )
    }

    fun filter(
        candidates: List<Candidate>,
        estimatedInputTokens: Int,
        hasKey: (Candidate) -> Boolean
    ): GateResult {
        val allowed = ArrayList<Candidate>()
        val skipped = ArrayList<SkippedCandidate>()
        val contextRejected = ArrayList<Candidate>()

        candidates.forEach { candidate ->
            val health = health(candidate)
            when {
                candidate.provider.requiresKey && !hasKey(candidate) ->
                    skipped += skip(candidate, SkipReason.MISSING_KEY)

                health.breaker == BreakerState.OPEN ->
                    skipped += skip(
                        candidate,
                        SkipReason.BREAKER_OPEN,
                        seconds(health.breakerOpenRemainingMillis)
                    )

                health.cooldownRemainingMillis > 0L ->
                    skipped += skip(
                        candidate,
                        SkipReason.COOLDOWN,
                        seconds(health.cooldownRemainingMillis)
                    )

                health.modelLockRemainingMillis > 0L ->
                    skipped += skip(
                        candidate,
                        SkipReason.MODEL_LOCKED,
                        seconds(health.modelLockRemainingMillis)
                    )

                !fitsContext(candidate, estimatedInputTokens) -> {
                    contextRejected += candidate
                    skipped += skip(candidate, SkipReason.CONTEXT_TOO_SMALL)
                }

                else -> allowed += candidate
            }
        }

        if (allowed.isEmpty() && contextRejected.isNotEmpty()) {
            val readmitted = contextRejected.sortedByDescending { it.model.contextLength }
            return GateResult(
                allowed = readmitted,
                skipped = skipped.filter { it.reason != SkipReason.CONTEXT_TOO_SMALL }
            )
        }

        return GateResult(allowed, skipped)
    }

    fun recordSuccess(candidate: Candidate) {
        breaker.recordSuccess(candidate.provider.id)
        cooldown.clear(candidate.connection.id)
    }

    fun recordFailure(candidate: Candidate, failure: RouteFailure) {
        if (failure.tripsBreaker) {
            breaker.recordFailure(candidate.provider.id, candidate.isLocal)
        }
        when (failure.kind) {
            FailureKind.COOLDOWN -> cooldown.penalize(candidate.connection.id, failure.retryAfterMillis)
            FailureKind.MODEL_LOCK -> lockout.lock(candidate.provider.id, candidate.model.id)
            FailureKind.RETRYABLE -> Unit
            FailureKind.TERMINAL -> Unit
        }
    }

    private fun fitsContext(candidate: Candidate, estimatedInputTokens: Int): Boolean {
        if (estimatedInputTokens <= 0) return true
        val reserve = candidate.model.maxOutputTokens.coerceAtMost(MAX_OUTPUT_RESERVE)
        return candidate.model.contextLength >= estimatedInputTokens + reserve
    }

    private fun skip(candidate: Candidate, reason: SkipReason, detail: String? = null) =
        SkippedCandidate(
            connectionLabel = candidate.connection.label,
            model = candidate.model.id,
            reason = reason,
            detail = detail
        )

    private fun seconds(millis: Long): String = ((millis + 999L) / 1000L).toString()

    companion object {
        const val MAX_OUTPUT_RESERVE = 1_024
    }
}
