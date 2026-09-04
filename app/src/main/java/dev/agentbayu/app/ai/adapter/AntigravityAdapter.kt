package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.FailureClassifier
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.RouteFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AntigravityAdapter(
    private val client: OkHttpClient,
    private val launchMillis: Long = System.currentTimeMillis()
) : ChatAdapter {

    override fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String>
    ): Flow<WireEvent> {
        val projectId = candidate.connection.projectId?.takeIf { it.isNotBlank() }
            ?: return flowOf(WireEvent.Failure(missingProject()))
        val payload = antigravityBody(
            candidate = candidate,
            request = request,
            projectId = projectId,
            sessionId = antigravitySessionId(candidate.connection.id, launchMillis),
            nowMillis = System.currentTimeMillis()
        )
        val httpRequest = Request.Builder()
            .url(joinUrl(candidate.baseUrl, STREAM_PATH))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .applyAuth(candidate, apiKey)
            .applyExtraHeaders(candidate)
            .applyAuthHeaders(authHeaders)
            .post(payload.toString().toRequestBody(StreamingHttp.jsonMediaType))
            .build()

        val tools = ToolCallBuffer()
        return StreamingHttp.stream(client, httpRequest, candidate.provider.timeoutMillis) { chunk ->
            parseAntigravityChunk(chunk, tools)
        }.releasingToolCalls(tools)
    }

    private fun missingProject(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "sign in again to finish Antigravity project setup"
    )

    companion object {
        const val STREAM_PATH = "/v1internal:streamGenerateContent?alt=sse"
    }
}

internal fun antigravityBody(
    candidate: Candidate,
    request: ChatRequest,
    projectId: String,
    sessionId: String,
    nowMillis: Long
): JsonObject = buildJsonObject {
    val upstreamModel = resolveAntigravityModelId(candidate.model)
    val contents = antigravityTurns(request.turns)
    put(PROJECT, projectId)
    put(
        REQUEST_ID,
        antigravityRequestId(
            sessionId = sessionId,
            upstreamModel = upstreamModel,
            contentCount = contents.size,
            nowMillis = nowMillis
        )
    )
    put(MODEL, upstreamModel)
    put(USER_AGENT, USER_AGENT_VALUE)
    put(REQUEST_TYPE, REQUEST_TYPE_VALUE)
    putJsonObject(REQUEST) {
        put(SESSION_ID, sessionId)
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", prompt) })
                }
            }
        }
        putJsonArray("contents") {
            var index = 0
            while (index < contents.size) {
                if (contents[index].role == ChatRole.TOOL) {
                    var end = index
                    while (end < contents.size && contents[end].role == ChatRole.TOOL) end += 1
                    add(antigravityFunctionResponseContent(contents.subList(index, end)))
                    index = end
                } else {
                    add(antigravityContent(contents[index]))
                    index += 1
                }
            }
        }
        if (request.tools.isNotEmpty() && WireParams.supports(candidate, WireParams.TOOLS)) {
            putGeminiTools(request.tools)
        }
        putJsonObject("generationConfig") {
            val requestedMax = request.maxOutputTokens ?: candidate.model.maxOutputTokens
            put("maxOutputTokens", antigravityMaxOutputTokens(requestedMax))
            val temperature = request.temperature
            if (temperature != null && WireParams.supports(candidate, WireParams.TEMPERATURE)) {
                put("temperature", temperature)
            }
        }
    }
}

private fun antigravityFunctionResponseContent(turns: List<ChatTurn>): JsonObject = buildJsonObject {
    put("role", "user")
    putJsonArray("parts") {
        turns.forEach { turn -> add(geminiFunctionResponsePart(turn)) }
    }
}

private fun antigravityContent(turn: ChatTurn): JsonObject = buildJsonObject {
    put("role", if (turn.role == ChatRole.ASSISTANT) "model" else "user")
    putJsonArray("parts") {
        turn.images.forEach { image ->
            add(
                buildJsonObject {
                    putJsonObject("inlineData") {
                        put("mimeType", image.mimeType)
                        put("data", image.data)
                    }
                }
            )
        }
        if (turn.content.isNotEmpty() || turn.toolCalls.isEmpty()) {
            add(buildJsonObject { put("text", turn.content) })
        }
        turn.toolCalls.forEach { call -> add(geminiFunctionCallPart(call)) }
    }
}

internal fun antigravityTurns(turns: List<ChatTurn>): List<ChatTurn> {
    val conversation = turns.filter { it.role != ChatRole.SYSTEM }
    val merged = ArrayList<ChatTurn>(conversation.size)
    conversation.forEach { turn ->
        val last = merged.lastOrNull()
        if (last != null && last.role == turn.role && !last.carriesTool && !turn.carriesTool) {
            merged[merged.lastIndex] = last.copy(
                content = last.content + "\n\n" + turn.content,
                images = last.images + turn.images
            )
        } else {
            merged += turn
        }
    }
    return merged
}

internal fun resolveAntigravityModelId(modelId: String): String =
    MODEL_ALIASES[modelId] ?: modelId

internal fun resolveAntigravityModelId(model: ModelEntry): String {
    val upstream = model.upstreamId?.takeIf { it.isNotBlank() }
    return upstream ?: resolveAntigravityModelId(model.id)
}

