package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class CredentialVault(private val storage: EncryptedStorage) : KeySource {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val entries = HashMap<String, String>()
    private var loaded = false

    override fun key(connectionId: String): String? = synchronized(entries) {
        ensureLoaded()
        entries[connectionId]
    }

    override fun hasKey(connectionId: String): Boolean = synchronized(entries) {
        ensureLoaded()
        !entries[connectionId].isNullOrBlank()
    }

    fun put(connectionId: String, apiKey: String) {
        synchronized(entries) {
            ensureLoaded()
            val trimmed = apiKey.trim()
            if (trimmed.isEmpty()) {
                entries.remove(connectionId)
            } else {
                entries[connectionId] = trimmed
            }
            persist()
        }
    }

    fun remove(connectionId: String) {
        synchronized(entries) {
            ensureLoaded()
            if (entries.remove(connectionId) != null) persist()
        }
    }

    fun hint(connectionId: String): String? = synchronized(entries) {
        ensureLoaded()
        val value = entries[connectionId] ?: return null
        if (value.length <= HINT_LENGTH) return HINT_MASK
        HINT_MASK + value.takeLast(HINT_LENGTH)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = storage.read(FILE_NAME) ?: return
        val restored = try {
            json.decodeFromString(serializer, raw)
        } catch (error: IllegalArgumentException) {
            emptyMap()
        }
        entries.putAll(restored)
    }

    private fun persist() {
        if (entries.isEmpty()) {
            storage.delete(FILE_NAME)
            return
        }
        storage.write(FILE_NAME, json.encodeToString(serializer, entries.toMap()))
    }

    companion object {
        const val FILE_NAME = "credentials.bin"
        const val HINT_LENGTH = 4
        const val HINT_MASK = "****"
    }
}
