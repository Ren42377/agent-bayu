package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.RouteFailure
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

internal fun JsonObject.turns(field: String): List<Pair<String?, String?>> {
    val array = arrayField(field) ?: return emptyList()
    return array.mapNotNull { element ->
        val turn = element as? JsonObject ?: return@mapNotNull null
        turn.stringField("role") to turn.stringField("content")
    }
}
