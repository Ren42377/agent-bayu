package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ActiveProviderProblem
import dev.agentbayu.app.ai.AiClient
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.ReplyEvent
import dev.agentbayu.app.ai.RouteFailure
import dev.agentbayu.app.ai.TokenUsage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.tools.ToolCall
import dev.agentbayu.app.ai.tools.ToolRegistry
import dev.agentbayu.app.ai.tools.ToolResult
import dev.agentbayu.app.domain.tools.ToolIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ProviderCopy(
    val noConnection: String,
    val unknownProvider: String,
    val missingCredential: String,
    val unauthorized: String,
    val outOfCredit: String,
    val quotaExhausted: String,
    val rateLimited: String,
    val rateLimitedWait: String,
    val modelUnavailable: String,
    val serverError: String,
    val networkError: String,
    val genericError: String
)

class ProviderAgentEngine(
    private val client: AiClient,
    private val contextBuilder: ContextBuilder,
    private val copy: ProviderCopy,
    private val tools: ToolRegistry = ToolRegistry(),
    private val intent: ToolIntent = ToolIntent()
) : AgentEngine {

    override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
        intent.text = request.prompt
        var chatRequest = contextBuilder.build(request).copy(tools = tools.specs)
        var detail: ReplyDetail? = null
        var usage: TokenUsage? = null
        var pass = 0
        val seen = HashSet<String>()

        while (true) {
            pass += 1
            val calls = ArrayList<ToolCall>()
            val spoken = StringBuilder()
            var stopped = false

            client.stream(chatRequest, countRequest = pass == 1).collect { event ->
                when (event) {
                    is ReplyEvent.Started -> {
                        val merged = mergeDetail(detail, event.detail)
                        detail = merged
                        if (pass == 1) emit(AgentEvent.Detail(merged))
                    }

                    is ReplyEvent.Delta -> {
                        spoken.append(event.text)
                        emit(AgentEvent.Delta(event.text))
                    }

                    is ReplyEvent.ToolUse -> calls += event.call

                    is ReplyEvent.Completed -> {
                        detail = mergeDetail(detail, event.detail)
                        usage = mergeUsage(usage, event.usage)
                    }

                    is ReplyEvent.Unavailable -> {
                        stopped = true
                        emit(AgentEvent.Failed(messageFor(event.problem)))
                    }

                    is ReplyEvent.Failed -> {
                        stopped = true
                        detail = mergeDetail(detail, event.detail)
                        emit(AgentEvent.Failed(messageFor(event.failure)))
                    }
                }
            }

            if (stopped) return@flow
            if (calls.isEmpty() || pass >= MAX_PASSES) {
                emit(AgentEvent.Completed(detail, usage))
                return@flow
            }

            val results = ArrayList<ToolResult>(calls.size)
            for (call in calls) {
                emit(AgentEvent.ToolStarted(call.name, labelOf(call)))
                val result = if (seen.add(call.name + "|" + call.arguments)) {
                    tools.run(call)
                } else {
                    ToolResult(
                        callId = call.id,
                        name = call.name,
                        content = REPEATED_CALL,
                        isError = true
                    )
                }
                emit(AgentEvent.ToolFinished(call.name, !result.isError))
                results += result
            }

            chatRequest = chatRequest.copy(
                turns = chatRequest.turns +
                    ChatTurn(ChatRole.ASSISTANT, spoken.toString(), toolCalls = calls) +
                    results.map(::toolTurn),
                tools = if (pass + 1 >= MAX_PASSES) emptyList() else tools.specs
            )
        }
    }

    private fun toolTurn(result: ToolResult): ChatTurn = ChatTurn(
        role = ChatRole.TOOL,
        content = result.content,
        toolCallId = result.callId,
        toolName = result.name,
        toolFailed = result.isError
    )

    private fun labelOf(call: ToolCall): String {
        val arguments = call.arguments.trim()
        if (arguments.isEmpty() || arguments == EMPTY_ARGUMENTS) return call.name
        return call.name + " " + arguments.take(MAX_LABEL_CHARS)
    }

    private fun mergeDetail(current: ReplyDetail?, next: ReplyDetail): ReplyDetail {
        if (current == null) return next
        val firstToken = if (current.firstTokenMillis > 0L) {
            current.firstTokenMillis
        } else {
            next.firstTokenMillis
        }
        return next.copy(
            firstTokenMillis = firstToken,
            totalMillis = current.totalMillis + next.totalMillis
        )
    }

    private fun mergeUsage(current: TokenUsage?, next: TokenUsage): TokenUsage {
        if (current == null) return next
        return TokenUsage(
            inputTokens = current.inputTokens + next.inputTokens,
            outputTokens = current.outputTokens + next.outputTokens,
            estimatedCostUsd = sumCost(current.estimatedCostUsd, next.estimatedCostUsd),
            estimated = current.estimated || next.estimated
        )
    }

    private fun sumCost(current: Double?, next: Double?): Double? {
        if (current == null) return next
        if (next == null) return current
        return current + next
    }

    private fun messageFor(problem: ActiveProviderProblem): String = when (problem) {
        ActiveProviderProblem.NO_CONNECTION -> copy.noConnection
        ActiveProviderProblem.UNKNOWN_PROVIDER -> copy.unknownProvider
        ActiveProviderProblem.MISSING_CREDENTIAL -> copy.missingCredential
    }

    private fun messageFor(failure: RouteFailure): String {
        val status = failure.statusCode
        return when {
            status == STATUS_UNAUTHORIZED -> copy.unauthorized
            status == STATUS_PAYMENT_REQUIRED || status == STATUS_FORBIDDEN -> copy.outOfCredit
            status == STATUS_TOO_MANY_REQUESTS -> rateLimitMessage(failure)
            failure.kind == FailureKind.MODEL_LOCK -> copy.modelUnavailable
            status != null && status >= STATUS_SERVER_ERROR -> copy.serverError
            status == null -> copy.networkError
            else -> copy.genericError.format(status)
        }
    }

    private fun rateLimitMessage(failure: RouteFailure): String {
        if (failure.kind == FailureKind.TERMINAL) return copy.quotaExhausted
        val waitMillis = failure.retryAfterMillis ?: return copy.rateLimited
        val seconds = ((waitMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND).coerceAtLeast(1L)
        return copy.rateLimitedWait.format(seconds)
    }

    private companion object {
        const val STATUS_UNAUTHORIZED = 401
        const val STATUS_PAYMENT_REQUIRED = 402
        const val STATUS_FORBIDDEN = 403
        const val STATUS_TOO_MANY_REQUESTS = 429
        const val STATUS_SERVER_ERROR = 500
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_PASSES = 8
        const val MAX_LABEL_CHARS = 120
        const val EMPTY_ARGUMENTS = "{}"
        const val REPEATED_CALL =
            "Already called with the same arguments. Reuse the earlier result."
    }
}
