package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.RouteDecision
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.coroutines.flow.Flow

data class AgentRequest(
    val prompt: String,
    val screenContext: String? = null,
    val history: List<ChatMessage> = emptyList()
)

sealed interface AgentEvent {
    data class Delta(val text: String) : AgentEvent

    data class Route(val decision: RouteDecision) : AgentEvent

    data class Completed(val decision: RouteDecision?, val usage: TokenUsage?) : AgentEvent

    data class Failed(val message: String) : AgentEvent
}

interface AgentEngine {
    fun reply(request: AgentRequest): Flow<AgentEvent>
}
