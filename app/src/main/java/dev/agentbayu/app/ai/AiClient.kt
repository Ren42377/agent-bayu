package dev.agentbayu.app.ai

import android.util.Log
import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.WireEvent
import dev.agentbayu.app.ai.tools.ToolCall
import dev.agentbayu.app.ai.tools.ToolSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

sealed interface ReplyEvent {
    data class Started(val detail: ReplyDetail) : ReplyEvent

    data class Delta(val text: String) : ReplyEvent

    data class ToolUse(val call: ToolCall) : ReplyEvent

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
    private val clock: Clock = RealClock,
    private val projects: ProjectResolver = ReadyProjectResolver,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {

    fun stream(request: ChatRequest, countRequest: Boolean = true): Flow<ReplyEvent> = flow {
        when (val resolution = activeProvider.resolve()) {
            is ActiveResolution.Unavailable -> {
                logStore.warning(SOURCE, "No usable connection", resolution.problem.name)
                emit(ReplyEvent.Unavailable(resolution.problem))
            }

            is ActiveResolution.Ready -> streamCandidate(
                resolution.candidate,
                request,
                countRequest
            )
        }
    }

    private suspend fun FlowCollector<ReplyEvent>.streamCandidate(
        candidate: Candidate,
        request: ChatRequest,
        countRequest: Boolean
    ) {
        val detail = detailOf(candidate)
        val route = candidate.provider.id + " " + candidate.model.id
        val adapter = adapters[candidate.wireFormat]
        if (adapter == null) {
            logStore.error(SOURCE, "Unsupported wire format", route)
            emit(ReplyEvent.Failed(unsupportedWireFormat(), detail))
            return
        }

        val effective = request.copy(
            turns = fitToContext(visionAware(candidate, request), candidate.model),
            maxOutputTokens = candidate.provider.clampOutputTokens(request.maxOutputTokens),
            effort = candidate.effort,
            tools = toolsFor(candidate, request)
        )
        val connectionId = candidate.connection.id
        val credential = credentials.resolve(candidate)
        val startedAt = clock.nowMillis()
        usageTracker.beginRequest(connectionId, countRequest)
        logStore.info(SOURCE, "Request started", route)

        val routed = when (val resolution = projects.resolve(candidate, credential)) {
            is ProjectResolution.Ready -> resolution.candidate
            is ProjectResolution.Failed -> {
                reportFailure(
                    candidate,
                    resolution.failure,
                    detail.copy(totalMillis = clock.nowMillis() - startedAt)
                )
                return
            }
        }

        var firstTokenMillis = 0L
        var outputChars = 0
        var toolChars = 0
        var toolCalls = 0
        var wireUsage: WireEvent.Usage? = null
        var failure: RouteFailure? = null
        var attempt = 0

        suspend fun markFirstToken() {
            if (firstTokenMillis != 0L) return
            firstTokenMillis = (clock.nowMillis() - startedAt).coerceAtLeast(1L)
            usageTracker.recordFirstToken(connectionId, firstTokenMillis)
            emit(ReplyEvent.Started(detail.copy(firstTokenMillis = firstTokenMillis)))
        }

        while (true) {
            attempt += 1
            failure = null
            wireUsage = null
            adapter.stream(routed, credential.token, effective, credential.headers)
                .collect { event ->
                    when (event) {
                        is WireEvent.Delta -> {
                            markFirstToken()
                            outputChars += event.text.length
                            emit(ReplyEvent.Delta(event.text))
                        }

                        is WireEvent.ToolUse -> {
                            markFirstToken()
                            toolCalls += 1
                            toolChars += event.call.name.length + event.call.arguments.length
                            emit(ReplyEvent.ToolUse(event.call))
                        }

                        is WireEvent.Usage -> wireUsage = event
                        is WireEvent.Failure -> failure = event.failure
                        WireEvent.Done -> Unit
                    }
                }

            if (outputChars > 0 || toolCalls > 0) break
            val pending = failure ?: emptyReply()
            val wait = retryDelayFor(pending, attempt) ?: break
            logStore.warning(SOURCE, "Retrying request", route + " " + pending.logLabel)
            pause(wait)
        }

        val reported = failure ?: if (outputChars == 0 && toolCalls == 0) emptyReply() else null
        val complete = detail.copy(
            firstTokenMillis = firstTokenMillis,
            totalMillis = clock.nowMillis() - startedAt
        )
        if (reported != null) {
            reportFailure(candidate, reported, complete)
            return
        }

        val usage = usageOf(candidate, effective, wireUsage, outputChars + toolChars)
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

    private suspend fun FlowCollector<ReplyEvent>.reportFailure(
        candidate: Candidate,
        failure: RouteFailure,
        detail: ReplyDetail
    ) {
        val connectionId = candidate.connection.id
        val route = candidate.provider.id + " " + candidate.model.id
        usageTracker.recordFailure(connectionId, failure)
        healthFor(failure)?.let { health ->
            connections.markHealth(connectionId, health, failure.logLabel)
        }
        logStore.error(SOURCE, failure.message, route + " " + failure.logLabel)
        Log.e(TAG, "Reply failed: " + route + " " + failure.logLabel)
        emit(ReplyEvent.Failed(failure, detail))
    }

    private fun retryDelayFor(failure: RouteFailure, attempt: Int): Long? {
        if (attempt >= MAX_ATTEMPTS) return null
        return when (failure.kind) {
            FailureKind.RETRYABLE -> failure.retryAfterMillis
                ?.coerceIn(0L, MAX_RETRY_DELAY_MILLIS)
                ?: (BASE_RETRY_DELAY_MILLIS shl (attempt - 1))

            FailureKind.COOLDOWN -> if (attempt >= MAX_COOLDOWN_ATTEMPTS) {
                null
            } else {
                failure.retryAfterMillis?.takeIf { it in 0L..MAX_RETRY_DELAY_MILLIS }
            }

            else -> null
        }
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

    private fun visionAware(candidate: Candidate, request: ChatRequest): ChatRequest {
        if (candidate.supportsVision) return request
        if (request.turns.none { it.images.isNotEmpty() }) return request
        return request.copy(turns = request.turns.map { it.copy(images = emptyList()) })
    }

    private fun toolsFor(candidate: Candidate, request: ChatRequest): List<ToolSpec> {
        if (request.tools.isEmpty()) return request.tools
        if (!candidate.supportsTools) return emptyList()
        if (candidate.supportsVision) return request.tools
        return request.tools.filterNot { it.needsVision }
    }

    private fun estimateInputTokens(request: ChatRequest): Int =
        TokenUsage.estimateTokens(request.systemPrompt.orEmpty()) +
            request.turns.sumOf { turnTokenCost(it) }

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
        private const val MAX_ATTEMPTS = 3
        private const val MAX_COOLDOWN_ATTEMPTS = 2
        private const val BASE_RETRY_DELAY_MILLIS = 1_000L
        private const val MAX_RETRY_DELAY_MILLIS = 8_000L
    }
}
