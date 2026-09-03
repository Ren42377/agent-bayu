package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConversationRepository {

    private val nextId = AtomicLong(1L)
    private val state = MutableStateFlow<List<ChatMessage>>(emptyList())

    val messages: StateFlow<List<ChatMessage>> = state.asStateFlow()

    fun append(
        author: MessageAuthor,
        text: String,
        streaming: Boolean = false,
        attachments: List<MessageAttachment> = emptyList()
    ): ChatMessage {
        val message = ChatMessage(
            id = nextId.getAndIncrement(),
            author = author,
            text = text,
            streaming = streaming,
            attachments = attachments
        )
        state.update { current -> current + message }
        return message
    }

    fun appendDelta(id: Long, text: String) {
        if (text.isEmpty()) return
        mutate(id) { message -> message.copy(text = message.text + text) }
    }

    fun replaceText(id: Long, text: String) {
        mutate(id) { message -> message.copy(text = text) }
    }

    fun attachDetail(id: Long, detail: ReplyDetail) {
        mutate(id) { message -> message.copy(detail = detail) }
    }

    fun complete(id: Long, detail: ReplyDetail?, usage: TokenUsage?) {
        mutate(id) { message ->
            message.copy(
                detail = detail ?: message.detail,
                usage = usage ?: message.usage,
                streaming = false
            )
        }
    }

    fun finishStreaming(id: Long) {
        mutate(id) { message -> if (message.streaming) message.copy(streaming = false) else message }
    }

    fun restore(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val highestId = messages.maxOf { it.id }
        nextId.set(highestId + 1L)
        state.value = messages.map { message ->
            if (message.streaming) message.copy(streaming = false) else message
        }
    }

    fun clear() {
        state.value = emptyList()
    }

    private fun mutate(id: Long, block: (ChatMessage) -> ChatMessage) {
        state.update { current ->
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) {
                current
            } else {
                current.toMutableList().apply { set(index, block(get(index))) }
            }
        }
    }
}
