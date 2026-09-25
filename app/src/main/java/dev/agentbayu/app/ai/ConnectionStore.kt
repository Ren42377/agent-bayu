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

    fun setDiscoveredModels(connectionId: String, models: List<String>) {
        val target = find(connectionId) ?: return
        val cleaned = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (target.discoveredModels == cleaned) return
        upsert(target.copy(discoveredModels = cleaned))
    }

    fun addCustomModel(connectionId: String, modelId: String) {
        val target = find(connectionId) ?: return
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in target.customModels) return
        upsert(target.copy(customModels = target.customModels + trimmed))
    }

    fun removeCustomModel(connectionId: String, modelId: String) {
        val target = find(connectionId) ?: return
        if (modelId !in target.customModels) return
        upsert(target.copy(customModels = target.customModels - modelId))
    }

    fun applyAccountLabel(connectionId: String, email: String?, providerLabel: String?) {
        val target = find(connectionId) ?: return
        val trimmed = email?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        if (target.label.contains(trimmed, ignoreCase = true)) return
        val base = providerLabel?.trim()?.takeIf { it.isNotEmpty() } ?: target.label
        upsert(target.copy(label = base + ACCOUNT_LABEL_SEPARATOR + trimmed))
    }

    fun nextLabelFor(providerId: String, baseLabel: String): String {
        val taken = state.value.count { it.providerId == providerId }
        if (taken == 0) return baseLabel
        return baseLabel + ACCOUNT_LABEL_SEPARATOR + (taken + 1)
    }

    fun migrateModels(catalog: ProviderCatalog) {
        state.value.forEach { connection ->
            val provider = catalog.find(connection.providerId) ?: return@forEach
            val current = connection.model.trim()
            if (current.isEmpty()) return@forEach
            val retired = isRetiredModel(provider, current)
            val deprecated = provider.model(current)?.deprecated == true
            if (!retired && !deprecated) return@forEach
            val target = migrationTarget(provider, current) ?: return@forEach
            val effort = connection.effort ?: splitEffortSuffix(current)?.second
            upsert(connection.copy(model = target, effort = effort))
        }
    }

    private fun migrationTarget(provider: ProviderEntry, current: String): String? {
        val fallback = provider.selectableModels.firstOrNull()?.id ?: return null
        val candidate = normalizeDiscoveredModelId(provider, current).trim()
        if (candidate.isEmpty()) return fallback
        if (isRetiredModel(provider, candidate)) return fallback
        if (provider.model(candidate)?.deprecated == true) return fallback
        return candidate
    }

    fun setContextLength(connectionId: String, contextLength: Int?) {
        val target = find(connectionId) ?: return
        val normalized = contextLength?.takeIf { it > 0 }
        if (target.contextLengthOverride == normalized) return
        upsert(target.copy(contextLengthOverride = normalized))
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
        const val ACCOUNT_LABEL_SEPARATOR = " - "
        private const val ID_PREFIX = "conn-"
        private const val RADIX = 36
    }
}
