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

    @Test
    fun imagesBecomeBase64SourceBlocks() {
        server.enqueue(sseResponse("{\"type\":\"message_stop\"}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "apa ini", listOf(testImage))))
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        val items = body?.contentItems("messages", 0).orEmpty()
        assertEquals(listOf("image", "text"), items.types())

        val source = items.first().objectField("source")
        assertEquals("base64", source?.stringField("type"))
        assertEquals(testImage.mimeType, source?.stringField("media_type"))
        assertEquals(testImage.data, source?.stringField("data"))
        assertEquals("apa ini", items.last().stringField("text"))
    }

    @Test
    fun mergedUserTurnsKeepEveryImage() {
        server.enqueue(sseResponse("{\"type\":\"message_stop\"}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(ChatRole.USER, "satu", listOf(testImage)),
                        ChatTurn(ChatRole.USER, "dua", listOf(testImage))
                    )
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(1, body?.arrayField("messages")?.size)
        assertEquals(
            listOf("image", "image", "text"),
            body?.contentItems("messages", 0).orEmpty().types()
        )
    }

    @Test
    fun toolDeclarationsCarryAnInputSchema() {
        server.enqueue(sseResponse("{\"type\":\"message_stop\"}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")), tools = listOf(testTool))
            )
        )

        val declared = parseJsonObject(server.takeRequest().body.readUtf8())
            ?.arrayField(WireParams.TOOLS)?.filterIsInstance<JsonObject>().orEmpty()
        assertEquals(1, declared.size)
        assertEquals("create_task", declared.first().stringField("name"))
        assertEquals("object", declared.first().objectField("input_schema")?.stringField("type"))
    }

    @Test
    fun toolsAreLeftOutWhenTheModelRejectsThem() {
        server.enqueue(sseResponse("{\"type\":\"message_stop\"}"))
        collectEvents(
            adapter.stream(
                testCandidate(
                    providerId = "anthropic",
                    baseUrl = server.url("/").toString(),
                    authHeader = AuthHeader.X_API_KEY,
                    wireFormat = WireFormat.ANTHROPIC,
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
    fun toolInputIsJoinedAcrossPartialJsonChunks() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":" +
                    "{\"type\":\"tool_use\",\"id\":\"toolu_a\",\"name\":\"create_task\",\"input\":{}}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":" +
                    "{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"title\\\":\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":" +
                    "{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"beli susu\\\"}\"}}",
                "{\"type\":\"message_stop\"}"
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "key", request()))

        assertEquals("", events.deltaText())
        assertEquals(
            listOf(ToolCall(id = "toolu_a", name = "create_task", arguments = "{\"title\":\"beli susu\"}")),
            events.toolCalls()
        )
        assertTrue(events.completed())
    }

    @Test
    fun parallelToolUseBlocksAreKeyedByIndex() {
        server.enqueue(
            sseResponse(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":" +
                    "{\"type\":\"tool_use\",\"id\":\"toolu_a\",\"name\":\"list_files\"}}",
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":" +
                    "{\"type\":\"tool_use\",\"id\":\"toolu_b\",\"name\":\"read_file\"}}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":" +
                    "{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"b.txt\\\"}\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":" +
                    "{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"path\\\":\\\"a\\\"}\"}}",
                "{\"type\":\"message_stop\"}"
            )
        )

        val calls = collectEvents(adapter.stream(candidate(), "key", request())).toolCalls()

        assertEquals(listOf("list_files", "read_file"), calls.map { it.name })
        assertEquals(listOf("toolu_a", "toolu_b"), calls.map { it.id })
        assertEquals("{\"path\":\"a\"}", calls.first().arguments)
        assertEquals("{\"path\":\"b.txt\"}", calls.last().arguments)
    }

    @Test
    fun parallelToolResultsShareOneUserMessage() {
        server.enqueue(sseResponse("{\"type\":\"message_stop\"}"))
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
                                ToolCall(id = "toolu_a", name = "create_task", arguments = "{\"title\":\"beli susu\"}"),
                                ToolCall(id = "toolu_b", name = "list_tasks", arguments = "{}")
                            )
                        ),
                        ChatTurn(
                            role = ChatRole.TOOL,
                            content = "Task created",
                            toolCallId = "toolu_a",
                            toolName = "create_task"
                        ),
                        ChatTurn(
                            role = ChatRole.TOOL,
                            content = "No list",
                            toolCallId = "toolu_b",
                            toolName = "list_tasks",
                            toolFailed = true
                        )
                    )
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        val messages = body?.arrayField("messages")?.filterIsInstance<JsonObject>().orEmpty()
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.stringField("role") })

        val calls = body?.contentItems("messages", 1).orEmpty()
        assertEquals(listOf("tool_use", "tool_use"), calls.types())
        assertEquals("toolu_a", calls.first().stringField("id"))
        assertEquals(
            "beli susu",
            calls.first().objectField("input")?.stringField("title")
        )

        val results = body?.contentItems("messages", 2).orEmpty()
        assertEquals(listOf("tool_result", "tool_result"), results.types())
        assertEquals("toolu_a", results.first().stringField("tool_use_id"))
        assertEquals("Task created", results.first().stringField("content"))
        assertFalse(results.first().containsKey("is_error"))
        assertEquals("true", results.last()["is_error"].toString())
    }

    @Test
    fun anOrphanToolTurnIsDropped() {
        server.enqueue(sseResponse("{\"type\":\"message_stop\"}"))
        collectEvents(
            adapter.stream(
                candidate(),
                "key",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(
                            role = ChatRole.TOOL,
                            content = "sisa lama",
                            toolCallId = "toolu_x",
                            toolName = "read_file"
                        ),
                        ChatTurn(ChatRole.USER, "halo")
                    )
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals(listOf("user" to "halo"), body?.turns("messages"))
    }
}
