package dev.agentbayu.app.domain.tools

enum class DiffKind {
    KEEP,
    ADD,
    REMOVE
}

data class DiffLine(val kind: DiffKind, val text: String)

object TextDiff {

    const val MAX_PREVIEW_LINES = 400
    const val MAX_ALIGNED_LINES = 800
    const val CONTEXT_LINES = 2

    fun of(before: String, after: String, maxLines: Int = MAX_PREVIEW_LINES): List<DiffLine> {
        if (before == after) return emptyList()
        val old = linesOf(before)
        val new = linesOf(after)
        val aligned = if (old.size > MAX_ALIGNED_LINES || new.size > MAX_ALIGNED_LINES) {
            coarse(old, new)
        } else {
            align(old, new)
        }
        val focused = focus(aligned)
        return if (focused.size <= maxLines) focused else focused.take(maxLines)
    }

    fun added(lines: List<DiffLine>): Int = lines.count { it.kind == DiffKind.ADD }

    fun removed(lines: List<DiffLine>): Int = lines.count { it.kind == DiffKind.REMOVE }

    private fun linesOf(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.split("\n")

    private fun coarse(old: List<String>, new: List<String>): List<DiffLine> =
        old.map { DiffLine(DiffKind.REMOVE, it) } + new.map { DiffLine(DiffKind.ADD, it) }

    private fun align(old: List<String>, new: List<String>): List<DiffLine> {
        val common = Array(old.size + 1) { IntArray(new.size + 1) }
        for (left in old.indices.reversed()) {
            for (right in new.indices.reversed()) {
                common[left][right] = if (old[left] == new[right]) {
                    common[left + 1][right + 1] + 1
                } else {
                    maxOf(common[left + 1][right], common[left][right + 1])
                }
            }
        }
        val result = ArrayList<DiffLine>(old.size + new.size)
        var left = 0
        var right = 0
        while (left < old.size && right < new.size) {
            when {
                old[left] == new[right] -> {
                    result += DiffLine(DiffKind.KEEP, old[left])
                    left += 1
                    right += 1
                }

                common[left + 1][right] >= common[left][right + 1] -> {
                    result += DiffLine(DiffKind.REMOVE, old[left])
                    left += 1
                }

                else -> {
                    result += DiffLine(DiffKind.ADD, new[right])
                    right += 1
                }
            }
        }
        while (left < old.size) {
            result += DiffLine(DiffKind.REMOVE, old[left])
            left += 1
        }
        while (right < new.size) {
            result += DiffLine(DiffKind.ADD, new[right])
            right += 1
        }
        return result
    }

    private fun focus(lines: List<DiffLine>): List<DiffLine> {
        val shown = BooleanArray(lines.size)
        lines.forEachIndexed { index, line ->
            if (line.kind == DiffKind.KEEP) return@forEachIndexed
            val from = (index - CONTEXT_LINES).coerceAtLeast(0)
            val to = (index + CONTEXT_LINES).coerceAtMost(lines.lastIndex)
            for (position in from..to) shown[position] = true
        }
        return lines.filterIndexed { index, _ -> shown[index] }
    }
}
