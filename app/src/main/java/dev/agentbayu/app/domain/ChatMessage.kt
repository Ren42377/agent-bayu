package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.RouteDecision
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.serialization.Serializable

@Serializable
enum class MessageAuthor {
    USER,
    AGENT
}

@Serializable
data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val text: String,
    val route: RouteDecision? = null,
    val usage: TokenUsage? = null,
    val streaming: Boolean = false
)
