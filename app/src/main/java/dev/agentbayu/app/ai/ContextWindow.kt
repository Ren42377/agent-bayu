package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatTurn

const val INPUT_BUDGET_SHARE = 0.9
const val MIN_INPUT_BUDGET = 1_024

fun inputTokenBudget(model: ModelEntry): Int {
    val reserved = model.maxOutputTokens.coerceAtMost(model.contextLength / 2)
    val usable = model.contextLength - reserved
    return (usable * INPUT_BUDGET_SHARE).toInt().coerceAtLeast(MIN_INPUT_BUDGET)
}

fun turnTokenCost(turn: ChatTurn): Int =
    TokenUsage.estimateTokens(turn.content) + turn.images.size * ChatImage.TOKEN_COST

fun fitToContext(request: ChatRequest, model: ModelEntry): List<ChatTurn> {
    val turns = request.turns
    if (turns.isEmpty()) return turns
    val budget = inputTokenBudget(model)
    val last = turns.last()
    var used = TokenUsage.estimateTokens(request.systemPrompt.orEmpty()) + turnTokenCost(last)
    val kept = ArrayDeque<ChatTurn>()
    for (index in turns.lastIndex - 1 downTo 0) {
        val turn = turns[index]
        val cost = turnTokenCost(turn)
        if (used + cost > budget) break
        used += cost
        kept.addFirst(turn)
    }
    kept.addLast(last)
    return kept.toList()
}
