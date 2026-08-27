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

    override fun stream(candidate: Candidate, apiKey: String?, request: ChatRequest): Flow<WireEvent> {
        val httpRequest = Request.Builder()
            .url(joinUrl(candidate.baseUrl, CHAT_PATH))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .applyAuth(candidate, apiKey)
            .applyExtraHeaders(candidate)
            .post(body(candidate, request).toString().toRequestBody(StreamingHttp.jsonMediaType))
            .build()

        return StreamingHttp.stream(client, httpRequest, candidate.provider.timeoutMillis) { chunk ->
            parseChunk(chunk)
        }
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
            request.turns.forEach { turn ->
                add(
                    buildJsonObject {
                        put("role", roleName(turn.role))
                        put("content", turn.content)
                    }
                )
            }
        }
        val maxTokens = request.maxOutputTokens
        if (maxTokens != null && WireParams.supports(candidate, WireParams.MAX_TOKENS)) {
            put(WireParams.MAX_TOKENS, maxTokens)
        }
        val temperature = request.temperature
        if (temperature != null && WireParams.supports(candidate, WireParams.TEMPERATURE)) {
            put(WireParams.TEMPERATURE, temperature)
        }
        if (candidate.provider.supportsStreamUsage && WireParams.supports(candidate, WireParams.STREAM_OPTIONS)) {
            putJsonObject(WireParams.STREAM_OPTIONS) {
                put("include_usage", true)
            }
        }
    }

    private fun roleName(role: ChatRole): String = when (role) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }

    private fun parseChunk(raw: String): List<WireEvent> {
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

        root.objectField("usage")?.let { usage ->
            val input = usage.intField("prompt_tokens") ?: 0
            val output = usage.intField("completion_tokens") ?: 0
            if (input > 0 || output > 0) events += WireEvent.Usage(input, output)
        }
        return events
    }

    companion object {
        const val CHAT_PATH = "chat/completions"
        const val STREAM_ERROR_STATUS = 500
    }
}
