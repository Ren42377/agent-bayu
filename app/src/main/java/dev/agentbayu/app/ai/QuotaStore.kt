package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class QuotaStore(
    private val storage: EncryptedStorage,
    private val clock: Clock = RealClock
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())

    val snapshots: StateFlow<Map<String, QuotaSnapshot>> = state.asStateFlow()

    fun snapshotFor(connectionId: String): QuotaSnapshot? = state.value[connectionId]

    fun record(connectionId: String, headers: Map<String, String>) {
        val trimmed = connectionId.trim()
        if (trimmed.isEmpty()) return
        val parsed = QuotaParser.parse(headers) ?: return
        state.value = state.value + (trimmed to parsed.copy(updatedAtMillis = clock.nowMillis()))
        persist()
    }

    fun forget(connectionId: String) {
        if (!state.value.containsKey(connectionId)) return
        state.value = state.value - connectionId
        persist()
    }

    private fun load(): Map<String, QuotaSnapshot> {
        val raw = storage.read(FILE) ?: return emptyMap()
        return try {
            json.decodeFromString(QuotaFile.serializer(), raw).entries
        } catch (error: IllegalArgumentException) {
            emptyMap()
        }
    }

    private fun persist() {
        val file = QuotaFile(entries = state.value)
        storage.write(FILE, json.encodeToString(QuotaFile.serializer(), file))
    }

    companion object {
        const val FILE = "quota_snapshots.json"
    }
}

@Serializable
internal data class QuotaFile(
    val version: Int = 1,
    val entries: Map<String, QuotaSnapshot> = emptyMap()
)

object QuotaRecorder {

    private val lock = Any()

    @Volatile
    private var sink: ((String, Map<String, String>) -> Unit)? = null

    fun bind(listener: ((String, Map<String, String>) -> Unit)?) {
        synchronized(lock) { sink = listener }
    }

    fun record(connectionId: String?, headers: Map<String, String>) {
        val id = connectionId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (headers.isEmpty()) return
        val listener = synchronized(lock) { sink } ?: return
        listener.invoke(id, headers)
    }
}
