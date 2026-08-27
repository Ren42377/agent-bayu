package dev.agentbayu.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryTest {

    @Test
    fun emptyByDefault() {
        val repository = ConversationRepository()
        assertTrue(repository.messages.value.isEmpty())
    }

    @Test
    fun appendKeepsOrderAndAssignsIncreasingIds() {
        val repository = ConversationRepository()
        repository.append(MessageAuthor.USER, "first")
        repository.append(MessageAuthor.AGENT, "second")

        val messages = repository.messages.value
        assertEquals(2, messages.size)
        assertEquals(MessageAuthor.USER, messages[0].author)
        assertEquals("first", messages[0].text)
        assertEquals(MessageAuthor.AGENT, messages[1].author)
        assertTrue(messages[1].id > messages[0].id)
    }

    @Test
    fun idsDoNotCollideAfterClear() = runTest {
        val repository = ConversationRepository()
        repository.append(MessageAuthor.USER, "old")
        repository.clear()
        repository.append(MessageAuthor.USER, "new")

        val messages = repository.messages.value
        assertEquals(1, messages.size)
        assertEquals("new", messages.single().text)
    }
}
