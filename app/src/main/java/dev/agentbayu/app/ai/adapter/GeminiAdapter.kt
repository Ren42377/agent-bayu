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

class GeminiAdapter(private val client: OkHttpClient) : ChatAdapter {

    override fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String>
    ): Flow<WireEvent> {
        val path = MODELS_PATH + candidate.model.id + STREAM_SUFFIX
        val httpRequest = Request.Builder()
            .url(joinUrl(candidate.baseUrl, path))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .applyAuth(candidate, apiKey)
            .applyExtraHeaders(candidate)
            .applyAuthHeaders(authHeaders)
            .post(body(candidate, request).toString().toRequestBody(StreamingHttp.jsonMediaType))
            .build()

        return StreamingHttp.stream(client, httpRequest, candidate.provider.timeoutMillis) { chunk ->
            parseChunk(chunk)
        }
    }

    private fun body(candidate: Candidate, request: ChatRequest): JsonObject = buildJsonObject {
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", prompt) })
                }
            }
        }
        putJsonArray("contents") {
            mergeTurns(request.turns).forEach { turn ->
                add(
                    buildJsonObject {
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
                            add(buildJsonObject { put("text", turn.content) })
                        }
                    }
                )
            }
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", request.maxOutputTokens ?: candidate.model.maxOutputTokens)
            val temperature = request.temperature
            if (temperature != null && WireParams.supports(candidate, WireParams.TEMPERATURE)) {
                put("temperature", temperature)
            }
        }
    }

    private fun mergeTurns(turns: List<ChatTurn>): List<ChatTurn> {
        val conversation = turns.filter { it.role != ChatRole.SYSTEM }
        val merged = ArrayList<ChatTurn>(conversation.size)
        conversation.forEach { turn ->
            val last = merged.lastOrNull()
            if (last != null && last.role == turn.role) {
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

    private fun parseChunk(raw: String): List<WireEvent> {
        val root = parseJsonObject(raw) ?: return emptyList()

        root.objectField("error")?.let { error ->
            val status = error.intField("code") ?: STREAM_ERROR_STATUS
            val message = error.stringField("message").orEmpty()
            return listOf(WireEvent.Failure(FailureClassifier.classifyHttp(status, message)))
        }

        val events = ArrayList<WireEvent>(2)
        val candidateNode = root.arrayField("candidates")?.firstOrNull() as? JsonObject
        val parts = candidateNode?.objectField("content")?.arrayField("parts")
        if (parts != null) {
            val text = parts.mapNotNull { (it as? JsonObject)?.stringField("text") }.joinToString("")
            if (text.isNotEmpty()) events += WireEvent.Delta(text)
        }

        root.objectField("usageMetadata")?.let { usage ->
            val input = usage.intField("promptTokenCount") ?: 0
            val output = usage.intField("candidatesTokenCount") ?: 0
            if (input > 0 || output > 0) events += WireEvent.Usage(input, output)
        }
        return events
    }

    companion object {
        const val MODELS_PATH = "v1beta/models/"
        const val STREAM_SUFFIX = ":streamGenerateContent?alt=sse"
        const val STREAM_ERROR_STATUS = 500
    }
}
