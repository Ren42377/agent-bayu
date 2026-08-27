package dev.agentbayu.app.domain

enum class MessageAuthor {
    USER,
    AGENT
}

data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val text: String
)
