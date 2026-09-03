package dev.agentbayu.app.domain

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.serialization.json.Json

class ConversationStore(private val storage: EncryptedStorage) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun loadIndex(): SessionIndexFile {
        val raw = storage.read(INDEX_FILE_NAME) ?: return SessionIndexFile()
        return try {
            json.decodeFromString(SessionIndexFile.serializer(), raw)
        } catch (error: IllegalArgumentException) {
            SessionIndexFile()
        }
    }

    fun saveIndex(index: SessionIndexFile) {
        storage.write(INDEX_FILE_NAME, json.encodeToString(SessionIndexFile.serializer(), index))
    }

    fun loadSession(sessionId: String): List<ChatMessage> {
        val raw = storage.read(sessionFileName(sessionId)) ?: return emptyList()
        return ConversationCodec.decode(raw)
    }

    fun saveSession(sessionId: String, messages: List<ChatMessage>) {
        val trimmed = ConversationCodec.trim(messages.filter { !it.streaming || it.text.isNotBlank() })
        if (trimmed.isEmpty()) {
            storage.delete(sessionFileName(sessionId))
            return
        }
        storage.write(sessionFileName(sessionId), ConversationCodec.encode(trimmed))
    }

    fun deleteSessionFile(sessionId: String) {
        storage.delete(sessionFileName(sessionId))
    }

    fun loadLegacy(): List<ChatMessage> {
        val raw = storage.read(LEGACY_FILE_NAME) ?: return emptyList()
        return ConversationCodec.decode(raw)
    }

    fun deleteLegacy() {
        storage.delete(LEGACY_FILE_NAME)
    }

    private fun sessionFileName(sessionId: String): String = SESSION_PREFIX + sessionId

    companion object {
        const val INDEX_FILE_NAME = "sessions.bin"
        const val LEGACY_FILE_NAME = "conversation.bin"
        private const val SESSION_PREFIX = "session_"
    }
}
