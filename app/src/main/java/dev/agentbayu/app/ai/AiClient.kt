package dev.agentbayu.app.ai

import android.util.Log
import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.WireEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

sealed interface ReplyEvent {
    data class Started(val detail: ReplyDetail) : ReplyEvent

    data class Delta(val text: String) : ReplyEvent

    data class Completed(val detail: ReplyDetail, val usage: TokenUsage) : ReplyEvent

    data class Unavailable(val problem: ActiveProviderProblem) : ReplyEvent

    data class Failed(val failure: RouteFailure, val detail: ReplyDetail) : ReplyEvent
}

class AiClient(
    private val activeProvider: ActiveProvider,
    private val connections: ConnectionSource,
    private val credentials: CredentialProvider,
    private val adapters: Map<WireFormat, ChatAdapter>,
    private val usageTracker: UsageTracker,
    private val logStore: LogStore,
    private val clock: Clock = RealClock
) {

    fun stream(request: ChatRequest): Flow<ReplyEvent> = flow {
        when (val resolution = activeProvider.resolve()) {
            is ActiveResolution.Unavailable -> {
                logStore.warning(SOURCE, "No usable connection", resolution.problem.name)
                emit(ReplyEvent.Unavailable(resolution.problem))
            }

            is ActiveResolution.Ready -> streamCandidate(resolution.candidate, request)
        }
    }

    private suspend fun FlowCollector<ReplyEvent>.streamCandidate(
        candidate: Candidate,
        request: ChatRequest
    ) {
        val detail = detailOf(candidate)
        val route = candidate.provider.id + " " + candidate.model.id
        val adapter = adapters[candidate.provider.wireFormat]
        if (adapter == null) {
            logStore.error(SOURCE, "Unsupported wire format", route)
            emit(ReplyEvent.Failed(unsupportedWireFormat(), detail))
            return
        }

        val effective = request.copy(
            turns = fitToContext(request, candidate.model),
            maxOutputTokens = candidate.provider.clampOutputTokens(request.maxOutputTokens),
            effort = candidate.effort
        )
        val connectionId = candidate.connection.id
        val credential = credentials.resolve(candidate)
        val startedAt = clock.nowMillis()
        usageTracker.beginRequest(connectionId)
        logStore.info(SOURCE, "Request started", route)

        var firstTokenMillis = 0L
        var outputChars = 0
        var wireUsage: WireEvent.Usage? = null
        var failure: RouteFailure? = null

        adapter.stream(candidate, credential.token, effective, credential.headers).collect { event ->
            when (event) {
                is WireEvent.Delta -> {
                    if (firstTokenMillis == 0L) {
                        firstTokenMillis = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
                        usageTracker.recordFirstToken(connectionId, firstTokenMillis)
                        emit(ReplyEvent.Started(detail.copy(firstTokenMillis = firstTokenMillis)))
                    }
                    outputChars += event.text.length
                    emit(ReplyEvent.Delta(event.text))
                }

                is WireEvent.Usage -> wireUsage = event
                is WireEvent.Failure -> failure = event.failure
                WireEvent.Done -> Unit
            }
        }

        val reported = failure ?: if (outputChars == 0) emptyReply() else null
        val complete = detail.copy(
            firstTokenMillis = firstTokenMillis,
            totalMillis = clock.nowMillis() - startedAt
        )
        if (reported != null) {
            usageTracker.recordFailure(connectionId, reported)
            healthFor(reported)?.let { health ->
                connections.markHealth(connectionId, health, reported.logLabel)
            }
            logStore.error(
                SOURCE,
                reported.message,
                route + " " + reported.logLabel
            )
            Log.e(
                TAG,
                "Reply failed: provider=" + candidate.provider.id +
                    " model=" + candidate.model.id + " " + reported.logLabel
            )
            emit(ReplyEvent.Failed(reported, complete))
            return
        }

        val usage = usageOf(candidate, effective, wireUsage, outputChars)
        usageTracker.recordSuccess(connectionId, usage)
        connections.markHealth(connectionId, ConnectionHealth.READY, null)
        logStore.info(
            SOURCE,
            "Reply completed",
            route + " " + complete.totalMillis + " ms, " +
                usage.outputTokens + " output tokens"
        )
        emit(ReplyEvent.Completed(complete, usage))
    }

    private fun detailOf(candidate: Candidate): ReplyDetail = ReplyDetail(
        providerId = candidate.provider.id,
        providerLabel = candidate.provider.label,
        model = candidate.model.id,
        connectionId = candidate.connection.id,
        connectionLabel = candidate.connection.label,
        authKind = candidate.provider.authKind
    )

    private fun usageOf(
        candidate: Candidate,
        request: ChatRequest,
        wireUsage: WireEvent.Usage?,
        outputChars: Int
    ): TokenUsage {
        val reportedInput = wireUsage?.inputTokens?.takeIf { it > 0 }
        val reportedOutput = wireUsage?.outputTokens?.takeIf { it > 0 }
        val inputTokens = reportedInput ?: estimateInputTokens(request)
        val outputTokens = reportedOutput ?: TokenUsage.estimateTokensFromChars(outputChars)
        val estimated = reportedInput == null || reportedOutput == null
        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            estimatedCostUsd = candidate.model.costUsd(inputTokens, outputTokens),
            estimated = estimated
        )
    }

    private fun estimateInputTokens(request: ChatRequest): Int =
        TokenUsage.estimateTokens(request.systemPrompt.orEmpty()) +
            request.turns.sumOf { TokenUsage.estimateTokens(it.content) }

    private fun healthFor(failure: RouteFailure): ConnectionHealth? = when {
        failure.statusCode == UNAUTHORIZED_STATUS -> ConnectionHealth.NEEDS_KEY
        failure.kind == FailureKind.TERMINAL -> ConnectionHealth.NEEDS_ATTENTION
        else -> null
    }

    private fun unsupportedWireFormat(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "unsupported provider"
    )

    private fun emptyReply(): RouteFailure = RouteFailure(
        kind = FailureKind.RETRYABLE,
        message = "no content"
    )

    companion object {
        private const val TAG = "AgentBayu"
        private const val SOURCE = "AiClient"
        private const val UNAUTHORIZED_STATUS = 401
    }
}
