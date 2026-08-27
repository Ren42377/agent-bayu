package dev.agentbayu.app.domain

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ConversationRepository {

    private val nextId = AtomicLong(1L)
    private val state = MutableStateFlow<List<ChatMessage>>(emptyList())

    val messages: StateFlow<List<ChatMessage>> = state.asStateFlow()

    fun append(author: MessageAuthor, text: String): ChatMessage {
        val message = ChatMessage(id = nextId.getAndIncrement(), author = author, text = text)
        state.update { current -> current + message }
        return message
    }

    fun clear() {
        state.value = emptyList()
    }
}
