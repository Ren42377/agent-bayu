package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.WireEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

sealed interface RouterEvent {
    data class Route(val decision: RouteDecision) : RouterEvent

    data class Delta(val text: String) : RouterEvent

    data class Completed(val decision: RouteDecision, val usage: TokenUsage) : RouterEvent

    data class Failed(val reason: RouterFailure, val detail: String? = null) : RouterEvent
}

enum class RouterFailure {
    NO_CONNECTION,
    NO_CANDIDATE_AVAILABLE,
    ALL_CANDIDATES_FAILED
}

interface ConnectionSource {
    val connections: StateFlow<List<Connection>>

    fun markHealth(connectionId: String, health: ConnectionHealth, detail: String? = null)
}

interface KeySource {
    fun key(connectionId: String): String?

    fun hasKey(connectionId: String): Boolean
}

interface RoutingConfigSource {
    val config: StateFlow<RoutingConfig>
}

class AiRouter(
    private val catalog: ProviderCatalog,
    private val connectionSource: ConnectionSource,
    private val keySource: KeySource,
    private val configSource: RoutingConfigSource,
    private val gate: ResilienceGate,
    private val usageTracker: UsageTracker,
    private val adapters: Map<WireFormat, ChatAdapter>,
    private val resolver: ComboResolver = ComboResolver(),
    private val clock: Clock = RealClock,
    private val random: Random = Random.Default
) {

    private val strategies = HashMap<String, RoutingStrategy>()

    fun activeChannel(): String = configSource.config.value.channel

    fun candidates(): List<Candidate> =
        ComboResolver.buildCandidates(connectionSource.connections.value, catalog)

    fun health(candidate: Candidate): CandidateHealth = gate.health(candidate)

    fun preview(channel: String = activeChannel()): List<RoutedCandidate> {
        val pool = candidates()
        if (pool.isEmpty()) return emptyList()
        return resolver.resolve(channel, configSource.config.value, pool, context(channel, pool, 0), ::strategyFor)
            .candidates
    }

    fun stream(request: ChatRequest, channelOverride: String? = null): Flow<RouterEvent> = flow {
        val config = configSource.config.value
        val channel = channelOverride ?: config.channel
        val pool = candidates()
        if (pool.isEmpty()) {
            emit(RouterEvent.Failed(RouterFailure.NO_CONNECTION))
            return@flow
        }

        val estimatedInputTokens = estimateInputTokens(request)
        val gateResult = gate.filter(pool, estimatedInputTokens) { keySource.hasKey(it.connection.id) }
        if (gateResult.allowed.isEmpty()) {
            emit(
                RouterEvent.Failed(
                    RouterFailure.NO_CANDIDATE_AVAILABLE,
                    gateResult.skipped.firstOrNull()?.reason?.name
                )
            )
            return@flow
        }

        val routingContext = context(channel, gateResult.allowed, estimatedInputTokens)
        val resolved = resolver.resolve(channel, config, gateResult.allowed, routingContext, ::strategyFor)
        val skipped = ArrayList(gateResult.skipped)
        var attempt = 0
        var lastFailure: RouteFailure? = null

        for (routed in resolved.candidates) {
            attempt += 1
            val candidate = routed.candidate
            val adapter = adapters[candidate.provider.wireFormat]
            if (adapter == null) {
                skipped += SkippedCandidate(candidate.connection.label, candidate.model.id, SkipReason.FAILED)
                continue
            }

            var decision = RouteDecision(
                channel = channel,
                strategy = routed.strategy,
                providerId = candidate.provider.id,
                providerLabel = candidate.provider.label,
                model = candidate.model.id,
                connectionId = candidate.connection.id,
                connectionLabel = candidate.connection.label,
                tier = candidate.tier,
                attempt = attempt,
                candidatesConsidered = resolved.candidates.size,
                reason = routed.strategy,
                skipped = skipped.toList()
            )

            val startedAt = clock.nowMillis()
            var firstTokenMillis = 0L
            var streamed = false
            var outputChars = 0
            var wireUsage: WireEvent.Usage? = null
            var failure: RouteFailure? = null

            usageTracker.beginRequest(candidate.connection.id)

            adapter.stream(candidate, keySource.key(candidate.connection.id), request).collect { event ->
                when (event) {
                    is WireEvent.Delta -> {
                        if (!streamed) {
                            streamed = true
                            firstTokenMillis = clock.nowMillis() - startedAt
                            usageTracker.recordFirstToken(candidate.connection.id, firstTokenMillis)
                            decision = decision.copy(firstTokenMillis = firstTokenMillis)
                            emit(RouterEvent.Route(decision))
                        }
                        outputChars += event.text.length
                        emit(RouterEvent.Delta(event.text))
                    }

                    is WireEvent.Usage -> wireUsage = event
                    is WireEvent.Failure -> failure = event.failure
                    WireEvent.Done -> Unit
                }
            }

            val totalMillis = clock.nowMillis() - startedAt
            val pending = failure

            if (pending == null) {
                gate.recordSuccess(candidate)
                connectionSource.markHealth(candidate.connection.id, ConnectionHealth.READY)
                val usage = buildUsage(candidate, estimatedInputTokens, outputChars, wireUsage)
                usageTracker.recordSuccess(candidate.connection.id, usage)
                emit(
                    RouterEvent.Completed(
                        decision.copy(totalMillis = totalMillis, firstTokenMillis = firstTokenMillis),
                        usage
                    )
                )
                return@flow
            }

            applyFailure(candidate, pending)
            lastFailure = pending

            if (streamed) {
                val usage = buildUsage(candidate, estimatedInputTokens, outputChars, wireUsage)
                emit(
                    RouterEvent.Completed(
                        decision.copy(totalMillis = totalMillis, degraded = true),
                        usage
                    )
                )
                return@flow
            }

            skipped += SkippedCandidate(
                connectionLabel = candidate.connection.label,
                model = candidate.model.id,
                reason = SkipReason.FAILED,
                detail = pending.statusCode?.toString()
            )
        }

        emit(RouterEvent.Failed(RouterFailure.ALL_CANDIDATES_FAILED, lastFailure?.logLabel))
    }

    private fun applyFailure(candidate: Candidate, failure: RouteFailure) {
        gate.recordFailure(candidate, failure)
        usageTracker.recordFailure(candidate.connection.id, failure)
        if (failure.kind == FailureKind.TERMINAL) {
            gate.cooldown.penalize(candidate.connection.id, TERMINAL_COOLDOWN_MILLIS)
            connectionSource.markHealth(
                candidate.connection.id,
                ConnectionHealth.NEEDS_ATTENTION,
                failure.logLabel
            )
        }
    }

    private fun context(
        channel: String,
        pool: List<Candidate>,
        estimatedInputTokens: Int
    ): RoutingContext = RoutingContext(
        channel = channel,
        nowMillis = clock.nowMillis(),
        estimatedInputTokens = estimatedInputTokens,
        health = pool.associate { it.key to gate.health(it) },
        usage = usageTracker.stats.value
    )

    private fun strategyFor(name: String): RoutingStrategy = synchronized(strategies) {
        strategies.getOrPut(name) { RoutingStrategies.create(name, random) }
    }

    private fun estimateInputTokens(request: ChatRequest): Int {
        val prompt = request.systemPrompt.orEmpty()
        val turns = request.turns.sumOf { TokenUsage.estimateTokens(it.content) }
        return TokenUsage.estimateTokens(prompt) + turns
    }

    private fun buildUsage(
        candidate: Candidate,
        estimatedInputTokens: Int,
        outputChars: Int,
        wireUsage: WireEvent.Usage?
    ): TokenUsage {
        val inputTokens = wireUsage?.inputTokens?.takeIf { it > 0 } ?: estimatedInputTokens
        val outputTokens = wireUsage?.outputTokens?.takeIf { it > 0 }
            ?: TokenUsage.estimateTokensFromChars(outputChars)
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            estimatedCostUsd = candidate.model.costUsd(inputTokens, outputTokens),
            estimated = wireUsage == null
        )
    }

    companion object {
        const val TERMINAL_COOLDOWN_MILLIS = 30 * 60_000L
    }
}
