package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.testCandidate
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiResponsesAdapterTest {

    private lateinit var server: MockWebServer
    private val adapter = OpenAiResponsesAdapter(adapterTestClient) { "session-1" }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun candidate(): Candidate = testCandidate(
        baseUrl = server.url("/backend-api/codex").toString(),
        modelId = "gpt-5.6-sol",
        wireFormat = WireFormat.OPENAI_RESPONSES,
        authKind = AuthKind.OAUTH_DEVICE
    )

    private fun request(): ChatRequest = ChatRequest(
        systemPrompt = "You are Bayu.",
        turns = listOf(
            ChatTurn(ChatRole.USER, "pertama"),
            ChatTurn(ChatRole.ASSISTANT, "jawaban"),
            ChatTurn(ChatRole.USER, "lanjut")
        ),
        temperature = 0.4,
        maxOutputTokens = 512
    )

    private fun JsonObject.inputTurns(): List<Triple<String?, String?, String?>> =
        arrayField("input").orEmpty().map { element ->
            val turn = element as JsonObject
            val part = turn.arrayField("content")?.firstOrNull() as? JsonObject
            Triple(turn.stringField("role"), part?.stringField("type"), part?.stringField("text"))
        }

    @Test
    fun postsAStreamingResponsesCall() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"response.output_text.delta\",\"delta\":\"ok\"}",
                "{\"type\":\"response.completed\",\"response\":{}}"
            )
        )

        val events = collectEvents(
            adapter.stream(candidate(), "token-1", request(), mapOf("chatgpt-account-id" to "acc-1"))
        )

        assertEquals("ok", events.deltaText())
        assertTrue(events.completed())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/backend-api/codex/responses", recorded.path)
        assertEquals("Bearer token-1", recorded.getHeader("Authorization"))
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        assertEquals("acc-1", recorded.getHeader("chatgpt-account-id"))
        assertEquals(
            OpenAiResponsesAdapter.CLIENT_VERSION,
            recorded.getHeader(OpenAiResponsesAdapter.VERSION_HEADER)
        )
        assertEquals(
            OpenAiResponsesAdapter.BETA_VALUE,
            recorded.getHeader(OpenAiResponsesAdapter.BETA_HEADER)
        )
        assertEquals(
            OpenAiResponsesAdapter.ORIGINATOR_VALUE,
            recorded.getHeader(OpenAiResponsesAdapter.ORIGINATOR_HEADER)
        )
        assertEquals("session-1", recorded.getHeader(OpenAiResponsesAdapter.SESSION_HEADER))
        assertEquals(OpenAiResponsesAdapter.USER_AGENT, recorded.getHeader("User-Agent"))
    }

    @Test
    fun theBodyCarriesInstructionsAndTypedInputWithoutTokenCaps() {
        server.enqueue(sseResponse("{\"type\":\"response.completed\",\"response\":{}}"))

        collectEvents(adapter.stream(candidate(), "token-1", request()))

        val body = parseJsonObject(server.takeRequest().body.readUtf8())!!
        assertEquals("gpt-5.6-sol", body.stringField("model"))
        assertEquals("You are Bayu.", body.stringField("instructions"))
        assertEquals(
            listOf(
                Triple("user", "input_text", "pertama"),
                Triple("assistant", "output_text", "jawaban"),
                Triple("user", "input_text", "lanjut")
            ),
            body.inputTurns()
        )
        assertEquals("true", body["stream"].toString())
        assertEquals("false", body["store"].toString())
        assertFalse(body.containsKey("max_output_tokens"))
        assertFalse(body.containsKey(WireParams.MAX_TOKENS))
        assertFalse(body.containsKey(WireParams.TEMPERATURE))
    }

    @Test
    fun systemTurnsJoinTheInstructionsInsteadOfTheInput() {
        server.enqueue(sseResponse("{\"type\":\"response.completed\",\"response\":{}}"))

        collectEvents(
            adapter.stream(
                candidate(),
                "token-1",
                ChatRequest(
                    systemPrompt = "You are Bayu.",
                    turns = listOf(
                        ChatTurn(ChatRole.SYSTEM, "Konteks layar."),
                        ChatTurn(ChatRole.USER, "halo")
                    )
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())!!
        assertEquals("You are Bayu.\n\nKonteks layar.", body.stringField("instructions"))
        assertEquals(listOf(Triple("user", "input_text", "halo")), body.inputTurns())
    }

    @Test
    fun aRequestWithoutAnyInstructionFallsBackToTheDefault() {
        server.enqueue(sseResponse("{\"type\":\"response.completed\",\"response\":{}}"))

        collectEvents(
            adapter.stream(
                candidate(),
                "token-1",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "halo")))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())!!
        assertEquals(
            OpenAiResponsesAdapter.DEFAULT_INSTRUCTIONS,
            body.stringField("instructions")
        )
    }

    @Test
    fun deltasStreamInOrderAndCompletedCarriesUsage() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"response.created\",\"response\":{}}",
                "{\"type\":\"response.output_text.delta\",\"delta\":\"Hal\"}",
                "{\"type\":\"response.output_text.delta\",\"delta\":\"lo \"}",
                "{\"type\":\"response.output_text.delta\",\"delta\":\"Bayu\"}",
                "{\"type\":\"response.output_text.done\",\"text\":\"Hallo Bayu\"}",
                "{\"type\":\"response.completed\",\"response\":{\"usage\":" +
                    "{\"input_tokens\":42,\"output_tokens\":7}}}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "token-1", request()))

        assertEquals("Hallo Bayu", events.deltaText())
        assertEquals(WireEvent.Usage(42, 7), events.lastUsage())
        assertTrue(events.completed())
        assertNull(events.firstFailure())
    }

    @Test
    fun completedWithoutUsageStillFinishesTheStream() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"response.output_text.delta\",\"delta\":\"ya\"}",
                "{\"type\":\"response.completed\",\"response\":{\"usage\":" +
                    "{\"input_tokens\":0,\"output_tokens\":0}}}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "token-1", request()))

        assertEquals("ya", events.deltaText())
        assertNull(events.lastUsage())
        assertTrue(events.completed())
    }

    @Test
    fun aFailedResponseStopsTheStream() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"response.output_text.delta\",\"delta\":\"separuh\"}",
                "{\"type\":\"response.failed\",\"response\":{\"error\":" +
                    "{\"code\":\"rate_limit_exceeded\",\"message\":\"slow down\"}}}",
                "{\"type\":\"response.output_text.delta\",\"delta\":\"tidak terkirim\"}",
                "{\"type\":\"response.completed\",\"response\":{}}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "token-1", request()))

        assertEquals("separuh", events.deltaText())
        val failure = events.firstFailure()
        assertEquals(FailureKind.COOLDOWN, failure?.kind)
        assertEquals(429, failure?.statusCode)
        assertFalse(events.completed())
    }

    @Test
    fun aStreamErrorEventBecomesAFailure() {
        server.enqueue(
            sseResponse("{\"type\":\"error\",\"error\":{\"code\":500,\"message\":\"boom\"}}")
        )

        val failure = collectEvents(adapter.stream(candidate(), "token-1", request())).firstFailure()

        assertEquals(FailureKind.RETRYABLE, failure?.kind)
        assertEquals(500, failure?.statusCode)
    }

    @Test
    fun anUnknownModelBecomesAModelLockFailure() {
        server.enqueue(errorResponse(404, "{\"error\":{\"message\":\"unknown model\"}}"))

        val failure = collectEvents(adapter.stream(candidate(), "token-1", request())).firstFailure()

        assertEquals(FailureKind.MODEL_LOCK, failure?.kind)
        assertEquals(404, failure?.statusCode)
    }

    @Test
    fun aRejectedTokenBecomesATerminalFailure() {
        server.enqueue(errorResponse(401, "{\"error\":{\"message\":\"invalid token\"}}"))

        val failure = collectEvents(adapter.stream(candidate(), "token-1", request())).firstFailure()

        assertEquals(FailureKind.TERMINAL, failure?.kind)
        assertEquals(401, failure?.statusCode)
    }

    @Test
    fun blankAuthHeaderValuesAreLeftOut() {
        server.enqueue(sseResponse("{\"type\":\"response.completed\",\"response\":{}}"))

        collectEvents(
            adapter.stream(candidate(), "token-1", request(), mapOf("chatgpt-account-id" to " "))
        )

        assertNull(server.takeRequest().getHeader("chatgpt-account-id"))
    }
}
