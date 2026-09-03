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

class AnthropicAdapter(private val client: OkHttpClient) : ChatAdapter {

    override fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String>
    ): Flow<WireEvent> {
        val httpRequest = Request.Builder()
            .url(joinUrl(candidate.baseUrl, MESSAGES_PATH))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .header(VERSION_HEADER, VERSION_VALUE)
            .applyAuth(candidate, apiKey)
            .applyExtraHeaders(candidate)
            .applyAuthHeaders(authHeaders)
            .post(body(candidate, request).toString().toRequestBody(StreamingHttp.jsonMediaType))
            .build()

        var inputTokens = 0
        var outputTokens = 0

        return StreamingHttp.stream(client, httpRequest, candidate.provider.timeoutMillis) { chunk ->
            val root = parseJsonObject(chunk)
            if (root == null) {
                emptyList()
            } else {
                val events = ArrayList<WireEvent>(2)
                val type = root.stringField("type")

                root.objectField("error")?.let { error ->
                    val message = error.stringField("message").orEmpty()
                    val status = if (error.stringField("type") == OVERLOADED_TYPE) {
                        OVERLOADED_STATUS
                    } else {
                        STREAM_ERROR_STATUS
                    }
                    return@stream listOf(WireEvent.Failure(FailureClassifier.classifyHttp(status, message)))
                }

                if (type == CONTENT_BLOCK_DELTA) {
                    val text = root.objectField("delta")?.stringField("text")
                    if (!text.isNullOrEmpty()) events += WireEvent.Delta(text)
                }

                val usage = root.objectField("message")?.objectField("usage") ?: root.objectField("usage")
                if (usage != null) {
                    usage.intField("input_tokens")?.let { inputTokens = it }
                    usage.intField("output_tokens")?.let { outputTokens = it }
                    if (inputTokens > 0 || outputTokens > 0) {
                        events += WireEvent.Usage(inputTokens, outputTokens)
                    }
                }
                events
            }
        }
    }

    private fun body(candidate: Candidate, request: ChatRequest): JsonObject = buildJsonObject {
        put("model", candidate.model.id)
        put("stream", true)
        put(
            WireParams.MAX_TOKENS,
            request.maxOutputTokens ?: candidate.model.maxOutputTokens
        )
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("system", it) }
        putJsonArray("messages") {
            normalizeTurns(request.turns).forEach { turn ->
                add(
                    buildJsonObject {
                        put("role", if (turn.role == ChatRole.ASSISTANT) "assistant" else "user")
                        if (turn.images.isEmpty()) {
                            put("content", turn.content)
                        } else {
                            putJsonArray("content") {
                                turn.images.forEach { image ->
                                    add(
                                        buildJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", image.mimeType)
                                                put("data", image.data)
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
                    }
                )
            }
        }
        val temperature = request.temperature
        if (temperature != null && WireParams.supports(candidate, WireParams.TEMPERATURE)) {
            put(WireParams.TEMPERATURE, temperature)
        }
    }

    private fun normalizeTurns(turns: List<ChatTurn>): List<ChatTurn> {
        val conversation = turns.filter { it.role != ChatRole.SYSTEM }
        val trimmed = conversation.dropWhile { it.role == ChatRole.ASSISTANT }
        val merged = ArrayList<ChatTurn>(trimmed.size)
        trimmed.forEach { turn ->
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
        if (merged.isEmpty()) return listOf(ChatTurn(ChatRole.USER, EMPTY_PROMPT_FALLBACK))
        return merged
    }

    companion object {
        const val MESSAGES_PATH = "v1/messages"
        const val VERSION_HEADER = "anthropic-version"
        const val VERSION_VALUE = "2023-06-01"
        const val CONTENT_BLOCK_DELTA = "content_block_delta"
        const val OVERLOADED_TYPE = "overloaded_error"
        const val OVERLOADED_STATUS = 529
        const val STREAM_ERROR_STATUS = 500
        const val EMPTY_PROMPT_FALLBACK = "Hello"
    }
}
