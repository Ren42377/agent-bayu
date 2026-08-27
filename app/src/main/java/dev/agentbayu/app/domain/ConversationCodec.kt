package dev.agentbayu.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ConversationFile(
    val version: Int = 1,
    val messages: List<ChatMessage> = emptyList()
)

object ConversationCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(messages: List<ChatMessage>): String =
        json.encodeToString(ConversationFile.serializer(), ConversationFile(messages = messages))

    fun decode(raw: String): List<ChatMessage> = try {
        json.decodeFromString(ConversationFile.serializer(), raw).messages
    } catch (error: IllegalArgumentException) {
        emptyList()
    }

    fun trim(
        messages: List<ChatMessage>,
        maxMessages: Int = MAX_MESSAGES,
        maxChars: Int = MAX_CHARS
    ): List<ChatMessage> {
        val limited = if (messages.size > maxMessages) messages.takeLast(maxMessages) else messages
        var total = limited.sumOf { it.text.length }
        if (total <= maxChars) return limited
        val window = ArrayDeque(limited)
        while (window.size > 1 && total > maxChars) {
            total -= window.removeFirst().text.length
        }
        return window.toList()
    }

    const val MAX_MESSAGES = 200
    const val MAX_CHARS = 512 * 1024
}
