package dev.agentbayu.app.domain

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationStore(
    private val storage: EncryptedStorage,
    private val attachments: Attachments? = null
) {

    fun load(): List<ChatMessage> {
        val raw = storage.read(FILE_NAME) ?: return emptyList()
        return ConversationCodec.decode(raw)
    }

    fun save(messages: List<ChatMessage>) {
        val trimmed = ConversationCodec.trim(messages.filter { !it.streaming || it.text.isNotBlank() })
        attachments?.keep(
            trimmed.flatMap { message -> message.attachments.map { it.id } }.toSet()
        )
        if (trimmed.isEmpty()) {
            storage.delete(FILE_NAME)
            return
        }
        storage.write(FILE_NAME, ConversationCodec.encode(trimmed))
    }

    fun clear() {
        storage.delete(FILE_NAME)
        attachments?.keep(emptySet())
    }

    fun attach(scope: CoroutineScope, repository: ConversationRepository) {
        scope.launch {
            val restored = withContext(Dispatchers.IO) { load() }
            repository.restore(restored)
            repository.messages.collectLatest { snapshot ->
                delay(DEBOUNCE_MILLIS)
                withContext(Dispatchers.IO) { save(snapshot) }
            }
        }
    }

    companion object {
        const val FILE_NAME = "conversation.bin"
        const val DEBOUNCE_MILLIS = 1_000L
    }
}
