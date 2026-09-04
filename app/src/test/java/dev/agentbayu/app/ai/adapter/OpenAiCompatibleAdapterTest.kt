package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.FailureKind
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

class OpenAiCompatibleAdapterTest {

    private lateinit var server: MockWebServer
    private val adapter = OpenAiCompatibleAdapter(adapterTestClient)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/v1").toString()

    private fun request(): ChatRequest = ChatRequest(
        systemPrompt = "You are Bayu.",
        turns = listOf(
            ChatTurn(ChatRole.USER, "pertama"),
            ChatTurn(ChatRole.ASSISTANT, "jawaban"),
            ChatTurn(ChatRole.USER, "lanjut")
        ),
        temperature = 0.4
    )

    @Test
    fun postsAStreamingChatCompletion() {
        server.enqueue(sseResponse("{\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}", "[DONE]"))

        val events = collectEvents(
            adapter.stream(testCandidate(baseUrl = baseUrl(), modelId = "llama-3.3"), "key-123", request())
        )

        assertEquals("ok", events.deltaText())
        assertTrue(events.completed())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer key-123", recorded.getHeader("Authorization"))
        assertEquals("text/event-stream", recorded.getHeader("Accept"))

        val body = parseJsonObject(recorded.body.readUtf8())
        assertEquals("llama-3.3", body?.stringField("model"))
        assertEquals(
            listOf(
                "system" to "You are Bayu.",
                "user" to "pertama",
                "assistant" to "jawaban",
                "user" to "lanjut"
            ),
            body?.turns("messages")
        )
        assertTrue(body?.containsKey("stream") == true)
        assertTrue(body?.containsKey("temperature") == true)
        assertFalse(body?.containsKey(WireParams.STREAM_OPTIONS) == true)
        assertFalse(body?.containsKey(WireParams.MAX_TOKENS) == true)
    }

