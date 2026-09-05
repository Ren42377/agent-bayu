package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.testCandidate
import dev.agentbayu.app.ai.tools.ToolCall
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AntigravityAdapterTest {

    private lateinit var server: MockWebServer
    private val adapter = AntigravityAdapter(adapterTestClient, LAUNCH_MILLIS)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun candidate(
        modelId: String = "gemini-3.7-flash-high",
        upstreamModelId: String? = "gemini-3.7-flash-tiered(high)",
        projectId: String? = "projects/42",
        maxOutputTokens: Int = 64_000,
        unsupportedParams: List<String> = emptyList()
    ) = testCandidate(
        providerId = "agy",
        modelId = modelId,
        upstreamModelId = upstreamModelId,
        maxOutputTokens = maxOutputTokens,
        baseUrl = server.url("/").toString(),
        projectId = projectId,
        wireFormat = WireFormat.ANTIGRAVITY,
        providerUnsupportedParams = unsupportedParams,
        extraHeaders = mapOf("User-Agent" to IDE_USER_AGENT)
    )

    private fun chat(
        systemPrompt: String? = "be brief",
        turns: List<ChatTurn> = listOf(ChatTurn(ChatRole.USER, "hello")),
        maxOutputTokens: Int? = 512,
        temperature: Double? = 0.4
    ) = ChatRequest(
        systemPrompt = systemPrompt,
        turns = turns,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature
    )

    private fun bodyOf(
        route: Candidate = candidate(),
        request: ChatRequest = chat()
    ): JsonObject {
        server.enqueue(sseResponse(EMPTY_CHUNK))
        collectEvents(adapter.stream(route, "token-1", request))
        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertNotNull(body)
        return body!!
    }

    private fun contents(inner: JsonObject?): List<Pair<String?, String?>> {
        val array = inner?.arrayField("contents") ?: return emptyList()
        return array.mapNotNull { element ->
            val turn = element as? JsonObject ?: return@mapNotNull null
            val text = turn.arrayField("parts")
                ?.mapNotNull { (it as? JsonObject)?.stringField("text") }
                ?.joinToString("")
            turn.stringField("role") to text
        }
    }

    @Test
    fun postsTheEnvelopeToTheInternalStreamEndpoint() {
        server.enqueue(sseResponse(EMPTY_CHUNK))

        collectEvents(adapter.stream(candidate(), "token-1", chat()))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1internal:streamGenerateContent?alt=sse", recorded.path)
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        assertEquals("Bearer token-1", recorded.getHeader("Authorization"))
        assertEquals(IDE_USER_AGENT, recorded.getHeader("User-Agent"))
        assertNull(recorded.getHeader("x-goog-user-project"))

        val body = parseJsonObject(recorded.body.readUtf8())
        assertEquals("projects/42", body?.stringField("project"))
        assertEquals("gemini-3.7-flash-tiered", body?.stringField("model"))
        assertEquals("antigravity", body?.stringField("userAgent"))
        assertEquals("agent", body?.stringField("requestType"))
        assertTrue(IDE_REQUEST_ID.matches(body?.stringField("requestId").orEmpty()))

        val inner = body?.objectField("request")
        assertEquals(antigravitySessionId("conn-1", LAUNCH_MILLIS), inner?.stringField("sessionId"))
        assertEquals(
            "be brief",
            inner?.objectField("systemInstruction")
                ?.arrayField("parts")
                ?.firstOrNull()
                ?.let { it as? JsonObject }
                ?.stringField("text")
        )
        assertEquals(listOf("user" to "hello"), contents(inner))

        val config = inner?.objectField("generationConfig")
        assertNotNull(config?.get("temperature"))
        assertFalse(config?.containsKey("topK") == true)
        assertFalse(config?.containsKey("topP") == true)
        assertFalse(inner?.containsKey("safetySettings") == true)

        val thinking = config?.objectField("thinkingConfig")
        assertEquals("high", thinking?.stringField("thinkingLevel"))
        assertTrue(thinking?.booleanField("includeThoughts") == true)
    }

    @Test
    fun theTierRidesInTheThinkingLevelInsteadOfTheModelId() {
        assertEquals(
            AntigravityModel("gemini-3.8-flash-high", "high"),
            antigravityModel("gemini-3.8-flash-high(high)")
        )
        assertEquals(
            AntigravityModel("gemini-3.6-flash-tiered", "medium"),
            antigravityModel("gemini-3.6-flash-tiered(medium)")
        )
        assertEquals(
            AntigravityModel("gemini-3.7-flash-tiered", "low"),
            antigravityModel(ModelEntry(id = "gemini-3.7-flash-low"))
        )
        assertEquals(
            AntigravityModel("gemini-pro-agent", null),
            antigravityModel(ModelEntry(id = "gemini-3.1-pro-high"))
        )
    }

    @Test
    fun anUnknownTierIsStillStrippedButCarriesNoThinkingLevel() {
        assertEquals(
            AntigravityModel("gemini-3.9-flash", null),
            antigravityModel("gemini-3.9-flash(banana)")
        )
        assertEquals(AntigravityModel("(high)", null), antigravityModel("(high)"))
        assertEquals(AntigravityModel("gemini-pro-agent", null), antigravityModel("gemini-pro-agent"))
        assertEquals(
            AntigravityModel("gemini-3.8-flash-high", "high"),
            antigravityModel("  gemini-3.8-flash-high(HIGH)  ")
        )
    }

    @Test
    fun anUntieredModelCarriesNoThinkingConfig() {
        val body = bodyOf(route = candidate(modelId = "claude-sonnet-4-6", upstreamModelId = null))

        val config = body.objectField("request")?.objectField("generationConfig")
        assertFalse(config?.containsKey("thinkingConfig") == true)
        assertEquals(512, config?.intField("maxOutputTokens"))
    }

    @Test
    fun theMinimalTierAsksTheHostToKeepThoughtsHidden() {
        val body = bodyOf(
            route = candidate(upstreamModelId = "gemini-3.8-flash-minimal(minimal)")
        )

        val config = body.objectField("request")?.objectField("generationConfig")
        assertEquals("gemini-3.8-flash-minimal", body.stringField("model"))
        assertEquals(4_096, config?.intField("maxOutputTokens"))
        val thinking = config?.objectField("thinkingConfig")
        assertEquals("minimal", thinking?.stringField("thinkingLevel"))
        assertFalse(thinking?.booleanField("includeThoughts") == true)
    }

    @Test
    fun theThinkingLevelLiftsTheOutputCeilingSoThoughtsLeaveRoomForText() {
        assertEquals(64_000, antigravityMaxOutputTokens(512, "high"))
        assertEquals(16_384, antigravityMaxOutputTokens(512, "medium"))
        assertEquals(8_192, antigravityMaxOutputTokens(512, "low"))
        assertEquals(4_096, antigravityMaxOutputTokens(512, "minimal"))
        assertEquals(32_768, antigravityMaxOutputTokens(32_768, "low"))
        assertEquals(512, antigravityMaxOutputTokens(512, "banana"))

        val body = bodyOf(request = chat(maxOutputTokens = 512))
        val config = body.objectField("request")?.objectField("generationConfig")
        assertEquals(64_000, config?.intField("maxOutputTokens"))
    }

    @Test
    fun sameRoleTurnsMergeAndTheModelIdIsPassedThroughWhenUnaliased() {
        val body = bodyOf(
            route = candidate(modelId = "claude-sonnet-4-6", upstreamModelId = null),
            request = chat(
                systemPrompt = null,
                turns = listOf(
                    ChatTurn(ChatRole.SYSTEM, "ignored"),
                    ChatTurn(ChatRole.USER, "one"),
                    ChatTurn(ChatRole.USER, "two"),
                    ChatTurn(ChatRole.ASSISTANT, "reply")
                )
            )
        )

        assertEquals("claude-sonnet-4-6", body.stringField("model"))
        val inner = body.objectField("request")
        assertFalse(inner?.containsKey("systemInstruction") == true)
        assertEquals(
            listOf("user" to "one\n\ntwo", "model" to "reply"),
            contents(inner)
        )
    }

    @Test
    fun theOutputCeilingMatchesTheIdeLimit() {
        assertEquals(64_000, antigravityMaxOutputTokens(200_000))
        assertEquals(64_000, antigravityMaxOutputTokens(64_000))
        assertEquals(1, antigravityMaxOutputTokens(0))
        assertEquals(4_096, antigravityMaxOutputTokens(4_096))

        val body = bodyOf(request = chat(maxOutputTokens = 250_000))
        val config = body.objectField("request")?.objectField("generationConfig")
        assertEquals(64_000, config?.intField("maxOutputTokens"))
    }

    @Test
    fun theModelCeilingAppliesWhenTheRequestLeavesItOpen() {
        val body = bodyOf(
            route = candidate(
                modelId = "claude-sonnet-4-6",
                upstreamModelId = null,
                maxOutputTokens = 32_768
            ),
            request = chat(maxOutputTokens = null)
        )

        val config = body.objectField("request")?.objectField("generationConfig")
        assertEquals(32_768, config?.intField("maxOutputTokens"))
    }

    @Test
    fun temperatureIsDroppedWhenTheProviderRejectsIt() {
        val body = bodyOf(
            route = candidate(unsupportedParams = listOf(WireParams.TEMPERATURE))
        )

        val config = body.objectField("request")?.objectField("generationConfig")
        assertFalse(config?.containsKey("temperature") == true)
    }

    @Test
    fun theRequestIdFollowsTheIdeShape() {
        val sessionId = antigravitySessionId("conn-1", LAUNCH_MILLIS)
        val requestId = antigravityRequestId(
            sessionId = sessionId,
            upstreamModel = "gemini-3.7-flash-tiered(high)",
            contentCount = 3,
            nowMillis = LAUNCH_MILLIS
        )

        assertTrue(IDE_REQUEST_ID.matches(requestId))
        val parts = requestId.split("/")
        assertEquals(5, parts.size)
        assertEquals("agent", parts[0])
        assertTrue(UUID_SHAPE.matches(parts[1]))
        assertEquals(LAUNCH_MILLIS.toString(), parts[2])
        assertTrue(UUID_SHAPE.matches(parts[3]))
        assertEquals("5", parts[4])
        assertFalse(parts[1] == parts[3])
    }

    @Test
    fun theRequestIdStepNeverDropsBelowOne() {
        val stepless = antigravityRequestId(
            sessionId = "session",
            upstreamModel = "model",
            contentCount = 0,
            nowMillis = LAUNCH_MILLIS
        )

        assertTrue(IDE_REQUEST_ID.matches(stepless))
        assertEquals("1", stepless.split("/").last())
    }

    @Test
    fun theTrajectoryIdTracksTheModel() {
        val sessionId = antigravitySessionId("conn-1", LAUNCH_MILLIS)
        val first = antigravityRequestId(sessionId, "gemini-3.7-flash-tiered(high)", 1, 1L)
        val second = antigravityRequestId(sessionId, "gemini-3.7-flash-tiered(low)", 1, 1L)

        assertEquals(first.split("/")[1], second.split("/")[1])
        assertFalse(first.split("/")[3] == second.split("/")[3])
    }

    @Test
    fun theSessionIdStaysStablePerConnectionAndLaunch() {
        val session = antigravitySessionId("conn-1", LAUNCH_MILLIS)

        assertEquals(session, antigravitySessionId("conn-1", LAUNCH_MILLIS))
        assertFalse(session == antigravitySessionId("conn-2", LAUNCH_MILLIS))
        assertFalse(session == antigravitySessionId("conn-1", LAUNCH_MILLIS + 1))
        assertTrue(session.endsWith(LAUNCH_MILLIS.toString()))
        assertTrue(UUID_SHAPE.matches(session.removeSuffix(LAUNCH_MILLIS.toString())))
    }

    @Test
    fun modelAliasesCarryTheTierForStaleIds() {
        assertEquals("gemini-3.7-flash-tiered(high)", resolveAntigravityModelId("gemini-3.7-flash"))
        assertEquals(
            "gemini-3.7-flash-tiered(high)",
            resolveAntigravityModelId("gemini-3.7-flash-tiered")
        )
        assertEquals(
            "gemini-3.7-flash-tiered(medium)",
            resolveAntigravityModelId("gemini-3.7-flash-medium")
        )
        assertEquals(
            "gemini-3.6-flash-tiered(low)",
            resolveAntigravityModelId("gemini-3.6-flash-low")
        )
        assertEquals("gemini-pro-agent", resolveAntigravityModelId("gemini-3.1-pro-high"))
        assertEquals("gpt-oss-120b-medium", resolveAntigravityModelId("gpt-oss-120b"))
        assertEquals("gemini-3.1-pro-low", resolveAntigravityModelId("gemini-3.1-pro-low"))
    }

    @Test
    fun theCatalogUpstreamIdWinsOverTheAliasTable() {
        val entry = ModelEntry(
            id = "gemini-3.7-flash",
            upstreamId = "gemini-3.7-flash-tiered(low)"
        )

        assertEquals("gemini-3.7-flash-tiered(low)", resolveAntigravityModelId(entry))
        assertEquals(
            "gemini-3.7-flash-tiered(high)",
            resolveAntigravityModelId(ModelEntry(id = "gemini-3.7-flash"))
        )
        assertEquals(
            "gemini-3.7-flash-tiered(high)",
            resolveAntigravityModelId(ModelEntry(id = "gemini-3.7-flash", upstreamId = " "))
        )
    }

    @Test
    fun aMissingProjectFailsBeforeAnyRequest() {
        val events = collectEvents(
            adapter.stream(candidate(projectId = null), "token-1", chat())
        )

        assertEquals(FailureKind.TERMINAL, events.firstFailure()?.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun thoughtPartsAreSkippedWhileSignedAnswerTextSurvives() {
        val events = parseAntigravityChunk(
            """
            {"response":{"candidates":[{"content":{"parts":[
            {"text":"planning","thought":true},
            {"text":"signed","thoughtSignature":"abc"},
            {"text":"visible"}
            ]}}],"usageMetadata":{"promptTokenCount":11,"candidatesTokenCount":7}}}
            """.trimIndent()
        )

        assertEquals("signedvisible", events.deltaText())
        assertEquals(11, events.lastUsage()?.inputTokens)
        assertEquals(7, events.lastUsage()?.outputTokens)
    }

    @Test
    fun signedThoughtPartsStayHidden() {
        val events = parseAntigravityChunk(
            """
            {"response":{"candidates":[{"content":{"parts":[
            {"text":"planning","thought":true,"thoughtSignature":"abc"},
            {"text":"answer","thoughtSignature":"def"}
            ]}}]}}
            """.trimIndent()
        )

        assertEquals("answer", events.deltaText())
    }

    @Test
    fun errorEnvelopesBecomeFailures() {
        val quota = parseAntigravityChunk(
            """{"error":{"code":429,"message":"Quota exhausted for the model"}}"""
        )
        assertEquals(FailureKind.TERMINAL, quota.firstFailure()?.kind)
        assertEquals(429, quota.firstFailure()?.statusCode)

        val nested = parseAntigravityChunk(
            """{"response":{"error":{"code":503,"message":"backend unavailable"}}}"""
        )
        assertEquals(FailureKind.RETRYABLE, nested.firstFailure()?.kind)

        val codeless = parseAntigravityChunk("""{"error":{"message":"unknown"}}""")
        assertEquals(FailureKind.RETRYABLE, codeless.firstFailure()?.kind)
        assertEquals(500, codeless.firstFailure()?.statusCode)

        assertTrue(parseAntigravityChunk("not json").isEmpty())
        assertTrue(parseAntigravityChunk("""{"response":{"candidates":[]}}""").isEmpty())
    }

    @Test
    fun imagesBecomeInlineDataParts() {
        val body = bodyOf(
            request = chat(turns = listOf(ChatTurn(ChatRole.USER, "apa ini", listOf(testImage))))
        )

        val parts = body.objectField("request")?.parts("contents", 0).orEmpty()
        assertEquals(2, parts.size)

        val inline = parts.first().objectField("inlineData")
        assertEquals(testImage.mimeType, inline?.stringField("mimeType"))
        assertEquals(testImage.data, inline?.stringField("data"))
        assertEquals("apa ini", parts.last().stringField("text"))
    }

    @Test
    fun toolDeclarationsRideInsideTheInnerRequest() {
        val body = bodyOf(
            request = ChatRequest(
                turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")),
                tools = listOf(testTool)
            )
        )

        assertFalse(body.containsKey(WireParams.TOOLS))
        val group = body.objectField("request")?.arrayField(WireParams.TOOLS)?.firstOrNull() as? JsonObject
        val declared = group?.arrayField("functionDeclarations")?.filterIsInstance<JsonObject>().orEmpty()
        assertEquals(1, declared.size)
        assertEquals("create_task", declared.first().stringField("name"))
        assertEquals("object", declared.first().objectField("parameters")?.stringField("type"))
    }

    @Test
    fun toolsAreLeftOutWhenTheProviderRejectsThem() {
        val body = bodyOf(
            route = candidate(unsupportedParams = listOf(WireParams.TOOLS)),
            request = ChatRequest(
                turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")),
                tools = listOf(testTool)
            )
        )

        assertFalse(body.objectField("request")?.containsKey(WireParams.TOOLS) == true)
        assertFalse(body.objectField("request")?.containsKey("toolConfig") == true)
    }

    @Test
    fun toolsSwitchTheFunctionCallingModeToValidated() {
        val body = bodyOf(
            request = ChatRequest(
                turns = listOf(ChatTurn(ChatRole.USER, "buat tugas")),
                tools = listOf(testTool)
            )
        )

        assertEquals(
            "VALIDATED",
            body.objectField("request")
                ?.objectField("toolConfig")
                ?.objectField("functionCallingConfig")
                ?.stringField("mode")
        )
    }

    @Test
    fun aRequestWithoutToolsCarriesNoToolConfig() {
        val body = bodyOf()

        assertFalse(body.objectField("request")?.containsKey("toolConfig") == true)
    }

    @Test
    fun replayedFunctionCallsCarryAnIdAndAThoughtSignature() {
        val body = bodyOf(
            request = chat(
                systemPrompt = null,
                turns = listOf(
                    ChatTurn(ChatRole.USER, "buat tugas"),
                    ChatTurn(
                        role = ChatRole.ASSISTANT,
                        content = "",
                        toolCalls = listOf(
                            ToolCall(
                                id = "call_1",
                                name = "create_task",
                                arguments = "{\"title\":\"beli susu\"}"
                            )
                        )
                    ),
                    ChatTurn(
                        role = ChatRole.TOOL,
                        content = "Task created",
                        toolCallId = "call_1",
                        toolName = "create_task"
                    )
                )
            )
        )

        val inner = body.objectField("request")
        val part = inner?.parts("contents", 1)?.single()
        assertEquals(ANTIGRAVITY_THOUGHT_SIGNATURE, part?.stringField("thoughtSignature"))
        val call = part?.objectField("functionCall")
        assertEquals("call_1", call?.stringField("id"))
        assertEquals("create_task", call?.stringField("name"))

        val response = inner?.parts("contents", 2)?.single()?.objectField("functionResponse")
        assertEquals("call_1", response?.stringField("id"))
        assertEquals("create_task", response?.stringField("name"))
        assertEquals("Task created", response?.objectField("response")?.stringField("result"))
    }

    @Test
    fun functionCallPartsBecomeToolCalls() {
        server.enqueue(
            sseResponse(
                "{\"response\":{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" +
                    "{\"text\":\"planning\",\"thought\":true}," +
                    "{\"functionCall\":{\"name\":\"create_task\",\"args\":{\"title\":\"beli susu\"}}}" +
                    "]}}]}}",
                EMPTY_CHUNK
            )
        )

        val events = collectEvents(adapter.stream(candidate(), "token-1", chat()))

        assertEquals("", events.deltaText())
        assertEquals(
            listOf(ToolCall(id = "call_1", name = "create_task", arguments = "{\"title\":\"beli susu\"}")),
            events.toolCalls()
        )
        assertTrue(events.completed())
    }

    @Test
    fun parallelFunctionCallsKeepTheirOwnArguments() {
        val buffer = ToolCallBuffer()
        parseAntigravityChunk(
            """{"response":{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"list_files","args":{"path":"a"}}},
            {"functionCall":{"name":"read_file","args":{"path":"b.txt"}}}
            ]}}]}}""".trimIndent(),
            buffer
        )

        val calls = buffer.release()
        assertEquals(listOf("list_files", "read_file"), calls.map { it.name })
        assertEquals("{\"path\":\"a\"}", calls.first().arguments)
        assertEquals("{\"path\":\"b.txt\"}", calls.last().arguments)
    }

    @Test
    fun parallelToolResultsShareOneUserContent() {
        val body = bodyOf(
            request = chat(
                systemPrompt = null,
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

        val inner = body.objectField("request")
        assertEquals(listOf("user" to "buat tugas", "model" to "", "user" to ""), contents(inner))

        val calls = inner?.parts("contents", 1).orEmpty()
        assertEquals(2, calls.size)
        assertEquals("create_task", calls.first().objectField("functionCall")?.stringField("name"))
        assertEquals(
            "beli susu",
            calls.first().objectField("functionCall")?.objectField("args")?.stringField("title")
        )

        val results = inner?.parts("contents", 2).orEmpty()
        assertEquals(2, results.size)
        val first = results.first().objectField("functionResponse")
        assertEquals("create_task", first?.stringField("name"))
        assertEquals("Task created", first?.objectField("response")?.stringField("result"))
        val second = results.last().objectField("functionResponse")
        assertEquals("No list", second?.objectField("response")?.stringField("error"))
    }

    private companion object {
        const val LAUNCH_MILLIS = 1_700_000_000_000L
        const val IDE_USER_AGENT = "antigravity/ide/2.11.0 darwin/arm64"
        const val EMPTY_CHUNK = """{"response":{"candidates":[]}}"""
        val IDE_REQUEST_ID = Regex("^agent/[^/]+/\\d+/[^/]+/\\d+$")
        val UUID_SHAPE =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
