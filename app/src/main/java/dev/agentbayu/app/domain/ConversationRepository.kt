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
        mutate(id) { message ->
            message.copy(
                text = message.text + text,
                segments = message.segments.withProse(text)
            )
        }
    }

    fun appendThinking(id: Long, text: String) {
        if (text.isEmpty()) return
        mutate(id) { message -> message.copy(segments = message.segments.withThinking(text)) }
    }

    fun closeThinking(id: Long, millis: Long) {
        mutate(id) { message -> message.copy(segments = message.segments.closingThinking(millis)) }
    }

    fun replaceText(id: Long, text: String) {
        mutate(id) { message ->
            message.copy(text = text, segments = listOf(MessageSegment.Prose(text)))
        }
    }

    fun attachDetail(id: Long, detail: ReplyDetail) {
        mutate(id) { message -> message.copy(detail = detail) }
    }

    fun startToolRun(id: Long, name: String, label: String) {
        mutate(id) { message ->
            message.copy(
                segments = message.segments + MessageSegment.Tool(name = name, label = label)
            )
        }
    }

    fun finishToolRun(id: Long, name: String, ok: Boolean) {
        mutate(id) { message ->
            val index = message.segments.indexOfLast { segment ->
                segment is MessageSegment.Tool && segment.name == name && segment.running
            }
            if (index < 0) {
                message
            } else {
                val segments = message.segments.toMutableList()
                val tool = segments[index] as MessageSegment.Tool
                segments[index] = tool.copy(running = false, ok = ok)
                message.copy(segments = segments)
            }
        }
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
        mutate(id) { message ->
            if (message.streaming) {
                message.copy(streaming = false, segments = message.segments.settled())
            } else {
                message
            }
        }
    }

    fun restore(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val highestId = messages.maxOf { it.id }
        nextId.set(highestId + 1L)
        state.value = messages.map { message ->
            if (message.streaming) {
                message.copy(streaming = false, segments = message.segments.settled())
            } else {
                message
            }
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

private fun List<MessageSegment>.withProse(text: String): List<MessageSegment> {
    val last = lastOrNull()
    if (last is MessageSegment.Prose) {
        return dropLast(1) + last.copy(text = last.text + text)
    }
    return this + MessageSegment.Prose(text)
}

private fun List<MessageSegment>.withThinking(text: String): List<MessageSegment> {
    val last = lastOrNull()
    if (last is MessageSegment.Thinking && !last.done) {
        return dropLast(1) + last.copy(text = last.text + text)
    }
    return this + MessageSegment.Thinking(text = text)
}

private fun List<MessageSegment>.closingThinking(millis: Long): List<MessageSegment> {
    val index = indexOfLast { it is MessageSegment.Thinking && !it.done }
    if (index < 0) return this
    val segments = toMutableList()
    val open = segments[index] as MessageSegment.Thinking
    segments[index] = open.copy(millis = millis, done = true)
    return segments
}

private fun List<MessageSegment>.settled(): List<MessageSegment> = map { segment ->
    when {
        segment is MessageSegment.Thinking && !segment.done -> segment.copy(done = true)
        segment is MessageSegment.Tool && segment.running -> segment.copy(running = false)
        else -> segment
    }
}
