package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.ai.adapter.booleanField
import dev.agentbayu.app.ai.adapter.intField
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class ToolField(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val needsVision: Boolean = false
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class ToolResult(
    val callId: String,
    val name: String,
    val content: String,
    val isError: Boolean = false,
    val images: List<ChatImage> = emptyList()
)

interface ToolHandler {
    val spec: ToolSpec

    suspend fun run(call: ToolCall): ToolResult
}

class ToolRegistry(handlers: List<ToolHandler> = emptyList()) {

    private val byName = handlers.associateBy { it.spec.name }

    val specs: List<ToolSpec> = handlers.map { it.spec }

    suspend fun run(call: ToolCall): ToolResult {
        val handler = byName[call.name] ?: return ToolResult(
            callId = call.id,
            name = call.name,
            content = "Unknown tool: " + call.name,
            isError = true
        )
        return handler.run(call)
    }
}

class ToolArguments(raw: String) {

    private val root = parseJsonObject(raw)

    fun text(field: String): String? = root?.stringField(field)?.takeIf { it.isNotEmpty() }

    fun raw(field: String): String? = root?.stringField(field)

    fun flag(field: String, fallback: Boolean = false): Boolean =
        root?.booleanField(field) ?: fallback

    fun number(field: String, fallback: Int): Int = root?.intField(field) ?: fallback
}

fun ToolCall.reply(content: String, images: List<ChatImage> = emptyList()): ToolResult =
    ToolResult(callId = id, name = name, content = content, images = images)

fun ToolCall.problem(reason: String): ToolResult =
    ToolResult(callId = id, name = name, content = reason, isError = true)

fun toolSchema(vararg fields: ToolField): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        fields.forEach { field ->
            putJsonObject(field.name) {
                put("type", field.type)
                put("description", field.description)
            }
        }
    }
    putJsonArray("required") {
        fields.filter { it.required }.forEach { field -> add(field.name) }
    }
}
