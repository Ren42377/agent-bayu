package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.serialization.Serializable

@Serializable
enum class MessageAuthor {
    USER,
    AGENT
}

@Serializable
data class MessageAttachment(
    val id: String,
    val mimeType: String,
    val fileName: String? = null,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val text: String,
    val detail: ReplyDetail? = null,
    val usage: TokenUsage? = null,
    val streaming: Boolean = false,
    val attachments: List<MessageAttachment> = emptyList()
)
