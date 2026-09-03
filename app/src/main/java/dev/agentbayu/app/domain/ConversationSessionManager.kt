package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConversationSessionManager(
    private val store: ConversationStore,
    private val repository: ConversationRepository,
    private val attachments: Attachments,
    private val clock: Clock = RealClock
) {

    private val mutex = Mutex()
    private val sessionsState = MutableStateFlow<List<ChatSessionMeta>>(emptyList())
    private val activeState = MutableStateFlow<String?>(null)

    val sessions: StateFlow<List<ChatSessionMeta>> = sessionsState.asStateFlow()
    val activeSessionId: StateFlow<String?> = activeState.asStateFlow()

    @Volatile
    private var cancelStreaming: (() -> Unit)? = null

    private var scope: CoroutineScope? = null

    fun bindCancel(block: () -> Unit) {
        cancelStreaming = block
    }

    fun attach(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            val restored = withContext(Dispatchers.IO) {
                mutex.withLock { initializeLocked() }
            }
            repository.restore(restored)
            repository.messages.collectLatest { snapshot ->
                delay(DEBOUNCE_MILLIS)
                mutex.withLock {
                    withContext(Dispatchers.IO) { persistSnapshotLocked(snapshot) }
                }
            }
        }
    }

    fun newSession() {
        switchSession {
            val snapshot = repository.messages.value
            if (snapshot.isEmpty()) {
                return@switchSession
            }
            persistSnapshotLocked(snapshot)
            repository.clear()
            createSessionLocked()
            saveIndexLocked()
        }
    }

    fun openSession(sessionId: String) {
        if (activeState.value == sessionId) {
            return
        }
        switchSession {
            if (sessionsState.value.none { it.id == sessionId }) {
                return@switchSession
            }
            persistSnapshotLocked(repository.messages.value)
            val messages = store.loadSession(sessionId)
            activeState.value = sessionId
            saveIndexLocked()
            repository.restore(messages)
        }
    }

    fun deleteSession(sessionId: String) {
        switchSession {
            val released = attachmentIdsOf(store.loadSession(sessionId))
            store.deleteSessionFile(sessionId)
            released.forEach { attachments.discard(it) }
            val remaining = sessionsState.value.filterNot { it.id == sessionId }
            if (activeState.value == sessionId) {
                repository.clear()
                val next = remaining.firstOrNull()
                if (next == null) {
                    activeState.value = null
                } else {
                    activeState.value = next.id
                    repository.restore(store.loadSession(next.id))
                }
            }
            sessionsState.value = remaining
            saveIndexLocked()
        }
    }

    private fun switchSession(block: suspend () -> Unit) {
        cancelStreaming?.invoke()
        val launchScope = scope ?: return
        launchScope.launch {
            mutex.withLock {
                withContext(Dispatchers.IO) { block() }
            }
        }
    }

    private fun initializeLocked(): List<ChatMessage> {
        var index = store.loadIndex()
        if (index.sessions.isEmpty()) {
            index = migrateLegacyLocked()
        }
        val sessions = index.sessions.sortedByDescending { it.updatedAtMillis }
        sessionsState.value = sessions
        val activeId = index.activeSessionId?.takeIf { id -> sessions.any { it.id == id } }
        activeState.value = activeId
        val active = if (activeId == null) emptyList() else store.loadSession(activeId)
        pruneAttachmentsLocked(sessions, activeId, active)
        return active
    }

    private fun pruneAttachmentsLocked(
        sessions: List<ChatSessionMeta>,
        activeId: String?,
        activeMessages: List<ChatMessage>
    ) {
        val retained = HashSet<String>()
        retained += attachmentIdsOf(activeMessages)
        sessions.forEach { meta ->
            if (meta.id != activeId) {
                retained += attachmentIdsOf(store.loadSession(meta.id))
            }
        }
        attachments.keep(retained)
    }

    private fun attachmentIdsOf(messages: List<ChatMessage>): Set<String> =
        messages.flatMap { message -> message.attachments.map { it.id } }.toSet()

    private fun migrateLegacyLocked(): SessionIndexFile {
        val legacy = store.loadLegacy()
        if (legacy.isEmpty()) {
            return SessionIndexFile()
        }
        val meta = ChatSessionMeta(
            id = newId(),
            title = legacy.firstOrNull { it.author == MessageAuthor.USER }?.text?.sanitize()
                ?.take(TITLE_MAX).orEmpty(),
            preview = legacy.lastOrNull()?.text?.sanitize()?.take(PREVIEW_MAX).orEmpty(),
            createdAtMillis = clock.nowMillis(),
            updatedAtMillis = clock.nowMillis()
        )
        store.saveSession(meta.id, legacy)
        store.deleteLegacy()
        val index = SessionIndexFile(activeSessionId = meta.id, sessions = listOf(meta))
        store.saveIndex(index)
        return index
    }

    private fun persistSnapshotLocked(messages: List<ChatMessage>) {
        val snapshot = messages.filter { !it.streaming || it.text.isNotBlank() }
        if (snapshot.isEmpty()) {
            return
        }
        val activeId = activeState.value ?: createSessionLocked().id
        val base = sessionsState.value.firstOrNull { it.id == activeId } ?: ChatSessionMeta(
            id = activeId,
            createdAtMillis = clock.nowMillis(),
            updatedAtMillis = clock.nowMillis()
        )
        val title = base.title.ifBlank {
            snapshot.firstOrNull { it.author == MessageAuthor.USER }?.text?.sanitize()
                ?.take(TITLE_MAX).orEmpty()
        }
        val preview = snapshot.lastOrNull()?.text?.sanitize()?.take(PREVIEW_MAX).orEmpty()
        replaceMeta(base.copy(title = title, preview = preview, updatedAtMillis = clock.nowMillis()))
        store.saveSession(activeId, snapshot)
        saveIndexLocked()
    }

    private fun createSessionLocked(): ChatSessionMeta {
        val meta = ChatSessionMeta(
            id = newId(),
            createdAtMillis = clock.nowMillis(),
            updatedAtMillis = clock.nowMillis()
        )
        sessionsState.value = sessionsState.value + meta
        activeState.value = meta.id
        return meta
    }

    private fun replaceMeta(meta: ChatSessionMeta) {
        sessionsState.value =
            (sessionsState.value.filterNot { it.id == meta.id } + meta)
                .sortedByDescending { it.updatedAtMillis }
    }

    private fun saveIndexLocked() {
        store.saveIndex(
            SessionIndexFile(activeSessionId = activeState.value, sessions = sessionsState.value)
        )
    }

    private fun newId(): String = ID_PREFIX + clock.nowMillis().toString(RADIX)

    private fun String.sanitize(): String {
        return replace('\n', ' ').replace('\r', ' ').trim()
    }

    companion object {
        const val DEBOUNCE_MILLIS = 1_000L
        const val TITLE_MAX = 64
        const val PREVIEW_MAX = 96
        private const val ID_PREFIX = "session-"
        private const val RADIX = 36
    }
}
