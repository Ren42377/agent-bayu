package dev.agentbayu.app.domain.tools

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ToolApprovalKind {
    CREATE,
    WRITE,
    EDIT,
    DELETE,
    MOVE
}

enum class ToolApprovalDecision {
    ALLOW_ONCE,
    ALLOW_SESSION,
    DENY
}

enum class ToolApprovalMode {
    ASK,
    BYPASS
}

data class ToolApprovalRequest(
    val id: Long,
    val toolName: String,
    val kind: ToolApprovalKind,
    val path: String,
    val destination: String? = null,
    val preview: List<DiffLine> = emptyList()
) {
    val added: Int get() = TextDiff.added(preview)

    val removed: Int get() = TextDiff.removed(preview)

    val sessionKey: String get() = toolName + "|" + path + "|" + destination.orEmpty()
}

object ToolApprovalTicket {

    private val counter = AtomicLong(0L)

    fun next(): Long = counter.incrementAndGet()
}

interface ToolApprovalGate {
    suspend fun confirm(request: ToolApprovalRequest): ToolApprovalDecision
}

object OpenToolApprovalGate : ToolApprovalGate {
    override suspend fun confirm(request: ToolApprovalRequest): ToolApprovalDecision =
        ToolApprovalDecision.ALLOW_ONCE
}

class UiToolApprovalGate(
    private val bypassed: () -> Boolean = { false }
) : ToolApprovalGate {

    private val state = MutableStateFlow<ToolApprovalRequest?>(null)
    private val granted = ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()

    @Volatile
    private var waiter: CompletableDeferred<ToolApprovalDecision>? = null

    val pending: StateFlow<ToolApprovalRequest?> = state.asStateFlow()

    override suspend fun confirm(request: ToolApprovalRequest): ToolApprovalDecision {
        if (bypassed()) return ToolApprovalDecision.ALLOW_ONCE
        if (granted.contains(request.sessionKey)) return ToolApprovalDecision.ALLOW_SESSION
        return lock.withLock { ask(request) }
    }

    fun resolve(decision: ToolApprovalDecision) {
        waiter?.complete(decision)
    }

    fun releaseIfOpen(decision: ToolApprovalDecision) {
        waiter?.complete(decision)
    }

    fun bypassPending(decision: ToolApprovalDecision) {
        waiter?.complete(decision)
    }

    fun clearSession() {
        granted.clear()
    }

    private suspend fun ask(request: ToolApprovalRequest): ToolApprovalDecision {
        if (bypassed()) return ToolApprovalDecision.ALLOW_ONCE
        if (granted.contains(request.sessionKey)) return ToolApprovalDecision.ALLOW_SESSION
        val answer = CompletableDeferred<ToolApprovalDecision>()
        waiter = answer
        if (bypassed()) answer.complete(ToolApprovalDecision.ALLOW_ONCE)
        if (!answer.isCompleted) state.value = request
        val decision = try {
            answer.await()
        } finally {
            waiter = null
            state.value = null
        }
        if (decision == ToolApprovalDecision.ALLOW_SESSION) granted.add(request.sessionKey)
        return decision
    }
}

class ToolApprovalRouter(
    private val mode: () -> ToolApprovalMode,
    private val ask: ToolApprovalGate,
    private val bypass: ToolApprovalGate = OpenToolApprovalGate
) : ToolApprovalGate {

    override suspend fun confirm(request: ToolApprovalRequest): ToolApprovalDecision =
        when (mode()) {
            ToolApprovalMode.ASK -> ask.confirm(request)
            ToolApprovalMode.BYPASS -> bypass.confirm(request)
        }
}
