package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.serialization.SerialName
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
sealed interface MessageSegment {

    @Serializable
    @SerialName("thinking")
    data class Thinking(
        val text: String = "",
        val millis: Long = 0L,
        val done: Boolean = false
    ) : MessageSegment

    @Serializable
    @SerialName("prose")
    data class Prose(val text: String) : MessageSegment

    @Serializable
    @SerialName("tool")
    data class Tool(
        val name: String,
        val label: String = "",
        val running: Boolean = true,
        val ok: Boolean = false
    ) : MessageSegment

    @Serializable
    @SerialName("auto_approve")
    data class AutoApprove(val reason: String) : MessageSegment
}

@Serializable
data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val text: String,
    val detail: ReplyDetail? = null,
    val usage: TokenUsage? = null,
    val streaming: Boolean = false,
    val attachments: List<MessageAttachment> = emptyList(),
    val segments: List<MessageSegment> = emptyList()
) {
    val displaySegments: List<MessageSegment>
        get() = if (segments.isNotEmpty() || text.isEmpty()) {
            segments
        } else {
            listOf(MessageSegment.Prose(text))
        }
}
