package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.ReasoningEffort
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.testCandidate
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
    private val adapter = AntigravityAdapter(adapterTestClient)

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
        projectId: String? = "projects/42",
        maxOutputTokens: Int = 65_536,
        unsupportedParams: List<String> = emptyList()
    ) = testCandidate(
        providerId = "agy",
        modelId = modelId,
        maxOutputTokens = maxOutputTokens,
        baseUrl = server.url("/").toString(),
        projectId = projectId,
        wireFormat = WireFormat.ANTIGRAVITY,
        providerUnsupportedParams = unsupportedParams
    )

    private fun request(
        effort: ReasoningEffort? = ReasoningEffort.HIGH,
        maxOutputTokens: Int? = 512
    ): ChatRequest = ChatRequest(
        systemPrompt = "You are Bayu.",
        turns = listOf(
            ChatTurn(ChatRole.SYSTEM, "diabaikan"),
            ChatTurn(ChatRole.USER, "pertama"),
            ChatTurn(ChatRole.ASSISTANT, "jawaban"),
            ChatTurn(ChatRole.USER, "lanjut")
        ),
        maxOutputTokens = maxOutputTokens,
        temperature = 0.3,
        effort = effort
    )

    private fun textDelta(text: String): String =
        "{\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}]}}"

    private fun contents(body: JsonObject?): List<Pair<String?, String?>> {
        val array = body?.objectField("request")?.arrayField("contents") ?: return emptyList()
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
        server.enqueue(sseResponse(textDelta("ok")))

        val events = collectEvents(adapter.stream(candidate(), "token-x", request()))

        assertEquals("ok", events.deltaText())
        assertTrue(events.completed())

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1internal:streamGenerateContent?alt=sse", recorded.path)
        assertEquals("text/event-stream", recorded.getHeader("Accept"))
        assertEquals("Bearer token-x", recorded.getHeader("Authorization"))
        assertEquals("projects/42", recorded.getHeader("x-goog-user-project"))

        val body = parseJsonObject(recorded.body.readUtf8())
        assertEquals("projects/42", body?.stringField("project"))
        assertEquals("gemini-3.7-flash-tiered", body?.stringField("model"))
        assertEquals("antigravity", body?.stringField("userAgent"))
        assertEquals("agent", body?.stringField("requestType"))
        assertTrue(body?.stringField("requestId").orEmpty().startsWith("agent/"))
        assertNull(body?.get("generationConfig"))

        val inner = body?.objectField("request")
        assertTrue(inner?.stringField("sessionId").orEmpty().startsWith("-"))
        val instruction = inner?.objectField("systemInstruction")
            ?.arrayField("parts")
            ?.firstOrNull() as? JsonObject
        assertEquals("You are Bayu.", instruction?.stringField("text"))
        assertEquals(
            listOf("user" to "pertama", "model" to "jawaban", "user" to "lanjut"),
            contents(body)
        )

        val config = inner?.objectField("generationConfig")
        assertEquals(40, config?.intField("topK"))
        assertTrue(config?.containsKey("topP") == true)
        assertTrue(config?.containsKey("temperature") == true)
        assertEquals(24_577, config?.intField("maxOutputTokens"))
        assertEquals(24_576, config?.objectField("thinkingConfig")?.intField("thinkingBudget"))
        assertEquals(true, config?.objectField("thinkingConfig")?.booleanField("includeThoughts"))
    }

    @Test
    fun sameRoleTurnsMergeAndTheModelIdIsPassedThroughWhenUnaliased() {
        server.enqueue(sseResponse(textDelta("hi")))
        collectEvents(
            adapter.stream(
                candidate(modelId = "gemini-3.1-pro-low"),
                "token-x",
                ChatRequest(
                    turns = listOf(
                        ChatTurn(ChatRole.USER, "satu"),
                        ChatTurn(ChatRole.USER, "dua")
                    ),
                    maxOutputTokens = 4_096,
                    effort = ReasoningEffort.MAX
                )
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals("gemini-3.1-pro-low", body?.stringField("model"))
        assertEquals(listOf("user" to "satu\n\ndua"), contents(body))

        val config = body?.objectField("request")?.objectField("generationConfig")
        assertEquals(16_000, config?.objectField("thinkingConfig")?.intField("thinkingBudget"))
        assertEquals(16_001, config?.intField("maxOutputTokens"))
        assertFalse(config?.containsKey("temperature") == true)
    }

    @Test
    fun noEffortAndUnsupportedReasoningLeaveThinkingOut() {
        server.enqueue(sseResponse(textDelta("hi")))
        collectEvents(
            adapter.stream(
                candidate(modelId = "gemini-3.7-flash-tiered"),
                "token-x",
                request(effort = null)
            )
        )
        val plain = parseJsonObject(server.takeRequest().body.readUtf8())
            ?.objectField("request")
            ?.objectField("generationConfig")
        assertFalse(plain?.containsKey("thinkingConfig") == true)
        assertEquals(512, plain?.intField("maxOutputTokens"))

        server.enqueue(sseResponse(textDelta("hi")))
        collectEvents(
            adapter.stream(
                candidate(unsupportedParams = listOf(WireParams.REASONING)),
                "token-x",
                request()
            )
        )
        val blocked = parseJsonObject(server.takeRequest().body.readUtf8())
            ?.objectField("request")
            ?.objectField("generationConfig")
        assertFalse(blocked?.containsKey("thinkingConfig") == true)
        assertEquals(512, blocked?.intField("maxOutputTokens"))
    }

    @Test
    fun theModelIdTierDrivesTheBudgetWhenNoLevelIsRequested() {
        server.enqueue(sseResponse(textDelta("hi")))
        collectEvents(
            adapter.stream(
                candidate(modelId = "gemini-3.7-flash-low"),
                "token-x",
                request(effort = null)
            )
        )

        val body = parseJsonObject(server.takeRequest().body.readUtf8())
        assertEquals("gemini-3.7-flash-tiered", body?.stringField("model"))
        val config = body?.objectField("request")?.objectField("generationConfig")
        assertEquals(1_024, config?.objectField("thinkingConfig")?.intField("thinkingBudget"))
        assertEquals(1_025, config?.intField("maxOutputTokens"))
    }

    @Test
    fun theRequestIdAndSessionIdFollowTheCliShape() {
        assertEquals("agent/1700000000000/0000002a", antigravityRequestId(1_700_000_000_000L, 42))
        assertEquals("agent/1700000000000/ffffffff", antigravityRequestId(1_700_000_000_000L, -1))

        val session = antigravitySessionId(-7L)
        assertTrue(session.startsWith("-"))
        assertTrue(session.drop(1).all { it.isDigit() })
        assertEquals("-0", antigravitySessionId(0L))
    }

    @Test
    fun onlyTheLevelSuffixesCountAsATier() {
        assertEquals(ReasoningEffort.LOW, antigravityTierEffort("gemini-3.7-flash-low"))
        assertEquals(ReasoningEffort.MEDIUM, antigravityTierEffort("gemini-3.7-flash-medium"))
        assertEquals(ReasoningEffort.HIGH, antigravityTierEffort("gemini-3.7-flash-high"))
        assertNull(antigravityTierEffort("gemini-3.7-flash-tiered"))
        assertNull(antigravityTierEffort("gemini-pro-agent"))
        assertNull(antigravityTierEffort("claude-sonnet-4-6"))
    }

    @Test
    fun aMissingProjectFailsBeforeAnyRequest() {
        val events = collectEvents(adapter.stream(candidate(projectId = null), "token-x", request()))

        assertEquals(FailureKind.TERMINAL, events.firstFailure()?.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun thoughtPartsAreSkippedAndUsageIsReported() {
        val events = parseAntigravityChunk(
            "{\"response\":{\"candidates\":[{\"content\":{\"parts\":[" +
                "{\"text\":\"batin\",\"thought\":true}," +
                "{\"text\":\"tanda\",\"thoughtSignature\":\"abc\"}," +
                "{\"text\":\"halo \"},{\"text\":\"dunia\"}" +
                "]}}],\"usageMetadata\":{\"promptTokenCount\":11,\"candidatesTokenCount\":4}}}"
        )

        assertEquals("halo dunia", events.deltaText())
        assertEquals(WireEvent.Usage(11, 4), events.lastUsage())
    }

    @Test
    fun thoughtFlagsThatAreFalseOrEmptyKeepTheText() {
        val events = parseAntigravityChunk(
            "{\"response\":{\"candidates\":[{\"content\":{\"parts\":[" +
                "{\"text\":\"satu\",\"thought\":false},{\"text\":\"dua\",\"thoughtSignature\":\"\"}" +
                "]}}]}}"
        )

        assertEquals("satudua", events.deltaText())
        assertNull(events.lastUsage())
    }

    @Test
    fun errorEnvelopesBecomeFailures() {
        val nested = parseAntigravityChunk(
            "{\"response\":{\"error\":{\"code\":429,\"message\":\"quota\"}}}"
        )
        val topLevel = parseAntigravityChunk("{\"error\":{\"code\":500,\"message\":\"boom\"}}")

        assertNotNull(nested.firstFailure())
        assertNotNull(topLevel.firstFailure())
        assertTrue(parseAntigravityChunk("[]").isEmpty())
    }

    @Test
    fun modelAliasesCollapseTheEffortSuffixes() {
        assertEquals("gemini-3.7-flash-tiered", resolveAntigravityModelId("gemini-3.7-flash"))
        assertEquals("gemini-3.7-flash-tiered", resolveAntigravityModelId("gemini-3.7-flash-low"))
        assertEquals("gemini-3.7-flash-tiered", resolveAntigravityModelId("gemini-3.7-flash-medium"))
        assertEquals("gemini-3.7-flash-tiered", resolveAntigravityModelId("gemini-3.7-flash-high"))
        assertEquals("gemini-pro-agent", resolveAntigravityModelId("gemini-3.1-pro-high"))
        assertEquals("gpt-oss-120b-medium", resolveAntigravityModelId("gpt-oss-120b"))
        assertEquals("claude-sonnet-4-6", resolveAntigravityModelId("claude-sonnet-4-6"))
    }

    @Test
    fun thinkingBudgetFollowsTheLevelAndTheModelCap() {
        assertEquals(1_024, antigravityThinkingBudget("gemini-pro-agent", ReasoningEffort.LOW))
        assertEquals(10_240, antigravityThinkingBudget("gemini-pro-agent", ReasoningEffort.MEDIUM))
        assertEquals(32_768, antigravityThinkingBudget("gemini-pro-agent", ReasoningEffort.HIGH))
        assertEquals(32_768, antigravityThinkingBudget("gemini-pro-agent", ReasoningEffort.MAX))
        assertEquals(24_576, antigravityThinkingBudget("gemini-3.7-flash-tiered", ReasoningEffort.HIGH))
        assertEquals(1_024, antigravityThinkingBudget("gemini-3.7-flash-tiered", ReasoningEffort.LOW))
        assertEquals(16_000, antigravityThinkingBudget("gemini-3.1-pro-low", ReasoningEffort.XHIGH))
        assertEquals(131_072, antigravityThinkingBudget("gemini-3.9-unknown", ReasoningEffort.XHIGH))
        assertNull(antigravityThinkingBudget("gemini-pro-agent", null))
    }

    @Test
    fun modelsWithoutThinkingGetNoBudget() {
        assertNull(antigravityThinkingBudget("claude-opus-4-6-thinking", ReasoningEffort.MAX))
        assertNull(antigravityThinkingBudget("gpt-oss-120b-medium", ReasoningEffort.HIGH))
        assertNull(antigravityThinkingBudget("tab_flash_lite_preview", ReasoningEffort.LOW))
    }

    @Test
    fun theOutputCeilingClearsTheThinkingBudget() {
        assertEquals(512, antigravityMaxOutputTokens(512, null))
        assertEquals(512, antigravityMaxOutputTokens(512, 0))
        assertEquals(1_025, antigravityMaxOutputTokens(512, 1_024))
        assertEquals(1_025, antigravityMaxOutputTokens(1_024, 1_024))
        assertEquals(2_048, antigravityMaxOutputTokens(2_048, 1_024))
    }
}
