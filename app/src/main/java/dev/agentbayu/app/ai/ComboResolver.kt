package dev.agentbayu.app.ai

data class RoutedCandidate(
    val candidate: Candidate,
    val strategy: String
)

data class ResolvedRoute(
    val channel: String,
    val candidates: List<RoutedCandidate>
)

class ComboResolver {

    fun resolve(
        channel: String,
        config: RoutingConfig,
        candidates: List<Candidate>,
        context: RoutingContext,
        strategyFor: (String) -> RoutingStrategy
    ): ResolvedRoute {
        if (candidates.isEmpty()) return ResolvedRoute(channel, emptyList())

        RoutingConfig.connectionIdOf(channel)?.let { connectionId ->
            val pinned = candidates.filter { it.connection.id == connectionId }
            if (pinned.isNotEmpty()) {
                return ResolvedRoute(channel, pinned.map { RoutedCandidate(it, RoutingStrategies.PRIORITY) })
            }
        }

        RoutingConfig.comboIdOf(channel)?.let { comboId ->
            val combo = config.combo(comboId)
            if (combo != null) {
                val ordered = resolveCombo(combo, candidates, context, strategyFor)
                if (ordered.isNotEmpty()) return ResolvedRoute(channel, ordered)
            }
        }

        val scorer = CandidateScorer.forChannel(if (AutoChannels.isAuto(channel)) channel else AutoChannels.AUTO)
        return ResolvedRoute(
            channel = channel,
            candidates = scorer.order(candidates, context).map { RoutedCandidate(it, scorer.name) }
        )
    }

    private fun resolveCombo(
        combo: Combo,
        candidates: List<Candidate>,
        context: RoutingContext,
        strategyFor: (String) -> RoutingStrategy
    ): List<RoutedCandidate> {
        val ordered = LinkedHashMap<String, RoutedCandidate>()
        combo.steps.forEach { step ->
            val pool = candidates.filter { matches(step, it) }
            if (pool.isNotEmpty()) {
                val strategy = strategyFor(step.strategy)
                strategy.order(pool, context).forEach { candidate ->
                    ordered.getOrPut(candidate.key) { RoutedCandidate(candidate, strategy.name) }
                }
            }
        }
        return ordered.values.toList()
    }

    private fun matches(step: ComboStep, candidate: Candidate): Boolean {
        if (step.tier != null && candidate.tier != step.tier) return false
        if (step.connectionIds.isNotEmpty() && !step.connectionIds.contains(candidate.connection.id)) {
            return false
        }
        return true
    }

    companion object {
        fun buildCandidates(
            connections: List<Connection>,
            catalog: ProviderCatalog
        ): List<Candidate> = connections
            .filter { it.enabled }
            .mapNotNull { connection ->
                val provider = catalog.find(connection.providerId) ?: return@mapNotNull null
                Candidate(
                    connection = connection,
                    provider = provider,
                    model = provider.modelOrFallback(connection.model)
                )
            }
    }
}
