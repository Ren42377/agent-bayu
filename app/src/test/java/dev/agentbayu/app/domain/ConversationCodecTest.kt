package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCodecTest {

    private val replyDetail = ReplyDetail(
        providerId = "kilocode",
        providerLabel = "Kilo Code",
        model = "minimax/minimax-m3:free",
        connectionId = "conn-1",
        connectionLabel = "Kilo Code",
        authKind = AuthKind.NONE,
        firstTokenMillis = 320L,
        totalMillis = 1_400L
    )

    private fun message(
        id: Long,
        author: MessageAuthor,
        text: String,
        detail: ReplyDetail? = null,
        usage: TokenUsage? = null,
        streaming: Boolean = false
    ): ChatMessage = ChatMessage(id, author, text, detail, usage, streaming)

    @Test
    fun encodeAndDecodeSurviveARoundTrip() {
        val messages = listOf(
            message(1L, MessageAuthor.USER, "halo"),
            message(
                2L,
                MessageAuthor.AGENT,
                "halo juga",
                detail = replyDetail,
                usage = TokenUsage(inputTokens = 120, outputTokens = 40, estimatedCostUsd = 0.002)
            )
        )
        val decoded = ConversationCodec.decode(ConversationCodec.encode(messages))
        assertEquals(messages, decoded)
        assertEquals(AuthKind.NONE, decoded[1].detail?.authKind)
        assertEquals("Kilo Code minimax/minimax-m3:free", decoded[1].detail?.label)
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
        assertNull(decoded.single().detail)
        assertFalse(decoded.single().streaming)
    }

    @Test
    fun routerEraFieldsAreDroppedWithoutLosingTheMessage() {
        val raw = "{\"version\":1,\"messages\":[{\"id\":9,\"author\":\"AGENT\"," +
            "\"text\":\"lanjut\",\"detail\":{\"providerId\":\"groq\",\"providerLabel\":\"Groq\"," +
            "\"model\":\"llama\",\"connectionId\":\"conn-1\",\"connectionLabel\":\"Groq\"," +
            "\"channel\":\"auto/fast\",\"strategy\":\"priority\",\"attempt\":2," +
            "\"skipped\":[{\"connectionLabel\":\"Cerebras\",\"reason\":\"COOLDOWN\"}]," +
            "\"degraded\":true}}]}"

        val decoded = ConversationCodec.decode(raw)

        val detail = decoded.single().detail
        assertEquals("groq", detail?.providerId)
        assertEquals("llama", detail?.model)
        assertEquals(AuthKind.API_KEY, detail?.authKind)
        assertEquals(0L, detail?.totalMillis)
    }

    @Test
    fun defaultsAreLeftOutOfThePayload() {
        val encoded = ConversationCodec.encode(listOf(message(1L, MessageAuthor.USER, "hai")))
        assertFalse(encoded.contains("streaming"))
        assertFalse(encoded.contains("detail"))
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
    fun attachmentsSurviveARoundTrip() {
        val picture = MessageAttachment(
            id = "img-1",
            mimeType = "image/jpeg",
            fileName = "photo.jpg",
            width = 1_024,
            height = 768
        )
        val decoded = ConversationCodec.decode(
            ConversationCodec.encode(
                listOf(ChatMessage(1L, MessageAuthor.USER, "", attachments = listOf(picture)))
            )
        )

        assertEquals(listOf(picture), decoded.single().attachments)
    }

    @Test
    fun conversationsWrittenBeforeAttachmentsStillDecode() {
        val decoded = ConversationCodec.decode(
            """{"version":1,"messages":[{"id":1,"author":"USER","text":"halo"}]}"""
        )

        assertTrue(decoded.single().attachments.isEmpty())
    }

    @Test
    fun theDefaultLimitsMatchThePlan() {
        assertEquals(200, ConversationCodec.MAX_MESSAGES)
        assertEquals(512 * 1024, ConversationCodec.MAX_CHARS)
    }
}
