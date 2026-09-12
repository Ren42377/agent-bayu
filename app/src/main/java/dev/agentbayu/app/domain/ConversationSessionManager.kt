package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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

class ConversationSessionManager internal constructor(
    private val store: ConversationStore,
    private val repository: ConversationRepository,
    private val discardAttachment: (String) -> Unit,
    private val keepAttachments: (Set<String>) -> Unit,
    private val clock: Clock = RealClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    constructor(
        store: ConversationStore,
        repository: ConversationRepository,
        attachments: Attachments,
        clock: Clock = RealClock
    ) : this(
        store = store,
        repository = repository,
        discardAttachment = attachments::discard,
        keepAttachments = attachments::keep,
        clock = clock,
        ioDispatcher = Dispatchers.IO
    )

    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val sessionCounter = AtomicLong(0L)
    private val sessionsState = MutableStateFlow<List<ChatSessionMeta>>(emptyList())
    private val activeState = MutableStateFlow<String?>(null)
    private val incognitoState = MutableStateFlow(false)
    private val incognitoTokenState = MutableStateFlow(0L)
    private val switchingState = MutableStateFlow(false)
    private val switchPending = AtomicBoolean(false)

    val sessions: StateFlow<List<ChatSessionMeta>> = sessionsState.asStateFlow()
    val activeSessionId: StateFlow<String?> = activeState.asStateFlow()
    val incognito: StateFlow<Boolean> = incognitoState.asStateFlow()
    val incognitoToken: StateFlow<Long> = incognitoTokenState.asStateFlow()
    val switching: StateFlow<Boolean> = switchingState.asStateFlow()

    @Volatile
    private var cancelStreaming: (() -> Unit)? = null

    @Volatile
    private var updateSwitching: ((Boolean) -> Unit)? = null

    private var scope: CoroutineScope? = null

    private data class AttachmentPrune(
        val sessions: List<ChatSessionMeta>,
        val activeId: String?,
        val activeMessages: List<ChatMessage>
    )

    private var pendingAttachmentPrune: AttachmentPrune? = null

    fun bindCancel(block: () -> Unit) {
        cancelStreaming = block
    }

    fun bindSwitching(block: (Boolean) -> Unit) {
        updateSwitching = block
        block(switchingState.value)
    }

    fun attach(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            try {
                mutex.withLock {
                    withContext(ioDispatcher) {
                        initializeLocked().also { restored -> repository.restore(restored) }
                    }
                }
                ready.complete(Unit)
                consumeAttachmentPrune()?.let { prune ->
                    scope.launch {
                        mutex.withLock {
                            withContext(ioDispatcher) {
                                pruneAttachmentsLocked(prune.sessions, prune.activeId, prune.activeMessages)
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                ready.completeExceptionally(error)
                throw error
            }
            repository.messages.collectLatest { snapshot ->
                val expectedSessionId = activeState.value
                val expectedIncognito = incognitoState.value
                delay(DEBOUNCE_MILLIS)
                mutex.withLock {
                    withContext(ioDispatcher) {
                        if (
                            !expectedIncognito &&
                            !incognitoState.value &&
                            activeState.value == expectedSessionId
                        ) {
                            persistSnapshotLocked(snapshot)
                        }
                    }
                }
            }
        }
    }

    suspend fun awaitReady() {
        ready.await()
    }

    fun startIncognito() {
        switchSession {
            val snapshot = repository.messages.value
            if (incognitoState.value) {
                attachmentIdsOf(snapshot).forEach(discardAttachment)
            } else if (snapshot.isEmpty()) {
                discardEmptyActiveLocked()
            } else {
                persistSnapshotLocked(snapshot)
            }
            incognitoState.value = true
            bumpIncognitoTokenLocked()
            activeState.value = null
            repository.clear()
            saveIndexLocked()
        }
    }

    fun stopIncognito() {
        if (!incognitoState.value) return
        switchSession {
            if (!incognitoState.value) return@switchSession
            attachmentIdsOf(repository.messages.value).forEach(discardAttachment)
            incognitoState.value = false
            bumpIncognitoTokenLocked()
            activeState.value = null
            repository.clear()
            saveIndexLocked()
        }
    }

    fun newSession() {
        switchSession {
            val wasIncognito = incognitoState.value
            val snapshot = repository.messages.value
            if (wasIncognito) {
                bumpIncognitoTokenLocked()
            }
            if (snapshot.isEmpty()) {
                if (!wasIncognito) {
                    discardEmptyActiveLocked()
                }
                incognitoState.value = false
                activeState.value = null
                saveIndexLocked()
                return@switchSession
            }
            if (!wasIncognito) {
                persistSnapshotLocked(snapshot)
            } else {
                attachmentIdsOf(snapshot).forEach(discardAttachment)
            }
            incognitoState.value = false
            activeState.value = null
            repository.clear()
            saveIndexLocked()
        }
    }

    fun openSession(sessionId: String) {
        if (activeState.value == sessionId && !incognitoState.value) return
        if (sessionsState.value.none { it.id == sessionId }) return
        switchSession {
            if (activeState.value == sessionId && !incognitoState.value) {
                return@switchSession
            }
            if (sessionsState.value.none { it.id == sessionId }) {
                return@switchSession
            }
            val snapshot = repository.messages.value
            if (incognitoState.value) {
                bumpIncognitoTokenLocked()
                attachmentIdsOf(snapshot).forEach(discardAttachment)
            } else if (snapshot.isEmpty()) {
                discardEmptyActiveLocked()
            } else {
                persistSnapshotLocked(snapshot)
            }
            val messages = store.loadSession(sessionId)
            incognitoState.value = false
            activeState.value = sessionId
            saveIndexLocked()
            repository.restore(messages)
        }
    }

    fun deleteSession(sessionId: String) {
        if (sessionsState.value.none { it.id == sessionId }) return
        switchSession {
            val wasIncognito = incognitoState.value
            val snapshot = repository.messages.value
            if (wasIncognito) {
                bumpIncognitoTokenLocked()
                attachmentIdsOf(snapshot).forEach(discardAttachment)
            } else if (activeState.value != sessionId) {
                if (snapshot.isEmpty()) {
                    discardEmptyActiveLocked()
                } else {
                    persistSnapshotLocked(snapshot)
                }
            }
            val released = attachmentIdsOf(store.loadSession(sessionId))
            store.deleteSessionFile(sessionId)
            val remaining = sessionsState.value.filterNot { it.id == sessionId }
            if (wasIncognito) {
                repository.clear()
                activeState.value = remaining.firstOrNull()?.id
                activeState.value?.let { nextId -> repository.restore(store.loadSession(nextId)) }
            } else if (activeState.value == sessionId) {
                repository.clear()
                val next = remaining.firstOrNull()
                if (next == null) {
                    activeState.value = null
                } else {
                    activeState.value = next.id
                    repository.restore(store.loadSession(next.id))
                }
            }
            val retained = HashSet<String>()
            retained += attachmentIdsOf(repository.messages.value)
            remaining.forEach { meta ->
                if (meta.id != activeState.value) {
                    retained += attachmentIdsOf(store.loadSession(meta.id))
                }
            }
            (released - retained).forEach(discardAttachment)
            sessionsState.value = remaining
            if (wasIncognito) incognitoState.value = false
            saveIndexLocked()
        }
    }

    private fun switchSession(block: suspend () -> Unit) {
        val launchScope = scope ?: return
        if (!switchPending.compareAndSet(false, true)) return
        cancelStreaming?.invoke()
        switchingState.value = true
        updateSwitching?.invoke(true)
        launchScope.launch {
            try {
                mutex.withLock {
                    withContext(ioDispatcher) { block() }
                }
            } finally {
                switchPending.set(false)
                switchingState.value = false
                updateSwitching?.invoke(false)
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
        pendingAttachmentPrune = AttachmentPrune(sessions, activeId, active)
        return active
    }

    private fun consumeAttachmentPrune(): AttachmentPrune? {
        val prune = pendingAttachmentPrune
        pendingAttachmentPrune = null
        return prune
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
        keepAttachments(retained)
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

    private fun discardEmptyActiveLocked() {
        activeState.value?.let { emptyId ->
            sessionsState.value = sessionsState.value.filterNot { it.id == emptyId }
            store.deleteSessionFile(emptyId)
            activeState.value = null
        }
    }

    private fun bumpIncognitoTokenLocked() {
        incognitoTokenState.value = incognitoTokenState.value + 1L
    }

    private fun persistSnapshotLocked(messages: List<ChatMessage>) {
        if (incognitoState.value) return
        val snapshot = messages.filter { !it.streaming || it.text.isNotBlank() }
        if (snapshot.isEmpty()) {
            discardEmptyActiveLocked()
            saveIndexLocked()
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

    private fun newId(): String {
        val stem = ID_PREFIX + clock.nowMillis().toString(RADIX)
        var candidate: String
        do {
            candidate = stem + "-" + sessionCounter.incrementAndGet().toString(RADIX)
        } while (sessionsState.value.any { it.id == candidate })
        return candidate
    }

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
