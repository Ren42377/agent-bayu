package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.FailureClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiCompatibleAdapter(private val client: OkHttpClient) : ChatAdapter {

    override fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String>
    ): Flow<WireEvent> {
        val httpRequest = Request.Builder()
            .url(joinUrl(candidate.baseUrl, CHAT_PATH))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .applyAuth(candidate, apiKey)
            .applyExtraHeaders(candidate)
            .applyAuthHeaders(authHeaders)
            .post(body(candidate, request).toString().toRequestBody(StreamingHttp.jsonMediaType))
            .build()

        val tools = ToolCallBuffer()
        return StreamingHttp.stream(client, httpRequest, candidate.provider.timeoutMillis) { chunk ->
            parseChunk(chunk, tools)
        }.releasingToolCalls(tools)
    }

    private fun body(candidate: Candidate, request: ChatRequest): JsonObject = buildJsonObject {
        put("model", candidate.model.id)
        put("stream", true)
        putJsonArray("messages") {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", prompt)
                    }
                )
            }
            request.turns.forEach { turn -> add(message(turn)) }
        }
        if (request.tools.isNotEmpty() && WireParams.supports(candidate, WireParams.TOOLS)) {
            putFunctionTools(request.tools)
        }
        val maxTokens = request.maxOutputTokens
        if (maxTokens != null && WireParams.supports(candidate, WireParams.MAX_TOKENS)) {
            put(WireParams.MAX_TOKENS, maxTokens)
        }
        val temperature = request.temperature
        if (temperature != null && WireParams.supports(candidate, WireParams.TEMPERATURE)) {
            put(WireParams.TEMPERATURE, temperature)
        }
        val effort = request.effort
        if (effort != null && WireParams.supports(candidate, WireParams.REASONING)) {
            put(REASONING_EFFORT, effort.wireValue)
        }
        if (candidate.provider.supportsStreamUsage && WireParams.supports(candidate, WireParams.STREAM_OPTIONS)) {
            putJsonObject(WireParams.STREAM_OPTIONS) {
                put("include_usage", true)
            }
        }
    }

    private fun message(turn: ChatTurn): JsonObject = buildJsonObject {
        put("role", roleName(turn.role))
        if (turn.role == ChatRole.TOOL) {
            put("tool_call_id", turn.toolCallId.orEmpty())
            put("content", turn.content)
            return@buildJsonObject
        }
        if (turn.images.isEmpty()) {
            put("content", turn.content)
        } else {
            putJsonArray("content") {
                turn.images.forEach { image ->
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", image.dataUrl)
                            }
                        }
                    )
                }
                if (turn.content.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", turn.content)
                        }
                    )
                }
            }
        }
        if (turn.toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                openAiToolCallItems(turn.toolCalls).forEach { item -> add(item) }
            }
        }
    }

    private fun roleName(role: ChatRole): String = when (role) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
        ChatRole.TOOL -> "tool"
    }

    private fun parseChunk(raw: String, tools: ToolCallBuffer): List<WireEvent> {
        val root = parseJsonObject(raw) ?: return emptyList()

        root.objectField("error")?.let { error ->
            val status = error.intField("code") ?: STREAM_ERROR_STATUS
            val message = error.stringField("message").orEmpty()
            return listOf(WireEvent.Failure(FailureClassifier.classifyHttp(status, message)))
        }

        val events = ArrayList<WireEvent>(2)
        val choice = root.arrayField("choices")?.firstOrNull() as? JsonObject
        val delta = choice?.objectField("delta")
        val text = delta?.stringField("content")
        if (!text.isNullOrEmpty()) events += WireEvent.Delta(text)
        delta?.arrayField("tool_calls")?.forEachIndexed { position, element ->
            val entry = element as? JsonObject ?: return@forEachIndexed
            val key = (entry.intField("index") ?: position).toString()
            val function = entry.objectField("function")
            tools.open(key, entry.stringField("id"), function?.stringField("name"))
            tools.append(key, function?.stringField("arguments"))
        }

        root.objectField("usage")?.let { usage ->
            val input = usage.intField("prompt_tokens") ?: 0
            val output = usage.intField("completion_tokens") ?: 0
            if (input > 0 || output > 0) events += WireEvent.Usage(input, output)
        }
        return events
    }

    companion object {
        const val CHAT_PATH = "chat/completions"
        const val REASONING_EFFORT = "reasoning_effort"
        const val STREAM_ERROR_STATUS = 500
    }
}
