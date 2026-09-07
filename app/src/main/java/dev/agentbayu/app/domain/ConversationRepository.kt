package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ConversationTurn(
    val history: List<ChatMessage>,
    val prompt: ChatMessage,
    val placeholder: ChatMessage
)

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

    fun finishToolRun(id: Long, name: String, ok: Boolean, path: String = "") {
        mutate(id) { message ->
            val index = message.segments.indexOfLast { segment ->
                segment is MessageSegment.Tool && segment.name == name && segment.running
            }
            if (index < 0) {
                message
            } else {
                val segments = message.segments.toMutableList()
                val tool = segments[index] as MessageSegment.Tool
                segments[index] = tool.copy(running = false, ok = ok, path = path.ifEmpty { tool.path })
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
        if (messages.isEmpty()) {
            state.value = emptyList()
            return
        }
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

    fun contains(id: Long, author: MessageAuthor): Boolean =
        state.value.any { message -> message.id == id && message.author == author }

    fun allAttachmentIds(): Set<String> = state.value.attachmentIds()

    fun attachmentIdsFrom(id: Long): Set<String> {
        val current = state.value
        val index = current.indexOfFirst { message -> message.id == id }
        return if (index < 0) emptySet() else current.drop(index).attachmentIds()
    }

    fun restartFrom(
        id: Long,
        text: String,
        attachments: List<MessageAttachment>
    ): ConversationTurn? {
        val current = state.value
        val index = current.indexOfFirst { message -> message.id == id }
        if (index < 0 || current[index].author != MessageAuthor.USER) return null
        val history = current.take(index)
        return replaceBranch(
            history = history,
            text = text,
            attachments = attachments
        )
    }

    fun canRegenerateFrom(promptId: Long, replyId: Long): Boolean {
        val current = state.value
        val promptIndex = current.indexOfFirst { message -> message.id == promptId }
        val replyIndex = current.indexOfFirst { message -> message.id == replyId }
        return promptIndex >= 0 &&
            replyIndex == promptIndex + 1 &&
            current[promptIndex].author == MessageAuthor.USER &&
            current[replyIndex].author == MessageAuthor.AGENT
    }

    fun regenerateFrom(promptId: Long, replyId: Long): ConversationTurn? {
        val current = state.value
        if (!canRegenerateFrom(promptId, replyId)) return null
        val promptIndex = current.indexOfFirst { message -> message.id == promptId }
        val replyIndex = promptIndex + 1
        val prompt = current[promptIndex]
        val placeholder = agentPlaceholder()
        state.value = current.take(replyIndex) + placeholder
        return ConversationTurn(current.take(promptIndex), prompt, placeholder)
    }

    fun truncateFrom(id: Long) {
        state.update { current -> current.takeWhile { message -> message.id != id } }
    }

    fun truncateAfter(id: Long) {
        state.update { current ->
            val index = current.indexOfFirst { message -> message.id == id }
            if (index < 0) current else current.take(index + 1)
        }
    }

    fun clear() {
        state.value = emptyList()
    }

    private fun replaceBranch(
        history: List<ChatMessage>,
        text: String,
        attachments: List<MessageAttachment>
    ): ConversationTurn {
        val prompt = ChatMessage(
            id = nextId.getAndIncrement(),
            author = MessageAuthor.USER,
            text = text.trim(),
            attachments = attachments
        )
        val placeholder = agentPlaceholder()
        state.value = history + prompt + placeholder
        return ConversationTurn(history, prompt, placeholder)
    }

    private fun agentPlaceholder(): ChatMessage = ChatMessage(
        id = nextId.getAndIncrement(),
        author = MessageAuthor.AGENT,
        text = "",
        streaming = true
    )

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

private fun List<ChatMessage>.attachmentIds(): Set<String> =
    flatMap { message -> message.attachments.map { attachment -> attachment.id } }.toSet()

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
