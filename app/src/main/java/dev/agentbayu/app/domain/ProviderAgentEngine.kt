package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ActiveProviderProblem
import dev.agentbayu.app.ai.AiClient
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.ReplyEvent
import dev.agentbayu.app.ai.RouteFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val copy: ProviderCopy
) : AgentEngine {

    override fun reply(request: AgentRequest): Flow<AgentEvent> {
        val chatRequest = contextBuilder.build(request)
        return client.stream(chatRequest).map { event ->
            when (event) {
                is ReplyEvent.Started -> AgentEvent.Detail(event.detail)
                is ReplyEvent.Delta -> AgentEvent.Delta(event.text)
                is ReplyEvent.Completed -> AgentEvent.Completed(event.detail, event.usage)
                is ReplyEvent.Unavailable -> AgentEvent.Failed(messageFor(event.problem))
                is ReplyEvent.Failed -> AgentEvent.Failed(messageFor(event.failure))
            }
        }
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
    }
}