    @Test
    fun usageIsRequestedOnlyWhenTheProviderSupportsIt() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl(), supportsStreamUsage = true),
                "key",
                request()
            )
        )
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        val streamOptions = body?.objectField(WireParams.STREAM_OPTIONS)
        assertEquals("true", streamOptions?.get("include_usage")?.toString())
    }

    @Test
    fun unsupportedParametersAreLeftOut() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(
                    baseUrl = baseUrl(),
                    supportsStreamUsage = true,
                    providerUnsupportedParams = listOf(WireParams.STREAM_OPTIONS),
                    modelUnsupportedParams = listOf(WireParams.TEMPERATURE)
                ),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "hi")), temperature = 0.4, maxOutputTokens = 256)
            )
        )
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertFalse(body?.containsKey(WireParams.STREAM_OPTIONS) == true)
        assertFalse(body?.containsKey(WireParams.TEMPERATURE) == true)
        assertEquals(256, body?.intField(WireParams.MAX_TOKENS))
        assertEquals(listOf("user" to "hi"), body?.turns("messages"))
    }

    @Test
    fun customAuthPrefixAndExtraHeadersAreSent() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(
                    baseUrl = baseUrl(),
                    authPrefix = "Token",
                    extraHeaders = mapOf("HTTP-Referer" to "https://agent.bayu")
                ),
                "key-9",
                request()
            )
        )
        val recorded = server.takeRequest()
        assertEquals("Token key-9", recorded.getHeader("Authorization"))
        assertEquals("https://agent.bayu", recorded.getHeader("HTTP-Referer"))
    }

    @Test
    fun keylessProvidersSendNoAuthorization() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl(), authKind = AuthKind.NONE, optionalKey = true),
                null,
                request()
            )
        )
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun deltasAreStreamedInOrderWithUsage() {
        server.enqueue(
            sseResponse(
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"Hal\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"lo \"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"Bayu\"}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":7}}",
                "[DONE]"
            )
        )

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        assertEquals("Hallo Bayu", events.deltaText())
        assertEquals(WireEvent.Usage(42, 7), events.lastUsage())
        assertTrue(events.completed())
        assertNull(events.firstFailure())
    }

    @Test
    fun aStreamThatEndsWithoutTheSentinelStillCompletes() {
        server.enqueue(rawSseResponse("data: {\"choices\":[{\"delta\":{\"content\":\"habis\"}}]}\n"))

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        assertEquals("habis", events.deltaText())
        assertTrue(events.completed())
    }

    @Test
    fun rateLimitsBecomeCooldownFailures() {
        server.enqueue(errorResponse(429, "{\"error\":{\"message\":\"slow down\"}}", retryAfter = "20"))

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        val failure = events.firstFailure()
        assertEquals(FailureKind.COOLDOWN, failure?.kind)
        assertEquals(429, failure?.statusCode)
        assertEquals(20_000L, failure?.retryAfterMillis)
        assertFalse(events.completed())
        assertEquals("", events.deltaText())
    }

    @Test
    fun badKeysBecomeTerminalFailures() {
        server.enqueue(errorResponse(401, "{\"error\":{\"message\":\"invalid key\"}}"))

        val failure = collectEvents(
            adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request())
        ).firstFailure()

        assertEquals(FailureKind.TERMINAL, failure?.kind)
        assertEquals(401, failure?.statusCode)
    }

    @Test
    fun errorsInsideTheStreamStopIt() {
        server.enqueue(
            sseResponse(
                "{\"choices\":[{\"delta\":{\"content\":\"separuh\"}}]}",
                "{\"error\":{\"code\":500,\"message\":\"upstream boom\"}}",
                "{\"choices\":[{\"delta\":{\"content\":\"tidak terkirim\"}}]}",
                "[DONE]"
            )
        )

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        assertEquals("separuh", events.deltaText())
        assertEquals(FailureKind.RETRYABLE, events.firstFailure()?.kind)
        assertFalse(events.completed())
    }

    @Test
    fun brokenChunksAreSkipped() {
        server.enqueue(
            sseResponse(
                "{not json",
                "{\"choices\":[{\"delta\":{\"content\":\"tetap\"}}]}",
                "[DONE]"
            )
        )

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        assertEquals("tetap", events.deltaText())
        assertTrue(events.completed())
    }

    @Test
    fun baseUrlOverrideWins() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(
                    baseUrl = "https://unused.example.test/v1",
                    baseUrlOverride = server.url("/local/v1").toString()
                ),
                "key",
                request()
            )
        )
        assertEquals("/local/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun imagesBecomeDataUrlContentParts() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl(), vision = true),
                "key",
                ChatRequest(
                    systemPrompt = "You are Bayu.",
                    turns = listOf(ChatTurn(ChatRole.USER, "apa ini", listOf(testImage)))
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(listOf("system" to "You are Bayu.", "user" to null), body?.turns("messages"))

        val items = body?.contentItems("messages", 1).orEmpty()
        assertEquals(listOf("image_url", "text"), items.types())
        assertEquals(testImage.dataUrl, items.first().objectField("image_url")?.stringField("url"))
        assertEquals("apa ini", items.last().stringField("text"))
    }

    @Test
    fun anImageOnlyTurnCarriesNoTextPart() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl(), vision = true),
                "key",
                ChatRequest(
                    systemPrompt = null,
                    turns = listOf(ChatTurn(ChatRole.USER, "", listOf(testImage)))
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(listOf("image_url"), body?.contentItems("messages", 0).orEmpty().types())
    }

    @Test
    fun toolDeclarationsAreSentAsFunctions() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl()),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")), tools = listOf(testTool))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        val declared = body?.arrayField(WireParams.TOOLS)?.filterIsInstance<JsonObject>().orEmpty()
        assertEquals(listOf("function"), declared.types())
        val function = declared.first().objectField("function")
        assertEquals("create_task", function?.stringField("name"))
        assertEquals("object", function?.objectField("parameters")?.stringField("type"))
    }

    @Test
    fun toolsAreLeftOutWhenTheModelRejectsThem() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl(), modelUnsupportedParams = listOf(WireParams.TOOLS)),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")), tools = listOf(testTool))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertFalse(body?.containsKey(WireParams.TOOLS) == true)
    }

    @Test
    fun toolCallArgumentsAreJoinedAcrossChunks() {
        server.enqueue(
            sseResponse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\"," +
                    "\"function\":{\"name\":\"create_task\",\"arguments\":\"\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                    "\"function\":{\"arguments\":\"{\\\"title\\\":\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0," +
                    "\"function\":{\"arguments\":\"\\\"beli susu\\\"}\"}}]}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                "[DONE]"
            )
        )

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        assertEquals("", events.deltaText())
        assertEquals(
            listOf(ToolCall(id = "call_a", name = "create_task", arguments = "{\"title\":\"beli susu\"}")),
            events.toolCalls()
        )
        assertTrue(events.completed())
    }

    @Test
    fun parallelToolCallsKeepTheirOwnArguments() {
        server.enqueue(
            sseResponse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[" +
                    "{\"index\":0,\"id\":\"call_a\",\"function\":{\"name\":\"list_files\",\"arguments\":\"{\\\"path\\\":\"}}," +
                    "{\"index\":1,\"id\":\"call_b\",\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\"}}" +
                    "]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[" +
                    "{\"index\":1,\"function\":{\"arguments\":\"\\\"b.txt\\\"}\"}}," +
                    "{\"index\":0,\"function\":{\"arguments\":\"\\\"a\\\"}\"}}" +
                    "]}}]}",
                "[DONE]"
            )
        )

        val calls = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request())).toolCalls()

        assertEquals(listOf("list_files", "read_file"), calls.map { it.name })
        assertEquals("{\"path\":\"a\"}", calls.first().arguments)
        assertEquals("{\"path\":\"b.txt\"}", calls.last().arguments)
    }

    @Test
    fun toolCallsAreDroppedWhenTheStreamFails() {
        server.enqueue(
            sseResponse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\"," +
                    "\"function\":{\"name\":\"delete_file\",\"arguments\":\"{}\"}}]}}]}",
                "{\"error\":{\"code\":500,\"message\":\"upstream boom\"}}",
                "[DONE]"
            )
        )

        val events = collectEvents(adapter.stream(testCandidate(baseUrl = baseUrl()), "key", request()))

        assertTrue(events.toolCalls().isEmpty())
        assertEquals(FailureKind.RETRYABLE, events.firstFailure()?.kind)
    }

    @Test
    fun toolResultsAreSentBackAsToolMessages() {
        server.enqueue(sseResponse("[DONE]"))
        collectEvents(
            adapter.stream(
                testCandidate(baseUrl = baseUrl()),
                "key",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(ChatRole.USER, "buat tugas"),
                        ChatTurn(
                            role = ChatRole.ASSISTANT,
                            content = "",
                            toolCalls = listOf(
                                ToolCall(id = "call_a", name = "create_task", arguments = "{\"title\":\"beli susu\"}")
                            )
                        ),
                        ChatTurn(
                            role = ChatRole.TOOL,
                            content = "Task created",
                            toolCallId = "call_a",
                            toolName = "create_task"
                        )
                    )
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(
            listOf("user" to "buat tugas", "assistant" to "", "tool" to "Task created"),
            body?.turns("messages")
        )
        val messages = body?.arrayField("messages")?.filterIsInstance<JsonObject>().orEmpty()
        val call = messages[1].arrayField("tool_calls")?.filterIsInstance<JsonObject>()?.first()
        assertEquals("call_a", call?.stringField("id"))
        assertEquals("create_task", call?.objectField("function")?.stringField("name"))
        assertEquals("{\"title\":\"beli susu\"}", call?.objectField("function")?.stringField("arguments"))
        assertEquals("call_a", messages[2].stringField("tool_call_id"))
    }
}
