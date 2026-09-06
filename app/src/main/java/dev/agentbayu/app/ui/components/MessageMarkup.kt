package dev.agentbayu.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

internal sealed interface MarkupBlock {
    data class Markdown(val source: String) : MarkupBlock

    data class Math(val latex: String) : MarkupBlock
}

internal sealed interface InlineRun {
    data class Text(val value: String) : InlineRun

    data class Math(val latex: String) : InlineRun
}

private val BLOCK_MATH = Regex("\\\$\\\$(.+?)\\\$\\\$|\\\\\\[(.+?)\\\\]", RegexOption.DOT_MATCHES_ALL)

private val INLINE_MATH = Regex("\\\$(\\S(?:[^\\n\$]*\\S)?)\\\$|\\\\\\((.+?)\\\\\\)")

private val INLINE_MARKDOWN = Regex("\\*\\*(.+?)\\*\\*|(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])|`([^`\\n]+)`")

internal fun splitMarkup(source: String): List<MarkupBlock> {
    if (!source.contains('$') && !source.contains("\\[")) {
        return listOf(MarkupBlock.Markdown(source))
    }
    val fenced = fencedRanges(source)
    val blocks = ArrayList<MarkupBlock>(4)
    var cursor = 0
    BLOCK_MATH.findAll(source).forEach { match ->
        if (fenced.any { match.range.first in it }) return@forEach
        val latex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        if (latex.isBlank()) return@forEach
        if (match.range.first > cursor) {
            blocks += MarkupBlock.Markdown(source.substring(cursor, match.range.first))
        }
        blocks += MarkupBlock.Math(latex)
        cursor = match.range.last + 1
    }
    if (cursor < source.length) blocks += MarkupBlock.Markdown(source.substring(cursor))
    return blocks.filterNot { it is MarkupBlock.Markdown && it.source.isBlank() }
        .ifEmpty { listOf(MarkupBlock.Markdown(source)) }
}

internal fun hasInlineMath(source: String): Boolean = INLINE_MATH.containsMatchIn(source)

internal fun splitInlineRuns(source: String): List<InlineRun> {
    val runs = ArrayList<InlineRun>(4)
    var cursor = 0
    INLINE_MATH.findAll(source).forEach { match ->
        val latex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        if (latex.isBlank()) return@forEach
        if (match.range.first > cursor) {
            runs += InlineRun.Text(source.substring(cursor, match.range.first))
        }
        runs += InlineRun.Math(latex)
        cursor = match.range.last + 1
    }
    if (cursor < source.length) runs += InlineRun.Text(source.substring(cursor))
    return runs
}

internal fun AnnotatedString.Builder.appendInlineMarkdown(source: String, codeColor: Color) {
    var cursor = 0
    INLINE_MARKDOWN.findAll(source).forEach { match ->
        if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
        val bold = match.groupValues[1]
        val italic = match.groupValues[2]
        val code = match.groupValues[3]
        when {
            bold.isNotEmpty() -> withSpan(SpanStyle(fontWeight = FontWeight.SemiBold), bold)
            italic.isNotEmpty() -> withSpan(SpanStyle(fontStyle = FontStyle.Italic), italic)
            code.isNotEmpty() -> withSpan(
                SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor),
                code
            )
            else -> append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < source.length) append(source.substring(cursor))
}

private fun AnnotatedString.Builder.withSpan(style: SpanStyle, text: String) {
    pushStyle(style)
    append(text)
    pop()
}
