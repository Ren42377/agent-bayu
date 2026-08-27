package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.AuthHeader
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.testCandidate
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicAdapterTest {

    private lateinit var server: MockWebServer
    private val adapter = AnthropicAdapter(adapterTestClient)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun candidate(modelId: String = "claude-sonnet-4", maxOutputTokens: Int = 2_048) = testCandidate(
        providerId = "anthropic",
        modelId = modelId,
        maxOutputTokens = maxOutputTokens,
        baseUrl = server.url("/").toString(),
        authHeader = AuthHeader.X_API_KEY,
        wireFormat = WireFormat.ANTHROPIC
    )

    private fun request(): ChatRequest = ChatRequest(
        systemPrompt = "You are Bayu.",
        turns = listOf(
            ChatTurn(ChatRole.USER, "pertama"),
            ChatTurn(ChatRole.ASSISTANT, "jawaban"),
            ChatTurn(ChatRole.USER, "lanjut")
        ),
        temperature = 0.5
    )

    @Test
    fun postsToTheMessagesEndpointWithTheVersionHeader() {
        server.enqueue(
            sseResponse("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}")
        )

        val events = collectEvents(adapter.stream(candidate(), "key-abc", request()))

        assertEquals("ok", events.deltaText())
        assertTrue(events.completed())

        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals("key-abc", recorded.getHeader("x-api-key"))
        assertEquals(AnthropicAdapter.VERSION_VALUE, recorded.getHeader(AnthropicAdapter.VERSION_HEADER))
        assertNull(recorded.getHeader("Authorization"))

        val body = parseJsonObject(recorded.body.readUtf8())
        assertEquals("claude-sonnet-4", body?.stringField("model"))
        assertEquals("You are Bayu.", body?.stringField("system"))
        assertEquals(2_048, body?.intField(WireParams.MAX_TOKENS))
        assertEquals(
            listOf("user" to "pertama", "assistant" to "jawaban", "user" to "lanjut"),
            body?.turns("messages")
        )
    }

    @Test
    fun anExplicitOutputLimitWinsOverTheModelDefault() {
        server.enqueue(sseResponse("{\"type\":\"ping\"}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "hi")), maxOutputTokens = 64)
            )
        )
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(64, body?.intField(WireParams.MAX_TOKENS))
    }

    @Test
    fun conversationTurnsAreNormalized() {
        server.enqueue(sseResponse("{\"type\":\"ping\"}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(ChatRole.ASSISTANT, "sisa lama"),
                        ChatTurn(ChatRole.SYSTEM, "diabaikan"),
                        ChatTurn(ChatRole.USER, "satu"),
                        ChatTurn(ChatRole.USER, "dua"),
                        ChatTurn(ChatRole.ASSISTANT, "jawab")
                    )
                )
            )
        )
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(
            listOf("user" to "satu\n\ndua", "assistant" to "jawab"),
            body?.turns("messages")
        )
    }

    @Test
    fun anEmptyConversationFallsBackToAGreeting() {
        server.enqueue(sseResponse("{\"type\":\"ping\"}"))
        collectEvents(adapter.stream(candidate(), "key", ChatRequest(turns = emptyList())))
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(
            listOf("user" to AnthropicAdapter.EMPTY_PROMPT_FALLBACK),
            body?.turns("messages")
        )
        assertFalse(body?.containsKey("system") == true)
    }

    @Test
    fun textDeltasAndUsageAreParsed() {
        server.enqueue(
            rawSseResponse(
                "event: message_start\n" +
                    "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":25,\"output_tokens\":1}}}\n" +
                    "\n" +
                    "event: content_block_start\n" +
                    "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n" +
                    "\n" +
                    "event: content_block_delta\n" +
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hal\"}}\n" +
                    "\n" +
                    "event: content_block_delta\n" +
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"lo\"}}\n" +
                    "\n" +
                    "event: message_delta\n" +
                    "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":40}}\n" +
                    "\n" +
                    "event: message_stop\n" +
                    "data: {\"type\":\"message_stop\"}\n" +
                    "\n"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals("Hallo", events.deltaText())
        assertEquals(WireEvent.Usage(25, 40), events.lastUsage())
        assertTrue(events.completed())
    }

    @Test
    fun overloadedErrorsRetryAndTripTheBreaker() {
        server.enqueue(
            sseResponse("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}")
        )

        val failure = collectEvents(adapter.stream(candidate(), "key", request())).firstFailure()

        assertEquals(FailureKind.RETRYABLE, failure?.kind)
        assertEquals(AnthropicAdapter.OVERLOADED_STATUS, failure?.statusCode)
        assertTrue(failure?.tripsBreaker == true)
    }

    @Test
    fun otherStreamErrorsAreRetryable() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"content_block_delta\",\"delta\":{\"text\":\"separuh\"}}",
                "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals("separuh", events.deltaText())
        assertEquals(FailureKind.RETRYABLE, events.firstFailure()?.kind)
        assertFalse(events.completed())
    }

    @Test
    fun unknownModelsLockTheModel() {
        server.enqueue(errorResponse(404, "{\"error\":{\"message\":\"model: claude-x not found\"}}"))

        val failure = collectEvents(adapter.stream(candidate(), "key", request())).firstFailure()

        assertEquals(FailureKind.MODEL_LOCK, failure?.kind)
    }
}
