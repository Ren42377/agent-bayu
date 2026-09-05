package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ConnectionStore(
    private val storage: EncryptedStorage,
    private val clock: Clock = RealClock
) : ConnectionSource, ProjectIdSink {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val persistScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val restored = load()
    private val state = MutableStateFlow(restored.connections)
    private val activeState = MutableStateFlow(restored.activeConnectionId)

    override val connections: StateFlow<List<Connection>> = state.asStateFlow()

    override val activeConnectionId: StateFlow<String?> = activeState.asStateFlow()

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
        persist(updated, activeState.value)
    }

    fun remove(connectionId: String) {
        val updated = state.value.filterNot { it.id == connectionId }
        if (updated.size == state.value.size) return
        state.value = updated
        if (activeState.value == connectionId) activeState.value = null
        persist(updated, activeState.value)
    }

    fun setModel(connectionId: String, model: String) {
        val target = find(connectionId) ?: return
        val trimmed = model.trim()
        if (trimmed.isEmpty() || target.model == trimmed) return
        upsert(target.copy(model = trimmed))
    }

    fun setEffort(connectionId: String, effort: ReasoningEffort?) {
        val target = find(connectionId) ?: return
        if (target.effort == effort) return
        upsert(target.copy(effort = effort))
    }

    override fun setProjectId(connectionId: String, projectId: String?) {
        val target = find(connectionId) ?: return
        val trimmed = projectId?.trim()?.takeIf { it.isNotEmpty() }
        if (target.projectId == trimmed) return
        upsert(target.copy(projectId = trimmed))
    }

    fun setActive(connectionId: String) {
        if (activeState.value == connectionId) return
        if (find(connectionId) == null) return
        activeState.value = connectionId
        persist(state.value, connectionId)
    }

    override fun markHealth(connectionId: String, health: ConnectionHealth, detail: String?) {
        val target = find(connectionId) ?: return
        if (target.health == health && target.healthDetail == detail) return
        upsert(target.copy(health = health, healthDetail = detail))
    }

    fun clear() {
        state.value = emptyList()
        activeState.value = null
        persistScope.launch { storage.delete(FILE_NAME) }
    }

    private fun load(): ConnectionFile {
        val raw = storage.read(FILE_NAME) ?: return ConnectionFile()
        return try {
            json.decodeFromString(ConnectionFile.serializer(), raw)
        } catch (error: IllegalArgumentException) {
            ConnectionFile()
        }
    }

    private fun persist(connections: List<Connection>, activeConnectionId: String?) {
        if (connections.isEmpty()) {
            persistScope.launch { storage.delete(FILE_NAME) }
            return
        }
        val payload = json.encodeToString(
            ConnectionFile.serializer(),
            ConnectionFile(connections = connections, activeConnectionId = activeConnectionId)
        )
        persistScope.launch { storage.write(FILE_NAME, payload) }
    }

    companion object {
        const val FILE_NAME = "connections.bin"
        private const val ID_PREFIX = "conn-"
        private const val RADIX = 36
    }
}
