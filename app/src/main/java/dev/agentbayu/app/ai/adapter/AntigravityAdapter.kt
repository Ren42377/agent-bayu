package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.FailureClassifier
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.RouteFailure
import java.util.UUID
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

class AntigravityAdapter(private val client: OkHttpClient) : ChatAdapter {

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
            requestId = UUID.randomUUID().toString()
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

        return StreamingHttp.stream(client, httpRequest, candidate.provider.timeoutMillis) { chunk ->
            parseAntigravityChunk(chunk)
        }
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
    requestId: String
): JsonObject = buildJsonObject {
    put(PROJECT, projectId)
    put(REQUEST_ID, requestId)
    put(MODEL, resolveAntigravityModelId(candidate.model.id))
    put(USER_AGENT, USER_AGENT_VALUE)
    put(REQUEST_TYPE, REQUEST_TYPE_VALUE)
    putJsonObject(REQUEST) {
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", prompt) })
                }
            }
        }
        putJsonArray("contents") {
            antigravityTurns(request.turns).forEach { turn ->
                add(
                    buildJsonObject {
                        put("role", if (turn.role == ChatRole.ASSISTANT) "model" else "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", turn.content) })
                        }
                    }
                )
            }
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", request.maxOutputTokens ?: candidate.model.maxOutputTokens)
            put("topK", DEFAULT_TOP_K)
            put("topP", DEFAULT_TOP_P)
            val temperature = request.temperature
            if (temperature != null && WireParams.supports(candidate, WireParams.TEMPERATURE)) {
                put("temperature", temperature)
            }
        }
    }
}

internal fun antigravityTurns(turns: List<ChatTurn>): List<ChatTurn> {
    val conversation = turns.filter { it.role != ChatRole.SYSTEM }
    val merged = ArrayList<ChatTurn>(conversation.size)
    conversation.forEach { turn ->
        val last = merged.lastOrNull()
        if (last != null && last.role == turn.role) {
            merged[merged.lastIndex] = last.copy(content = last.content + "\n\n" + turn.content)
        } else {
            merged += turn
        }
    }
    return merged
}

internal fun resolveAntigravityModelId(modelId: String): String =
    MODEL_ALIASES[modelId] ?: modelId

internal fun parseAntigravityChunk(raw: String): List<WireEvent> {
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
            if (part.containsKey(THOUGHT) || part.containsKey(THOUGHT_SIGNATURE)) {
                null
            } else {
                part.stringField("text")
            }
        }.joinToString("")
        if (text.isNotEmpty()) events += WireEvent.Delta(text)
    }

    root.objectField("usageMetadata")?.let { usage ->
        val input = usage.intField("promptTokenCount") ?: 0
        val output = usage.intField("candidatesTokenCount") ?: 0
        if (input > 0 || output > 0) events += WireEvent.Usage(input, output)
    }
    return events
}

private val MODEL_ALIASES = mapOf(
    "gemini-3.7-flash" to "gemini-3.7-flash-tiered",
    "gemini-3.7-flash-high" to "gemini-3.7-flash-tiered",
    "gemini-3.7-flash-medium" to "gemini-3.7-flash-tiered",
    "gemini-3.7-flash-low" to "gemini-3.7-flash-tiered",
    "gemini-3.1-pro-high" to "gemini-pro-agent",
    "gpt-oss-120b" to "gpt-oss-120b-medium"
)

private const val PROJECT = "project"
private const val REQUEST_ID = "requestId"
private const val MODEL = "model"
private const val USER_AGENT = "userAgent"
private const val USER_AGENT_VALUE = "antigravity"
private const val REQUEST_TYPE = "requestType"
private const val REQUEST_TYPE_VALUE = "agent"
private const val REQUEST = "request"
private const val RESPONSE = "response"
private const val THOUGHT = "thought"
private const val THOUGHT_SIGNATURE = "thoughtSignature"
private const val DEFAULT_TOP_K = 40
private const val DEFAULT_TOP_P = 1.0
private const val STREAM_ERROR_STATUS = 500