internal fun antigravityUuidFromSeed(seed: String): String {
    val bytes = sha256(seed).copyOf(UUID_BYTES)
    bytes[VERSION_INDEX] = ((bytes[VERSION_INDEX].toInt() and 0x0f) or VERSION_BITS).toByte()
    bytes[VARIANT_INDEX] = ((bytes[VARIANT_INDEX].toInt() and 0x3f) or VARIANT_BITS).toByte()
    val hex = hexOf(bytes)
    return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) +
        "-" + hex.substring(16, 20) + "-" + hex.substring(20)
}

internal fun antigravitySessionId(connectionId: String, launchMillis: Long): String =
    antigravityUuidFromSeed(SESSION_SEED + connectionId + ":" + launchMillis) + launchMillis

internal fun antigravityRequestId(
    sessionId: String,
    upstreamModel: String,
    contentCount: Int,
    nowMillis: Long
): String {
    val conversationId = antigravityUuidFromSeed(CONVERSATION_SEED + sessionId)
    val trajectoryId = antigravityUuidFromSeed(
        TRAJECTORY_SEED + sessionId + ":" + upstreamModel + ":" + REQUEST_TYPE_VALUE
    )
    val step = (contentCount * 2 - 1).coerceAtLeast(1)
    return REQUEST_ID_PREFIX + conversationId + "/" + nowMillis + "/" + trajectoryId + "/" + step
}

internal fun antigravityMaxOutputTokens(requested: Int): Int =
    requested.coerceIn(1, MAX_OUTPUT_TOKENS)

internal fun parseAntigravityChunk(
    raw: String,
    tools: ToolCallBuffer = ToolCallBuffer()
): List<WireEvent> {
    val envelope = parseJsonObject(raw) ?: return emptyList()
    val root = envelope.objectField(RESPONSE) ?: envelope

    (envelope.objectField("error") ?: root.objectField("error"))?.let { error ->
        val status = error.intField("code") ?: STREAM_ERROR_STATUS
        val message = error.stringField("message").orEmpty()
        return listOf(WireEvent.Failure(FailureClassifier.classifyHttp(status, message)))
    }

    val events = ArrayList<WireEvent>(2)
    val candidateNode = root.arrayField("candidates")?.firstOrNull() as? JsonObject
    val parts = candidateNode?.objectField("content")?.arrayField("parts")
    if (parts != null) {
        val text = parts.mapNotNull { element ->
            val part = element as? JsonObject ?: return@mapNotNull null
            val isThought = part.booleanField(THOUGHT) == true ||
                !part.stringField(THOUGHT_SIGNATURE).isNullOrEmpty()
            if (isThought) null else part.stringField("text")
        }.joinToString("")
        if (text.isNotEmpty()) events += WireEvent.Delta(text)
        collectFunctionCalls(parts, tools)
    }

    root.objectField("usageMetadata")?.let { usage ->
        val input = usage.intField("promptTokenCount") ?: 0
        val output = usage.intField("candidatesTokenCount") ?: 0
        if (input > 0 || output > 0) events += WireEvent.Usage(input, output)
    }
    return events
}

private val MODEL_ALIASES = mapOf(
    "gemini-3.7-flash" to "gemini-3.7-flash-tiered(high)",
    "gemini-3.7-flash-tiered" to "gemini-3.7-flash-tiered(high)",
    "gemini-3.7-flash-high" to "gemini-3.7-flash-tiered(high)",
    "gemini-3.7-flash-medium" to "gemini-3.7-flash-tiered(medium)",
    "gemini-3.7-flash-low" to "gemini-3.7-flash-tiered(low)",
    "gemini-3.6-flash-high" to "gemini-3.6-flash-tiered(high)",
    "gemini-3.6-flash-medium" to "gemini-3.6-flash-tiered(medium)",
    "gemini-3.6-flash-low" to "gemini-3.6-flash-tiered(low)",
    "gemini-3.1-pro-high" to "gemini-pro-agent",
    "gpt-oss-120b" to "gpt-oss-120b-medium"
)

private const val PROJECT = "project"
private const val REQUEST_ID = "requestId"
private const val REQUEST_ID_PREFIX = "agent/"
private const val SESSION_ID = "sessionId"
private const val SESSION_SEED = "antigravity:session:"
private const val CONVERSATION_SEED = "antigravity:conversation:"
private const val TRAJECTORY_SEED = "antigravity:trajectory:"
private const val UUID_BYTES = 16
private const val VERSION_INDEX = 6
private const val VERSION_BITS = 0x50
private const val VARIANT_INDEX = 8
private const val VARIANT_BITS = 0x80
private const val MAX_OUTPUT_TOKENS = 64_000
private const val MODEL = "model"
private const val USER_AGENT = "userAgent"
private const val USER_AGENT_VALUE = "antigravity"
private const val REQUEST_TYPE = "requestType"
private const val REQUEST_TYPE_VALUE = "agent"
private const val REQUEST = "request"
private const val RESPONSE = "response"
private const val THOUGHT = "thought"
private const val THOUGHT_SIGNATURE = "thoughtSignature"
private const val STREAM_ERROR_STATUS = 500
