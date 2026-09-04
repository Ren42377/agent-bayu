package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.coroutines.flow.Flow

data class AgentRequest(
    val prompt: String,
    val screenContext: String? = null,
    val history: List<ChatMessage> = emptyList(),
    val attachments: List<MessageAttachment> = emptyList()
)

sealed interface AgentEvent {
    data class Delta(val text: String) : AgentEvent

    data class Detail(val detail: ReplyDetail) : AgentEvent

    data class ToolStarted(val name: String, val label: String) : AgentEvent

    data class ToolFinished(val name: String, val ok: Boolean) : AgentEvent

    data class Completed(val detail: ReplyDetail?, val usage: TokenUsage?) : AgentEvent

    data class Failed(val message: String) : AgentEvent
}

interface AgentEngine {
    fun reply(request: AgentRequest): Flow<AgentEvent>
}
