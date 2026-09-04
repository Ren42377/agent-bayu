package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.RouteFailure
import dev.agentbayu.app.ai.tools.ToolCall

sealed interface WireEvent {
    data class Delta(val text: String) : WireEvent

    data class ToolUse(val call: ToolCall) : WireEvent

    data class Usage(val inputTokens: Int, val outputTokens: Int) : WireEvent

    data object Done : WireEvent

    data class Failure(val failure: RouteFailure) : WireEvent
}
