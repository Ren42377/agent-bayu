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

class OpenAiResponsesAdapter(private val client: OkHttpClient) : ChatAdapter {

    override fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String>
    ): Flow<WireEvent> {
        val httpRequest = Request.Builder()
            .url(joinUrl(candidate.baseUrl, RESPONSES_PATH))
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
        put("model", candidate.model.wireId)
        put("instructions", instructionsOf(request))
        putJsonArray("input") {
            request.turns.filter { it.role != ChatRole.SYSTEM }.forEach { turn ->
                if (turn.role == ChatRole.TOOL) {
                    add(responsesFunctionOutputItem(turn))
                    return@forEach
                }
                if (turn.toolCalls.isEmpty() || turn.content.isNotEmpty()) add(messageItem(turn))
                turn.toolCalls.forEach { call -> add(responsesFunctionCallItem(call)) }
            }
        }
        put("stream", true)
        put("store", false)
        if (request.tools.isNotEmpty() && WireParams.supports(candidate, WireParams.TOOLS)) {
            putResponsesTools(request.tools)
        }
        val effort = request.effort
        if (effort != null && WireParams.supports(candidate, WireParams.REASONING)) {
            putJsonObject("reasoning") {
                put("effort", effort.wireValue)
                put("summary", REASONING_SUMMARY)
            }
        }
    }

    private fun messageItem(turn: ChatTurn): JsonObject = buildJsonObject {
        put("type", "message")
        put("role", roleName(turn.role))
        putJsonArray("content") {
            if (turn.role != ChatRole.ASSISTANT) {
                turn.images.forEach { image ->
                    add(
                        buildJsonObject {
                            put("type", INPUT_IMAGE)
                            put("image_url", image.dataUrl)
                        }
                    )
                }
            }
            add(
                buildJsonObject {
                    put("type", contentType(turn.role))
                    put("text", turn.content)
                }
            )
        }
    }

    private fun instructionsOf(request: ChatRequest): String {
        val prompt = request.systemPrompt?.takeIf { it.isNotBlank() }
        val leading = request.turns.filter { it.role == ChatRole.SYSTEM }.map { it.content }
        val parts = (listOfNotNull(prompt) + leading).filter { it.isNotBlank() }
        if (parts.isEmpty()) return DEFAULT_INSTRUCTIONS
        return parts.joinToString("\n\n")
    }

    private fun roleName(role: ChatRole): String =
        if (role == ChatRole.ASSISTANT) "assistant" else "user"

    private fun contentType(role: ChatRole): String =
        if (role == ChatRole.ASSISTANT) OUTPUT_TEXT else INPUT_TEXT

    private fun parseChunk(raw: String, tools: ToolCallBuffer): List<WireEvent> {
        val root = parseJsonObject(raw) ?: return emptyList()
        val type = root.stringField("type")
        val error = root.objectField("error")
            ?: root.objectField("response")?.objectField("error")
        if (error != null || type == FAILED_TYPE || type == ERROR_TYPE) {
            val message = error?.stringField("message").orEmpty()
            return listOf(WireEvent.Failure(FailureClassifier.classifyHttp(statusOf(error), message)))
        }

        return when (type) {
            DELTA_TYPE -> {
                val text = root.stringField("delta")
                if (text.isNullOrEmpty()) emptyList() else listOf(WireEvent.Delta(text))
            }

            OUTPUT_ITEM_ADDED_TYPE -> {
                val item = root.objectField("item")
                if (item?.stringField("type") == FUNCTION_CALL_ITEM) {
                    val id = item.stringField("call_id") ?: item.stringField("id")
                    tools.open(toolKey(root), id, item.stringField("name"))
                    tools.append(toolKey(root), item.stringField("arguments"))
                }
                emptyList()
            }

            ARGUMENTS_DELTA_TYPE -> {
                tools.append(toolKey(root), root.stringField("delta"))
                emptyList()
            }

            ARGUMENTS_DONE_TYPE -> {
                tools.replace(toolKey(root), root.stringField("arguments"))
                emptyList()
            }

            COMPLETED_TYPE -> {
                val events = ArrayList<WireEvent>(2)
                root.objectField("response")?.objectField("usage")?.let { usage ->
                    val input = usage.intField("input_tokens") ?: 0
                    val output = usage.intField("output_tokens") ?: 0
                    if (input > 0 || output > 0) events += WireEvent.Usage(input, output)
                }
                events += WireEvent.Done
                events
            }

            else -> emptyList()
        }
    }

    private fun toolKey(root: JsonObject): String =
        root.intField("output_index")?.toString()
            ?: root.stringField("item_id")
            ?: DEFAULT_TOOL_KEY

    private fun statusOf(error: JsonObject?): Int {
        if (error == null) return STREAM_ERROR_STATUS
        error.intField("code")?.let { return it }
        val code = error.stringField("code")?.lowercase().orEmpty()
        return when {
            code.contains("rate_limit") -> RATE_LIMIT_STATUS
            code.contains("server") -> STREAM_ERROR_STATUS
            code.isEmpty() -> STREAM_ERROR_STATUS
            else -> BAD_REQUEST_STATUS
        }
    }

    companion object {
        const val RESPONSES_PATH = "responses"
        const val REASONING_SUMMARY = "auto"
        const val INPUT_TEXT = "input_text"
        const val OUTPUT_TEXT = "output_text"
        const val INPUT_IMAGE = "input_image"
        const val DELTA_TYPE = "response.output_text.delta"
        const val COMPLETED_TYPE = "response.completed"
        const val FAILED_TYPE = "response.failed"
        const val ERROR_TYPE = "error"
        const val OUTPUT_ITEM_ADDED_TYPE = "response.output_item.added"
        const val ARGUMENTS_DELTA_TYPE = "response.function_call_arguments.delta"
        const val ARGUMENTS_DONE_TYPE = "response.function_call_arguments.done"
        const val FUNCTION_CALL_ITEM = "function_call"
        const val DEFAULT_TOOL_KEY = "0"
        const val DEFAULT_INSTRUCTIONS = "You are a helpful assistant."
        const val STREAM_ERROR_STATUS = 500
        const val RATE_LIMIT_STATUS = 429
        const val BAD_REQUEST_STATUS = 400
    }
}
