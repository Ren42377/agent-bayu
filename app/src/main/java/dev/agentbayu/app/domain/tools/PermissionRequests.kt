package dev.agentbayu.app.domain.tools

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PermissionKind(val wireValue: String) {
    STORAGE("storage"),
    NOTIFICATIONS("notifications"),
    EXACT_ALARMS("exact_alarms");

    companion object {
        fun of(value: String): PermissionKind? {
            val cleaned = value.trim().lowercase().replace(' ', '_').replace('-', '_')
            return entries.firstOrNull { it.wireValue == cleaned }
        }
    }
}

data class PermissionAsk(val id: Long, val kind: PermissionKind)

class PermissionRequests(private val granted: (PermissionKind) -> Boolean) {

    private val state = MutableStateFlow<PermissionAsk?>(null)
    private val tickets = AtomicLong(0L)
    private val lock = Mutex()

    @Volatile
    private var waiter: CompletableDeferred<Boolean>? = null

    val pending: StateFlow<PermissionAsk?> = state.asStateFlow()

    fun isGranted(kind: PermissionKind): Boolean = granted(kind)

    suspend fun request(kind: PermissionKind): Boolean {
        if (granted(kind)) return true
        return lock.withLock { ask(kind) }
    }

    fun resolve(asked: Boolean) {
        waiter?.complete(asked)
    }

    private suspend fun ask(kind: PermissionKind): Boolean {
        if (granted(kind)) return true
        val answer = CompletableDeferred<Boolean>()
        waiter = answer
        state.value = PermissionAsk(id = tickets.incrementAndGet(), kind = kind)
        return try {
            answer.await()
        } finally {
            waiter = null
            state.value = null
        }
    }
}
