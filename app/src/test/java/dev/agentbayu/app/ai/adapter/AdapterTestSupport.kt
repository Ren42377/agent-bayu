package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.RouteFailure
import dev.agentbayu.app.ai.tools.ToolCall
import dev.agentbayu.app.ai.tools.ToolField
import dev.agentbayu.app.ai.tools.ToolSpec
import dev.agentbayu.app.ai.tools.toolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse

internal val adapterTestClient = OkHttpClient()

internal fun sseResponse(vararg chunks: String): MockResponse = MockResponse()
    .setHeader("Content-Type", "text/event-stream")
    .setBody(chunks.joinToString("") { chunk -> "data: " + chunk + "\n\n" })

internal fun rawSseResponse(body: String): MockResponse = MockResponse()
    .setHeader("Content-Type", "text/event-stream")
    .setBody(body)

internal fun errorResponse(code: Int, body: String, retryAfter: String? = null): MockResponse {
    val response = MockResponse().setResponseCode(code).setBody(body)
    if (retryAfter != null) response.setHeader("Retry-After", retryAfter)
    return response
}

internal fun collectEvents(flow: Flow<WireEvent>): List<WireEvent> = runBlocking { flow.toList() }

internal fun List<WireEvent>.deltaText(): String =
    filterIsInstance<WireEvent.Delta>().joinToString("") { it.text }

internal fun List<WireEvent>.lastUsage(): WireEvent.Usage? =
    filterIsInstance<WireEvent.Usage>().lastOrNull()

internal fun List<WireEvent>.firstFailure(): RouteFailure? =
    filterIsInstance<WireEvent.Failure>().firstOrNull()?.failure

internal fun List<WireEvent>.completed(): Boolean = contains(WireEvent.Done)

internal fun List<WireEvent>.toolCalls(): List<ToolCall> =
    filterIsInstance<WireEvent.ToolUse>().map { it.call }

internal val testTool = ToolSpec(
    name = "create_task",
    description = "Create a task for the owner",
    parameters = toolSchema(
        ToolField(name = "title", type = "string", description = "Task title")
    )
)

internal fun JsonObject.turns(field: String): List<Pair<String?, String?>> {
    val array = arrayField(field) ?: return emptyList()
    return array.mapNotNull { element ->
        val turn = element as? JsonObject ?: return@mapNotNull null
        turn.stringField("role") to turn.stringField("content")
    }
}

internal val testImage = ChatImage(mimeType = "image/jpeg", data = "QUJDRA==")

internal fun JsonObject.contentItems(field: String, index: Int): List<JsonObject> =
    itemsOf(field, index, "content")

internal fun JsonObject.parts(field: String, index: Int): List<JsonObject> =
    itemsOf(field, index, "parts")

internal fun List<JsonObject>.types(): List<String?> = map { it.stringField("type") }

private fun JsonObject.itemsOf(field: String, index: Int, key: String): List<JsonObject> {
    val array = arrayField(field) ?: return emptyList()
    val turn = array.getOrNull(index) as? JsonObject ?: return emptyList()
    return turn.arrayField(key).orEmpty().filterIsInstance<JsonObject>()
}
