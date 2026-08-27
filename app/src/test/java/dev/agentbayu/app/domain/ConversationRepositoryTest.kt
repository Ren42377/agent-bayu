package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RouteDecision
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun deltasAccumulateOnTheTargetMessage() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)

        repository.appendDelta(placeholder.id, "Hal")
        repository.appendDelta(placeholder.id, "")
        repository.appendDelta(placeholder.id, "lo")

        val message = repository.messages.value.single()
        assertEquals("Hallo", message.text)
        assertTrue(message.streaming)
    }

    @Test
    fun unknownIdsAreIgnored() {
        val repository = ConversationRepository()
        val message = repository.append(MessageAuthor.USER, "satu")

        repository.appendDelta(message.id + 99L, "hantu")
        repository.replaceText(message.id + 99L, "hantu")
        repository.complete(message.id + 99L, decision(), TokenUsage(1, 1))

        assertEquals(listOf("satu"), repository.messages.value.map { it.text })
    }

    @Test
    fun completeAttachesTheRouteAndClosesTheStream() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        repository.appendDelta(placeholder.id, "jawaban")

        repository.complete(placeholder.id, decision(), TokenUsage(12, 8, 0.001, false))

        val message = repository.messages.value.single()
        assertEquals("jawaban", message.text)
        assertEquals("groq", message.route?.providerId)
        assertEquals(20, message.usage?.totalTokens)
        assertFalse(message.streaming)
    }

    @Test
    fun completeKeepsAnEarlierRouteWhenNoneIsGiven() {
        val repository = ConversationRepository()
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        repository.attachRoute(placeholder.id, decision())

        repository.complete(placeholder.id, null, null)

        val message = repository.messages.value.single()
        assertEquals("groq", message.route?.providerId)
        assertNull(message.usage)
        assertFalse(message.streaming)
    }

    @Test
    fun finishStreamingOnlyTouchesStreamingMessages() {
        val repository = ConversationRepository()
        val done = repository.append(MessageAuthor.USER, "selesai")
        val streaming = repository.append(MessageAuthor.AGENT, "sedang", streaming = true)

        repository.finishStreaming(done.id)
        repository.finishStreaming(streaming.id)

        assertEquals(listOf(false, false), repository.messages.value.map { it.streaming })
        assertEquals(done, repository.messages.value.first())
    }

    @Test
    fun restoreClosesOpenStreamsAndContinuesTheIdSequence() {
        val repository = ConversationRepository()
        repository.restore(
            listOf(
                ChatMessage(id = 7L, author = MessageAuthor.USER, text = "lama"),
                ChatMessage(id = 8L, author = MessageAuthor.AGENT, text = "setengah", streaming = true)
            )
        )

        val appended = repository.append(MessageAuthor.USER, "baru")

        assertEquals(9L, appended.id)
        assertEquals(listOf("lama", "setengah", "baru"), repository.messages.value.map { it.text })
        assertEquals(listOf(false, false, false), repository.messages.value.map { it.streaming })
    }

    @Test
    fun restoreIgnoresAnEmptyHistory() {
        val repository = ConversationRepository()
        repository.append(MessageAuthor.USER, "ada")

        repository.restore(emptyList())

        assertEquals(listOf("ada"), repository.messages.value.map { it.text })
    }

    private fun decision(): RouteDecision = RouteDecision(
        channel = "auto",
        strategy = "priority",
        providerId = "groq",
        providerLabel = "Groq",
        model = "llama-3.3-70b-versatile",
        connectionId = "conn-1",
        connectionLabel = "Groq utama",
        tier = ProviderTier.FREE,
        attempt = 1,
        candidatesConsidered = 2
    )
}
