package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface KeySource {
    fun key(connectionId: String): String?
    fun hasKey(connectionId: String): Boolean
}

fun KeySource.secretFor(connection: Connection, provider: ProviderEntry): String? =
    key(connection.id) ?: provider.anonymousKey?.takeIf { it.isNotBlank() }

fun KeySource.secretFor(candidate: Candidate): String? =
    secretFor(candidate.connection, candidate.provider)

class CredentialStore(private val storage: EncryptedStorage) : KeySource {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    private val legacySerializer = MapSerializer(String.serializer(), String.serializer())
    private val entries = HashMap<String, Credential>()
    private var loaded = false

    override fun key(connectionId: String): String? = synchronized(entries) {
        ensureLoaded()
        entries[connectionId]?.secret?.takeIf { it.isNotBlank() }
    }

    override fun hasKey(connectionId: String): Boolean = key(connectionId) != null

    fun credential(connectionId: String): Credential? = synchronized(entries) {
        ensureLoaded()
        entries[connectionId]
    }

    fun put(connectionId: String, credential: Credential) {
        synchronized(entries) {
            ensureLoaded()
            if (credential.secret.isBlank()) {
                if (entries.remove(connectionId) != null) persist()
                return
            }
            entries[connectionId] = credential
            persist()
        }
    }

    fun putApiKey(connectionId: String, apiKey: String) {
        put(connectionId, Credential.ApiKey(apiKey.trim()))
    }

    fun remove(connectionId: String) {
        synchronized(entries) {
            ensureLoaded()
            if (entries.remove(connectionId) != null) persist()
        }
    }

    fun hint(connectionId: String): String? {
        val value = key(connectionId) ?: return null
        return Credential.hintOf(value)
    }

    fun preload() {
        synchronized(entries) {
            ensureLoaded()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = storage.read(FILE_NAME) ?: return
        val root = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (error: IllegalArgumentException) {
            null
        } ?: return
        if (root[ENTRIES_FIELD] is JsonObject) {
            restoreCurrent(raw)
        } else {
            restoreLegacy(raw)
        }
    }

    private fun restoreCurrent(raw: String) {
        val file = try {
            json.decodeFromString(CredentialFile.serializer(), raw)
        } catch (error: IllegalArgumentException) {
            return
        }
        entries.putAll(file.entries)
    }

    private fun restoreLegacy(raw: String) {
        val restored = try {
            json.decodeFromString(legacySerializer, raw)
        } catch (error: IllegalArgumentException) {
            return
        }
        restored.forEach { (connectionId, value) ->
            if (value.isNotBlank()) entries[connectionId] = Credential.ApiKey(value)
        }
        if (entries.isNotEmpty()) persist()
    }

    private fun persist() {
        if (entries.isEmpty()) {
            storage.delete(FILE_NAME)
            return
        }
        storage.write(
            FILE_NAME,
            json.encodeToString(CredentialFile.serializer(), CredentialFile(entries = entries.toMap()))
        )
    }

    companion object {
        const val FILE_NAME = "credentials.bin"
        private const val ENTRIES_FIELD = "entries"
    }
}
