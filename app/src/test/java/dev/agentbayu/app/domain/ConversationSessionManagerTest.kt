package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.platform.InMemoryStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSessionManagerTest {

    @Test
    fun newSessionsHaveUniqueIdsAtTheSameTimestamp() = runTest {
        val fixture = fixture(this)
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()

        fixture.repository.append(MessageAuthor.USER, "first")
        fixture.manager.newSession()
        advanceUntilIdle()
        assertNull(fixture.manager.activeSessionId.value)
        fixture.repository.append(MessageAuthor.USER, "second")
        fixture.manager.newSession()
        advanceUntilIdle()

        assertNull(fixture.manager.activeSessionId.value)
        val ids = fixture.manager.sessions.value.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(2, ids.size)
    }

    @Test
    fun anEmptyNewSessionDoesNotLeaveAStoredPlaceholder() = runTest {
        val fixture = fixture(this)
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()

        fixture.repository.append(MessageAuthor.USER, "first")
        fixture.manager.newSession()
        advanceUntilIdle()
        fixture.manager.newSession()
        advanceUntilIdle()

        assertEquals(1, fixture.manager.sessions.value.size)
        assertNull(fixture.manager.activeSessionId.value)
        assertTrue(fixture.repository.messages.value.isEmpty())
    }

    @Test
    fun openingANormalSessionDiscardsIncognitoAttachments() = runTest {
        val fixture = fixture(this)
        val saved = ChatMessage(id = 1L, author = MessageAuthor.USER, text = "saved")
        val meta = ChatSessionMeta(id = "saved-session", title = "Saved")
        fixture.store.saveSession(meta.id, listOf(saved))
        fixture.store.saveIndex(SessionIndexFile(activeSessionId = meta.id, sessions = listOf(meta)))
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()

        fixture.manager.startIncognito()
        advanceUntilIdle()
        val attachment = MessageAttachment(id = "incognito-image", mimeType = "image/jpeg")
        fixture.repository.append(MessageAuthor.USER, "private", attachments = listOf(attachment))
        fixture.manager.openSession(meta.id)
        advanceUntilIdle()

        assertEquals(listOf("incognito-image"), fixture.discarded)
        assertEquals(listOf(saved), fixture.repository.messages.value)
        assertFalse(fixture.manager.incognito.value)
    }

    @Test
    fun deletingASessionWhileIncognitoClearsThePrivateConversation() = runTest {
        val fixture = fixture(this)
        val stored = ChatMessage(id = 1L, author = MessageAuthor.USER, text = "stored")
        val meta = ChatSessionMeta(id = "stored-session", title = "Stored")
        fixture.store.saveSession(meta.id, listOf(stored))
        fixture.store.saveIndex(SessionIndexFile(activeSessionId = meta.id, sessions = listOf(meta)))
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()
        fixture.manager.startIncognito()
        advanceUntilIdle()
        val privateImage = MessageAttachment(id = "private", mimeType = "image/jpeg")
        fixture.repository.append(
            MessageAuthor.USER,
            "private",
            attachments = listOf(privateImage)
        )

        fixture.manager.deleteSession(meta.id)
        advanceUntilIdle()

        assertTrue(fixture.repository.messages.value.isEmpty())
        assertFalse(fixture.manager.incognito.value)
        assertEquals(listOf("private"), fixture.discarded)
    }

    @Test
    fun deletingAStoredSessionKeepsAttachmentsUsedByTheActiveConversation() = runTest {
        val fixture = fixture(this)
        val shared = MessageAttachment(id = "shared", mimeType = "image/jpeg")
        val stored = ChatMessage(
            id = 1L,
            author = MessageAuthor.USER,
            text = "stored",
            attachments = listOf(shared)
        )
        val storedMeta = ChatSessionMeta(id = "stored-session", title = "Stored")
        val active = ChatMessage(
            id = 2L,
            author = MessageAuthor.USER,
            text = "active",
            attachments = listOf(shared)
        )
        val activeMeta = ChatSessionMeta(id = "active-session", title = "Active")
        fixture.store.saveSession(storedMeta.id, listOf(stored))
        fixture.store.saveSession(activeMeta.id, listOf(active))
        fixture.store.saveIndex(
            SessionIndexFile(activeSessionId = activeMeta.id, sessions = listOf(storedMeta, activeMeta))
        )
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()

        fixture.manager.deleteSession(storedMeta.id)
        advanceUntilIdle()

        assertFalse("shared" in fixture.discarded)
    }

    @Test
    fun deletingAnotherSessionPersistsTheActiveConversationFirst() = runTest {
        val fixture = fixture(this)
        val oldMessage = ChatMessage(id = 1L, author = MessageAuthor.USER, text = "old")
        val oldMeta = ChatSessionMeta(id = "old-session", title = "Old")
        val activeMessage = ChatMessage(id = 2L, author = MessageAuthor.USER, text = "active")
        val activeMeta = ChatSessionMeta(id = "active-session", title = "Active")
        fixture.store.saveSession(oldMeta.id, listOf(oldMessage))
        fixture.store.saveSession(activeMeta.id, listOf(activeMessage))
        fixture.store.saveIndex(
            SessionIndexFile(activeSessionId = activeMeta.id, sessions = listOf(oldMeta, activeMeta))
        )
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()
        fixture.repository.append(MessageAuthor.AGENT, "unsaved reply")

        fixture.manager.deleteSession(oldMeta.id)
        advanceUntilIdle()

        assertEquals(
            listOf("active", "unsaved reply"),
            fixture.store.loadSession(activeMeta.id).map { it.text }
        )
        assertEquals(listOf(activeMeta.id), fixture.manager.sessions.value.map { it.id })
    }

    @Test
    fun switchingGateChangesSynchronouslyAroundSessionWork() = runTest {
        val fixture = fixture(this)
        val states = mutableListOf<Boolean>()
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()
        fixture.manager.bindSwitching(states::add)

        fixture.repository.append(MessageAuthor.USER, "first")
        fixture.manager.newSession()

        assertTrue(fixture.manager.switching.value)
        assertTrue(states.last())
        advanceUntilIdle()
        assertFalse(fixture.manager.switching.value)
        assertFalse(states.last())
    }

    @Test
    fun startingIncognitoRemovesLegacyEmptySessionMetadata() = runTest {
        val fixture = fixture(this)
        val meta = ChatSessionMeta(id = "empty-session", title = "")
        fixture.store.saveIndex(SessionIndexFile(activeSessionId = meta.id, sessions = listOf(meta)))
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()

        fixture.manager.startIncognito()
        advanceUntilIdle()

        assertTrue(fixture.manager.sessions.value.isEmpty())
        assertNull(fixture.manager.activeSessionId.value)
        assertTrue(fixture.manager.incognito.value)
        assertTrue(fixture.store.loadIndex().sessions.isEmpty())
    }

    @Test
    fun incognitoSnapshotsAreNeverPersistedAfterTheModeChanges() = runTest {
        val fixture = fixture(this)
        fixture.manager.attach(backgroundScope)
        advanceUntilIdle()

        fixture.manager.startIncognito()
        advanceUntilIdle()
        fixture.repository.append(MessageAuthor.USER, "private")
        advanceTimeBy(ConversationSessionManager.DEBOUNCE_MILLIS + 1L)
        advanceUntilIdle()

        assertTrue(fixture.store.loadIndex().sessions.isEmpty())
        assertTrue(fixture.manager.sessions.value.isEmpty())
    }

    private fun fixture(testScope: TestScope): Fixture {
        val repository = ConversationRepository()
        val store = ConversationStore(InMemoryStorage())
        val discarded = mutableListOf<String>()
        val manager = ConversationSessionManager(
            store = store,
            repository = repository,
            discardAttachment = discarded::add,
            keepAttachments = {},
            clock = FakeClock(10_000L),
            ioDispatcher = StandardTestDispatcher(testScope.testScheduler)
        )
        return Fixture(store, repository, manager, discarded)
    }

    private data class Fixture(
        val store: ConversationStore,
        val repository: ConversationRepository,
        val manager: ConversationSessionManager,
        val discarded: MutableList<String>
    )
}
