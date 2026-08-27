package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class ConnectionStore(
    private val storage: EncryptedStorage,
    private val clock: Clock = RealClock
) : ConnectionSource {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())

    override val connections: StateFlow<List<Connection>> = state.asStateFlow()

    fun find(connectionId: String): Connection? = state.value.firstOrNull { it.id == connectionId }

    fun newId(): String = ID_PREFIX + clock.nowMillis().toString(RADIX)

    fun upsert(connection: Connection) {
        val current = state.value
        val index = current.indexOfFirst { it.id == connection.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { set(index, connection) }
        } else {
            current + connection.copy(createdAtMillis = clock.nowMillis())
        }
        state.value = updated
        persist(updated)
    }

    fun remove(connectionId: String) {
        val updated = state.value.filterNot { it.id == connectionId }
        if (updated.size == state.value.size) return
        state.value = updated
        persist(updated)
    }

    fun setEnabled(connectionId: String, enabled: Boolean) {
        val target = find(connectionId) ?: return
        if (target.enabled == enabled) return
        upsert(target.copy(enabled = enabled))
    }

    override fun markHealth(connectionId: String, health: ConnectionHealth, detail: String?) {
        val target = find(connectionId) ?: return
        if (target.health == health && target.healthDetail == detail) return
        upsert(target.copy(health = health, healthDetail = detail))
    }

    fun clear() {
        state.value = emptyList()
        storage.delete(FILE_NAME)
    }

    private fun load(): List<Connection> {
        val raw = storage.read(FILE_NAME) ?: return emptyList()
        return try {
            json.decodeFromString(ConnectionFile.serializer(), raw).connections
        } catch (error: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun persist(connections: List<Connection>) {
        if (connections.isEmpty()) {
            storage.delete(FILE_NAME)
            return
        }
        storage.write(
            FILE_NAME,
            json.encodeToString(ConnectionFile.serializer(), ConnectionFile(connections = connections))
        )
    }

    companion object {
        const val FILE_NAME = "connections.bin"
        private const val ID_PREFIX = "conn-"
        private const val RADIX = 36
    }
}
