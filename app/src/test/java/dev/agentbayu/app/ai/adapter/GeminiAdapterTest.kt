package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.AuthHeader
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

class GeminiAdapterTest {

    private lateinit var server: MockWebServer
    private val adapter = GeminiAdapter(adapterTestClient)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun candidate(modelId: String = "gemini-2.0-flash", maxOutputTokens: Int = 8_192) = testCandidate(
        providerId = "google",
        modelId = modelId,
        maxOutputTokens = maxOutputTokens,
        baseUrl = server.url("/").toString(),
        authHeader = AuthHeader.X_GOOG_API_KEY,
        wireFormat = WireFormat.GEMINI
    )

    private fun request(): ChatRequest = ChatRequest(
        systemPrompt = "You are Bayu.",
        turns = listOf(
            ChatTurn(ChatRole.USER, "pertama"),
            ChatTurn(ChatRole.ASSISTANT, "jawaban"),
            ChatTurn(ChatRole.USER, "lanjut")
        ),
        temperature = 0.3
    )

    private fun contents(body: JsonObject?): List<Pair<String?, String?>> {
        val array = body?.arrayField("contents") ?: return emptyList()
        return array.mapNotNull { element ->
            val turn = element as? JsonObject ?: return@mapNotNull null
            val text = turn.arrayField("parts")
                ?.mapNotNull { (it as? JsonObject)?.stringField("text") }
                ?.joinToString("")
            turn.stringField("role") to text
        }
    }

    private fun textDelta(text: String): String =
        "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"" + text + "\"}]}}]}"

    @Test
    fun postsToTheStreamingGenerateEndpoint() {
        server.enqueue(sseResponse(textDelta("ok")))

        val events = collectEvents(adapter.stream(candidate(), "key-goog", request()))

        assertEquals("ok", events.deltaText())
        assertTrue(events.completed())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(
            "/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse",
            recorded.path
        )
        assertEquals("key-goog", recorded.getHeader("x-goog-api-key"))
        assertNull(recorded.getHeader("Authorization"))

        val body = parseJsonObject(recorded.body.readUtf8())
        val instruction = body?.objectField("systemInstruction")
            ?.arrayField("parts")
            ?.firstOrNull() as? JsonObject
        assertEquals("You are Bayu.", instruction?.stringField("text"))
        assertEquals(
            listOf("user" to "pertama", "model" to "jawaban", "user" to "lanjut"),
            contents(body)
        )
        val config = body?.objectField("generationConfig")
        assertEquals(8_192, config?.intField("maxOutputTokens"))
        assertTrue(config?.containsKey("temperature") == true)
    }

    @Test
    fun requestedOutputLimitsAndUnsupportedTemperatureAreHonoured() {
        server.enqueue(sseResponse(textDelta("hi")))
        collectEvents(
            adapter.stream(
                testCandidate(
                    providerId = "google",
                    modelId = "gemini-2.0-flash",
                    baseUrl = server.url("/").toString(),
                    authHeader = AuthHeader.X_GOOG_API_KEY,
                    wireFormat = WireFormat.GEMINI,
                    providerUnsupportedParams = listOf(WireParams.TEMPERATURE)
                ),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "hi")), temperature = 0.9, maxOutputTokens = 128)
            )
        )
        val config = parseJsonObject(server.takeRequest().body.readUtf8())?.objectField("generationConfig")
        assertEquals(128, config?.intField("maxOutputTokens"))
        assertFalse(config?.containsKey("temperature") == true)
    }

    @Test
    fun systemTurnsAreDroppedAndSameRoleTurnsMerge() {
        server.enqueue(sseResponse(textDelta("hi")))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(ChatRole.SYSTEM, "diabaikan"),
                        ChatTurn(ChatRole.USER, "satu"),
                        ChatTurn(ChatRole.USER, "dua"),
                        ChatTurn(ChatRole.ASSISTANT, "jawab")
                    )
                )
            )
        )
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(listOf("user" to "satu\n\ndua", "model" to "jawab"), contents(body))
        assertFalse(body?.containsKey("systemInstruction") == true)
    }

    @Test
    fun partsAreJoinedAndUsageMetadataIsRead() {
        server.enqueue(
            sseResponse(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hal\"},{\"text\":\"lo \"}]}}]}",
                textDelta("Bayu"),
                "{\"candidates\":[{\"finishReason\":\"STOP\"}]," +
                    "\"usageMetadata\":{\"promptTokenCount\":31,\"candidatesTokenCount\":9}}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals("Hallo Bayu", events.deltaText())
        assertEquals(WireEvent.Usage(31, 9), events.lastUsage())
        assertTrue(events.completed())
    }

    @Test
    fun streamErrorsCarryTheStatusCode() {
        server.enqueue(
            sseResponse(
                "{\"error\":{\"code\":404,\"message\":\"model gemini-x is not found\",\"status\":\"NOT_FOUND\"}}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals(FailureKind.MODEL_LOCK, events.firstFailure()?.kind)
        assertEquals(404, events.firstFailure()?.statusCode)
        assertFalse(events.completed())
    }

    @Test
    fun blockedKeysAreTerminal() {
        server.enqueue(errorResponse(403, "{\"error\":{\"message\":\"API key not valid\"}}"))

        val failure = collectEvents(adapter.stream(candidate(), "key", request())).firstFailure()

        assertEquals(FailureKind.TERMINAL, failure?.kind)
        assertEquals(403, failure?.statusCode)
    }

    @Test
    fun quotaExhaustionCoolsTheConnectionDown() {
        server.enqueue(errorResponse(429, "{\"error\":{\"message\":\"rate limit\"}}", retryAfter = "12"))

        val failure = collectEvents(adapter.stream(candidate(), "key", request())).firstFailure()

        assertEquals(FailureKind.COOLDOWN, failure?.kind)
        assertEquals(12_000L, failure?.retryAfterMillis)
    }

    @Test
    fun emptyCandidateListsProduceNoDelta() {
        server.enqueue(sseResponse("{\"candidates\":[]}", "{\"candidates\":[{\"content\":{\"parts\":[]}}]}"))

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals("", events.deltaText())
        assertTrue(events.completed())
    }

    @Test
    fun imagesBecomeInlineDataParts() {
        server.enqueue(sseResponse("{\"candidates\":[]}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "apa ini", listOf(testImage))))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        val parts = body?.parts("contents", 0).orEmpty()
        assertEquals(2, parts.size)

        val inline = parts.first().objectField("inlineData")
        assertEquals(testImage.mimeType, inline?.stringField("mimeType"))
        assertEquals(testImage.data, inline?.stringField("data"))
        assertEquals("apa ini", parts.last().stringField("text"))
    }
}
