package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RouteDecision
import dev.agentbayu.app.ai.SkipReason
import dev.agentbayu.app.ai.SkippedCandidate
import dev.agentbayu.app.ai.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCodecTest {

    private val decision = RouteDecision(
        channel = "auto/fast",
        strategy = "priority",
        providerId = "groq",
        providerLabel = "Groq",
        model = "llama-3.3-70b-versatile",
        connectionId = "conn-1",
        connectionLabel = "Groq utama",
        tier = ProviderTier.FREE,
        attempt = 2,
        candidatesConsidered = 3,
        reason = "fallback",
        firstTokenMillis = 320L,
        totalMillis = 1_400L,
        skipped = listOf(
            SkippedCandidate(
                connectionLabel = "Cerebras",
                model = "llama-4",
                reason = SkipReason.COOLDOWN,
                detail = "12"
            )
        ),
        degraded = true
    )

    private fun message(
        id: Long,
        author: MessageAuthor,
        text: String,
        route: RouteDecision? = null,
        usage: TokenUsage? = null,
        streaming: Boolean = false
    ): ChatMessage = ChatMessage(id, author, text, route, usage, streaming)

    @Test
    fun encodeAndDecodeSurviveARoundTrip() {
        val messages = listOf(
            message(1L, MessageAuthor.USER, "halo"),
            message(
                2L,
                MessageAuthor.AGENT,
                "halo juga",
                route = decision,
                usage = TokenUsage(inputTokens = 120, outputTokens = 40, estimatedCostUsd = 0.002)
            )
        )
        val decoded = ConversationCodec.decode(ConversationCodec.encode(messages))
        assertEquals(messages, decoded)
        assertEquals(SkipReason.COOLDOWN, decoded[1].route?.skipped?.single()?.reason)
        assertEquals(160, decoded[1].usage?.totalTokens)
    }

    @Test
    fun anEmptyConversationRoundTripsToAnEmptyList() {
        assertTrue(ConversationCodec.decode(ConversationCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun brokenPayloadsDecodeToNothing() {
        assertTrue(ConversationCodec.decode("").isEmpty())
        assertTrue(ConversationCodec.decode("{").isEmpty())
        assertTrue(ConversationCodec.decode("not json").isEmpty())
        assertTrue(ConversationCodec.decode("{\"messages\":\"wrong type\"}").isEmpty())
    }

    @Test
    fun unknownFieldsAreTolerated() {
        val raw = "{\"version\":1,\"future\":true,\"messages\":[" +
            "{\"id\":7,\"author\":\"USER\",\"text\":\"hai\",\"mood\":\"happy\"}]}"
        val decoded = ConversationCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(7L, decoded.single().id)
        assertEquals("hai", decoded.single().text)
        assertNull(decoded.single().route)
        assertFalse(decoded.single().streaming)
    }

    @Test
    fun defaultsAreLeftOutOfThePayload() {
        val encoded = ConversationCodec.encode(listOf(message(1L, MessageAuthor.USER, "hai")))
        assertFalse(encoded.contains("streaming"))
        assertFalse(encoded.contains("route"))
        assertFalse(encoded.contains("usage"))
    }

    @Test
    fun trimKeepsTheNewestMessages() {
        val messages = (1..10).map { message(it.toLong(), MessageAuthor.USER, "m" + it) }
        val trimmed = ConversationCodec.trim(messages, maxMessages = 4)
        assertEquals(listOf(7L, 8L, 9L, 10L), trimmed.map { it.id })
    }

    @Test
    fun trimLeavesShortConversationsAlone() {
        val messages = (1..3).map { message(it.toLong(), MessageAuthor.USER, "m" + it) }
        assertEquals(messages, ConversationCodec.trim(messages))
    }

    @Test
    fun trimHonoursTheCharacterCeiling() {
        val messages = (1..5).map { message(it.toLong(), MessageAuthor.USER, "x".repeat(100)) }
        val trimmed = ConversationCodec.trim(messages, maxChars = 250)
        assertEquals(listOf(4L, 5L), trimmed.map { it.id })
    }

    @Test
    fun trimNeverEmptiesTheConversation() {
        val messages = listOf(
            message(1L, MessageAuthor.USER, "x".repeat(100)),
            message(2L, MessageAuthor.AGENT, "y".repeat(1_000))
        )
        val trimmed = ConversationCodec.trim(messages, maxChars = 10)
        assertEquals(listOf(2L), trimmed.map { it.id })
    }

    @Test
    fun theDefaultLimitsMatchThePlan() {
        assertEquals(200, ConversationCodec.MAX_MESSAGES)
        assertEquals(512 * 1024, ConversationCodec.MAX_CHARS)
    }
}
