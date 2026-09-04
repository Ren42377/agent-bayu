package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.tools.ToolCall
import dev.agentbayu.app.ai.tools.ToolSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal const val EMPTY_TOOL_ARGUMENTS = "{}"

internal class ToolCallBuffer {

    private val slots = LinkedHashMap<String, Slot>()
    private var generated = 0

    fun open(key: String, id: String?, name: String?) {
        val slot = slotAt(key)
        if (!id.isNullOrEmpty()) slot.id = id
        if (!name.isNullOrEmpty()) slot.name = name
    }

    fun append(key: String, fragment: String?) {
        if (fragment.isNullOrEmpty()) return
        slotAt(key).arguments.append(fragment)
    }

    fun replace(key: String, arguments: String?) {
        if (arguments.isNullOrEmpty()) return
        val slot = slots[key] ?: return
        slot.arguments.setLength(0)
        slot.arguments.append(arguments)
    }

    fun whole(name: String, arguments: String) {
        val id = nextId()
        val slot = slots.getOrPut(id) { Slot(id) }
        slot.name = name
        slot.arguments.append(arguments)
    }

    fun release(): List<ToolCall> {
        val calls = slots.values
            .filter { slot -> slot.name.isNotEmpty() }
            .map { slot -> ToolCall(id = slot.id, name = slot.name, arguments = argumentsOf(slot)) }
        slots.clear()
        return calls
    }

    private fun slotAt(key: String): Slot = slots.getOrPut(key) { Slot(nextId()) }

    private fun argumentsOf(slot: Slot): String {
        val text = slot.arguments.toString().trim()
        return if (text.isEmpty()) EMPTY_TOOL_ARGUMENTS else text
    }

    private fun nextId(): String {
        generated += 1
        return GENERATED_PREFIX + generated
    }

    private class Slot(var id: String) {
        var name: String = ""
        val arguments = StringBuilder()
    }

    private companion object {
        const val GENERATED_PREFIX = "call_"
    }
}

internal fun Flow<WireEvent>.releasingToolCalls(buffer: ToolCallBuffer): Flow<WireEvent> = flow {
    var failed = false
    collect { event ->
        if (event is WireEvent.Failure) failed = true
        if (event is WireEvent.Done) {
            for (call in buffer.release()) emit(WireEvent.ToolUse(call))
        }
        emit(event)
    }
    if (!failed) {
        for (call in buffer.release()) emit(WireEvent.ToolUse(call))
    }
}

internal fun argumentsObject(arguments: String): JsonObject =
    parseJsonObject(arguments) ?: buildJsonObject { }

internal fun JsonObjectBuilder.putFunctionTools(tools: List<ToolSpec>) {
    putJsonArray(WireParams.TOOLS) {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", FUNCTION)
                    putJsonObject(FUNCTION) {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }
                }
            )
        }
    }
}

internal fun JsonObjectBuilder.putResponsesTools(tools: List<ToolSpec>) {
    putJsonArray(WireParams.TOOLS) {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", FUNCTION)
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameters)
                }
            )
        }
    }
}

internal fun JsonObjectBuilder.putAnthropicTools(tools: List<ToolSpec>) {
    putJsonArray(WireParams.TOOLS) {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("input_schema", tool.parameters)
                }
            )
        }
    }
}

internal fun JsonObjectBuilder.putGeminiTools(tools: List<ToolSpec>) {
    putJsonArray(WireParams.TOOLS) {
        add(
            buildJsonObject {
                putJsonArray("functionDeclarations") {
                    tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        )
                    }
                }
            }
        )
    }
}

internal fun openAiToolCallItems(calls: List<ToolCall>): List<JsonObject> = calls.map { call ->
    buildJsonObject {
        put("id", call.id)
        put("type", FUNCTION)
        putJsonObject(FUNCTION) {
            put("name", call.name)
            put("arguments", call.arguments)
        }
    }
}

internal fun responsesFunctionCallItem(call: ToolCall): JsonObject = buildJsonObject {
    put("type", "function_call")
    put("call_id", call.id)
    put("name", call.name)
    put("arguments", call.arguments)
}

internal fun responsesFunctionOutputItem(turn: ChatTurn): JsonObject = buildJsonObject {
    put("type", "function_call_output")
    put("call_id", turn.toolCallId.orEmpty())
    put("output", turn.content)
}

internal fun anthropicToolUseBlock(call: ToolCall): JsonObject = buildJsonObject {
    put("type", "tool_use")
    put("id", call.id)
    put("name", call.name)
    put("input", argumentsObject(call.arguments))
}

internal fun anthropicToolResultBlock(turn: ChatTurn): JsonObject = buildJsonObject {
    put("type", "tool_result")
    put("tool_use_id", turn.toolCallId.orEmpty())
    put("content", turn.content)
    if (turn.toolFailed) put("is_error", true)
}

internal fun geminiFunctionCallPart(call: ToolCall): JsonObject = buildJsonObject {
    putJsonObject("functionCall") {
        put("name", call.name)
        put("args", argumentsObject(call.arguments))
    }
}

internal fun collectFunctionCalls(parts: JsonArray, tools: ToolCallBuffer) {
    parts.forEach { element ->
        val call = (element as? JsonObject)?.objectField("functionCall") ?: return@forEach
        val name = call.stringField("name")
        if (name.isNullOrEmpty()) return@forEach
        tools.whole(name, call.objectField("args")?.toString() ?: EMPTY_TOOL_ARGUMENTS)
    }
}

internal fun geminiFunctionResponsePart(turn: ChatTurn): JsonObject = buildJsonObject {
    putJsonObject("functionResponse") {
        put("name", turn.toolName.orEmpty())
        putJsonObject("response") {
            if (turn.toolFailed) put("error", turn.content) else put("result", turn.content)
        }
    }
}

private const val FUNCTION = "function"
