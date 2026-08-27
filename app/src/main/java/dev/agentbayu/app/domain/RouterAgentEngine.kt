package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.AiRouter
import dev.agentbayu.app.ai.RouterEvent
import dev.agentbayu.app.ai.RouterFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RouterCopy(
    val noConnection: String,
    val noCandidateAvailable: String,
    val allCandidatesFailed: String
)

class RouterAgentEngine(
    private val router: AiRouter,
    private val contextBuilder: ContextBuilder,
    private val copy: RouterCopy
) : AgentEngine {

    override fun reply(request: AgentRequest): Flow<AgentEvent> {
        val chatRequest = contextBuilder.build(request)
        return router.stream(chatRequest).map { event ->
            when (event) {
                is RouterEvent.Route -> AgentEvent.Route(event.decision)
                is RouterEvent.Delta -> AgentEvent.Delta(event.text)
                is RouterEvent.Completed -> AgentEvent.Completed(event.decision, event.usage)
                is RouterEvent.Failed -> AgentEvent.Failed(messageFor(event.reason))
            }
        }
    }

    private fun messageFor(reason: RouterFailure): String = when (reason) {
        RouterFailure.NO_CONNECTION -> copy.noConnection
        RouterFailure.NO_CANDIDATE_AVAILABLE -> copy.noCandidateAvailable
        RouterFailure.ALL_CANDIDATES_FAILED -> copy.allCandidatesFailed
    }
}
