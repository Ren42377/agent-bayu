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

internal fun hasInlineMath(source: String): Boolean =
    BLOCK_MATH.containsMatchIn(source) || INLINE_MATH.containsMatchIn(source)

internal fun splitInlineRuns(source: String): List<InlineRun> {
    val runs = ArrayList<InlineRun>(4)
    var cursor = 0
    BLOCK_MATH.findAll(source).forEach { match ->
        appendInlineMathRuns(source, cursor, match.range.first, runs)
        val latex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        if (latex.isNotBlank()) runs += InlineRun.Math(latex)
        cursor = match.range.last + 1
    }
    appendInlineMathRuns(source, cursor, source.length, runs)
    return runs
}

private fun appendInlineMathRuns(
    source: String,
    from: Int,
    to: Int,
    runs: MutableList<InlineRun>
) {
    var cursor = from
    INLINE_MATH.findAll(source, from).forEach { match ->
        if (match.range.first >= to) return@forEach
        val latex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        if (latex.isBlank()) return@forEach
        if (match.range.first > cursor) {
            runs += InlineRun.Text(source.substring(cursor, match.range.first))
        }
        runs += InlineRun.Math(latex)
        cursor = match.range.last + 1
    }
    if (cursor < to) runs += InlineRun.Text(source.substring(cursor, to))
}

private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

private val HIDDEN_ELEMENT = Regex(
    "<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*\\bhidden\\b[^>]*>.*?</\\1\\s*>",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)

private val HIDDEN_SELF_CLOSING = Regex(
    "<[a-zA-Z][a-zA-Z0-9]*\\b[^>]*\\bhidden\\b[^>]*/>",
    RegexOption.IGNORE_CASE
)

private val BOLD_TAG = Regex("</?(?:b|strong)\\b[^>]*>", RegexOption.IGNORE_CASE)

private val ITALIC_TAG = Regex("</?(?:i|em)\\b[^>]*>", RegexOption.IGNORE_CASE)

private val BREAK_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

private val GENERIC_TAG = Regex("</?[a-zA-Z][a-zA-Z0-9]*\\b[^>]*/?>", RegexOption.IGNORE_CASE)

private val FOOTNOTE_DEF = Regex(" {0,3}\\[\\^([^\\]]+)]:[ \\t]*(.*)")

private val FOOTNOTE_CONTINUATION = Regex("(?: {4}|\\t)(.*)")

private val FOOTNOTE_REF = Regex("\\[\\^([^\\]]+)]")

private val DASH_RULE = Regex(" {0,3}-{3,}[ \\t]*")

internal fun sanitiseMarkdown(source: String): String {
    if (source.isBlank()) return source
    var text = transformOutsideFences(source, ::stripHtml)
    text = transformOutsideFences(text, ::liftFootnoteDefinitions)
    text = transformOutsideFences(text, ::separateThematicBreaks)
    return text
}

private fun transformOutsideFences(source: String, transform: (String) -> String): String {
    if (!source.contains("```") && !source.contains("~~~")) return transform(source)
    val ranges = fencedRanges(source)
    if (ranges.isEmpty()) return transform(source)
    val out = StringBuilder(source.length)
    var cursor = 0
    ranges.forEach { range ->
        if (range.first > cursor) out.append(transform(source.substring(cursor, range.first)))
        out.append(source.substring(range.first, range.last))
        cursor = range.last
    }
    if (cursor < source.length) out.append(transform(source.substring(cursor)))
    return out.toString()
}

private fun stripHtml(source: String): String {
    var text = HTML_COMMENT.replace(source, "")
    text = HIDDEN_ELEMENT.replace(text, "")
    text = HIDDEN_SELF_CLOSING.replace(text, "")
    text = BOLD_TAG.replace(text, "**")
    text = ITALIC_TAG.replace(text, "*")
    text = BREAK_TAG.replace(text, "\n")
    return GENERIC_TAG.replace(text, "")
}

private fun liftFootnoteDefinitions(source: String): String {
    if (!source.contains("[^")) return source
    val lines = source.split("\n")
    val definitions = LinkedHashMap<String, String>()
    val body = ArrayList<String>(lines.size)
    var index = 0
    var removedAny = false
    while (index < lines.size) {
        val definition = FOOTNOTE_DEF.matchEntire(lines[index])
        if (definition == null) {
            body += lines[index]
            index += 1
            continue
        }
        val id = definition.groupValues[1]
        val text = StringBuilder(definition.groupValues[2].trim())
        index += 1
        while (index < lines.size) {
            val continuation = FOOTNOTE_CONTINUATION.matchEntire(lines[index]) ?: break
            if (text.isNotEmpty()) text.append(' ')
            text.append(continuation.groupValues[1].trim())
            index += 1
        }
        definitions[id] = text.toString()
        removedAny = true
    }
    if (!removedAny) return source
    val rewritten = FOOTNOTE_REF.replace(body.joinToString("\n")) { match ->
        "[" + match.groupValues[1] + "]"
    }
    val section = definitions.entries.joinToString("\n") { (id, text) ->
        "[" + id + "] " + text
    }
    return rewritten.trimEnd() + "\n\n---\n\n" + section + "\n"
}

private fun separateThematicBreaks(source: String): String {
    if (!source.contains("---")) return source
    val lines = source.split("\n")
    val out = ArrayList<String>(lines.size + 2)
    lines.forEach { line ->
        val isDashRule = DASH_RULE.matchEntire(line.trimEnd()) != null
        if (isDashRule) {
            if (out.isNotEmpty() && out.last().isNotBlank()) {
                out += ""
            }
            out += line.trim()
        } else {
            out += line
        }
    }
    return out.joinToString("\n")
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
