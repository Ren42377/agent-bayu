package dev.agentbayu.app.assistant

import android.app.assist.AssistStructure

object ScreenContextHolder {

    private const val MAX_TEXT_NODES = 80
    private const val MAX_LENGTH = 2000

    @Volatile
    private var screenText: String? = null

    fun update(structure: AssistStructure?) {
        screenText = structure?.let { extractText(it) }
    }

    fun clear() {
        screenText = null
    }

    fun current(): String? = screenText

    private fun extractText(structure: AssistStructure): String? {
        val builder = StringBuilder()
        var nodes = 0
        for (index in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(index).rootViewNode ?: continue
            nodes = collectText(root, builder, nodes)
        }
        return builder.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun collectText(
        node: AssistStructure.ViewNode,
        builder: StringBuilder,
        collected: Int
    ): Int {
        var nodes = collected
        if (nodes >= MAX_TEXT_NODES || builder.length >= MAX_LENGTH) {
            return nodes
        }
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            builder.append(text).append('\n')
            nodes++
        }
        for (index in 0 until node.childCount) {
            nodes = collectText(node.getChildAt(index), builder, nodes)
        }
        return nodes
    }
}
