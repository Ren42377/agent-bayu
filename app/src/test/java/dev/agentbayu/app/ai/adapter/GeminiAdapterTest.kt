package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.AuthHeader
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.testCandidate
import dev.agentbayu.app.ai.tools.ToolCall
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

    @Test
    fun toolDeclarationsRideInsideFunctionDeclarations() {
        server.enqueue(sseResponse("{\"candidates\":[]}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")), tools = listOf(testTool))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        val group = body?.arrayField(WireParams.TOOLS)?.firstOrNull() as? JsonObject
        val declared = group?.arrayField("functionDeclarations")?.filterIsInstance<JsonObject>().orEmpty()
        assertEquals(1, declared.size)
        assertEquals("create_task", declared.first().stringField("name"))
        assertEquals("object", declared.first().objectField("parameters")?.stringField("type"))
    }

    @Test
    fun toolsAreLeftOutWhenTheModelRejectsThem() {
        server.enqueue(sseResponse("{\"candidates\":[]}"))
        collectEvents(
            adapter.stream(
                testCandidate(
                    providerId = "google",
                    baseUrl = server.url("/").toString(),
                    authHeader = AuthHeader.X_GOOG_API_KEY,
                    wireFormat = WireFormat.GEMINI,
                    modelUnsupportedParams = listOf(WireParams.TOOLS)
                ),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")), tools = listOf(testTool))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertFalse(body?.containsKey(WireParams.TOOLS) == true)
    }

    @Test
    fun functionCallPartsBecomeToolCalls() {
        server.enqueue(
            sseResponse(
                textDelta("sebentar"),
                "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":" +
                    "{\"name\":\"create_task\",\"args\":{\"title\":\"beli susu\"}}}]}}]}",
                "{\"candidates\":[{\"finishReason\":\"STOP\"}]}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals("sebentar", events.deltaText())
        assertEquals(
            listOf(ToolCall(id = "call_1", name = "create_task", arguments = "{\"title\":\"beli susu\"}")),
            events.toolCalls()
        )
        assertTrue(events.completed())
    }

    @Test
    fun parallelFunctionCallsKeepTheirOwnArguments() {
        server.enqueue(
            sseResponse(
                "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                    "{\"functionCall\":{\"name\":\"list_files\",\"args\":{\"path\":\"a\"}}}," +
                    "{\"functionCall\":{\"name\":\"read_file\",\"args\":{\"path\":\"b.txt\"}}}" +
                    "]}}]}",
                "{\"candidates\":[{\"finishReason\":\"STOP\"}]}"
            )
        )

        val calls = collectEvents(adapter.stream(candidate(), "key", request())).toolCalls()

        assertEquals(listOf("list_files", "read_file"), calls.map { it.name })
        assertEquals("{\"path\":\"a\"}", calls.first().arguments)
        assertEquals("{\"path\":\"b.txt\"}", calls.last().arguments)
    }

    @Test
    fun aFunctionCallWithoutArgumentsFallsBackToAnEmptyObject() {
        server.enqueue(
            sseResponse(
                "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                    "{\"functionCall\":{\"name\":\"list_tasks\"}}]}}]}"
            )
        )

        val calls = collectEvents(adapter.stream(candidate(), "key", request())).toolCalls()

        assertEquals(listOf("list_tasks"), calls.map { it.name })
        assertEquals("{}", calls.first().arguments)
    }

    @Test
    fun parallelToolResultsShareOneUserContent() {
        server.enqueue(sseResponse("{\"candidates\":[]}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(ChatRole.USER, "buat tugas"),
                        ChatTurn(
                            role = ChatRole.ASSISTANT,
                            content = "",
                            toolCalls = listOf(
                                ToolCall(id = "call_1", name = "create_task", arguments = "{\"title\":\"beli susu\"}"),
                                ToolCall(id = "call_2", name = "list_tasks", arguments = "{}")
                            )
                        ),
                        ChatTurn(
                            role = ChatRole.TOOL,
                            content = "Task created",
                            toolCallId = "call_1",
                            toolName = "create_task"
                        ),
                        ChatTurn(
                            role = ChatRole.TOOL,
                            content = "No list",
                            toolCallId = "call_2",
                            toolName = "list_tasks",
                            toolFailed = true
                        )
                    )
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(listOf("user" to "buat tugas", "model" to "", "user" to ""), contents(body))

        val calls = body?.parts("contents", 1).orEmpty()
        assertEquals(2, calls.size)
        assertEquals("create_task", calls.first().objectField("functionCall")?.stringField("name"))
        assertEquals(
            "beli susu",
            calls.first().objectField("functionCall")?.objectField("args")?.stringField("title")
        )

        val results = body?.parts("contents", 2).orEmpty()
        assertEquals(2, results.size)
        val first = results.first().objectField("functionResponse")
        assertEquals("create_task", first?.stringField("name"))
        assertEquals("Task created", first?.objectField("response")?.stringField("result"))
        val second = results.last().objectField("functionResponse")
        assertEquals("No list", second?.objectField("response")?.stringField("error"))
    }
}
