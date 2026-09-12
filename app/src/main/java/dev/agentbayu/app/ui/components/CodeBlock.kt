package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.glassSurface
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CodeCard(
    label: String,
    icon: Int?,
    code: String,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                size = 28.dp
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.code_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        body()
    }
}

@Composable
internal fun CodeBody(
    code: String,
    language: String?,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val spans by rememberCodeSpans(code, language, darkTheme)
    Text(
        text = spans,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        softWrap = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
    )
}

@Composable
private fun rememberCodeSpans(
    code: String,
    language: String?,
    darkTheme: Boolean
): State<AnnotatedString> = produceState(
    initialValue = AnnotatedString(code),
    key1 = code,
    key2 = language,
    key3 = darkTheme
) {
    value = withContext(Dispatchers.Default) { codeSpans(code, language, darkTheme) }
}

private fun codeSpans(code: String, language: String?, darkTheme: Boolean): AnnotatedString {
    val resolved = language?.let { SyntaxLanguage.getByName(it) }
    val highlights = try {
        Highlights.Builder()
            .theme(SyntaxThemes.atom(darkMode = darkTheme))
            .code(code)
            .let { if (resolved != null) it.language(resolved) else it }
            .build()
            .getHighlights()
    } catch (error: Exception) {
        return AnnotatedString(code)
    }
    return buildAnnotatedString {
        append(code)
        highlights.forEach { highlight ->
            val style = when (highlight) {
                is ColorHighlight -> SpanStyle(color = Color(highlight.rgb).copy(alpha = 1f))
                is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
                else -> return@forEach
            }
            addStyle(
                style = style,
                start = highlight.location.start.coerceIn(0, code.length),
                end = highlight.location.end.coerceIn(0, code.length)
            )
        }
    }
}

internal data class CodeFence(val language: String?, val body: String)

private val FENCE_LINE = Regex("^ {0,3}(`{3,}|~{3,})([^`]*)$")

internal fun fenceOf(source: String): CodeFence {
    val lines = source.trim().lines()
    if (lines.isEmpty()) return CodeFence(null, source)
    val opening = FENCE_LINE.matchEntire(lines.first())
        ?: return CodeFence(null, source.trimEnd())
    val marker = opening.groupValues[1]
    val language = opening.groupValues[2]
        .trim()
        .substringBefore(' ')
        .takeIf { it.isNotEmpty() }
    val closing = lines.lastOrNull()?.trim().orEmpty()
    val closes = lines.size > 1 &&
        closing.length >= marker.length &&
        closing.isNotEmpty() &&
        closing.all { it == marker[0] }
    val end = if (closes) lines.lastIndex else lines.size
    return CodeFence(language, lines.subList(1, end).joinToString("\n"))
}

internal fun normaliseMarkdownFences(source: String): String {
    if (!source.contains("```") && !source.contains("~~~")) return source
    val lines = source.split("\n")
    val out = ArrayList<String>(lines.size + 2)
    var index = 0
    while (index < lines.size) {
        val opening = FENCE_LINE.matchEntire(lines[index])
        val info = opening?.groupValues?.get(2)?.trim().orEmpty()
        if (opening == null || !isMarkdownLanguage(info.substringBefore(' '))) {
            out += lines[index]
            index += 1
            continue
        }
        val marker = opening.groupValues[1]
        var longest = marker.length
        var depth = 0
        var cursor = index + 1
        val body = ArrayList<String>()
        while (cursor < lines.size) {
            val inner = FENCE_LINE.matchEntire(lines[cursor])
            if (inner != null && inner.groupValues[1][0] == marker[0]) {
                longest = maxOf(longest, inner.groupValues[1].length)
                val bare = inner.groupValues[2].isBlank()
                if (bare && inner.groupValues[1].length >= marker.length) {
                    if (depth == 0) break
                    depth -= 1
                } else if (!bare) {
                    depth += 1
                }
            }
            body += lines[cursor]
            cursor += 1
        }
        val outer = marker[0].toString().repeat(longest + 1)
        out += outer + info
        out += body
        out += outer
        index = if (cursor < lines.size) cursor + 1 else cursor
    }
    return out.joinToString("\n")
}

internal fun isMarkdownLanguage(language: String?): Boolean {
    val name = language?.lowercase() ?: return false
    return name == "md" || name == "markdown"
}

internal fun fencedRanges(source: String): List<IntRange> {
    if (!source.contains("```") && !source.contains("~~~")) return emptyList()
    val ranges = ArrayList<IntRange>(2)
    var offset = 0
    var openAt = -1
    var marker = ""
    source.split("\n").forEach { line ->
        val fence = FENCE_LINE.matchEntire(line)
        if (fence != null) {
            val run = fence.groupValues[1]
            if (openAt < 0) {
                openAt = offset
                marker = run
            } else if (
                run[0] == marker[0] &&
                run.length >= marker.length &&
                fence.groupValues[2].isBlank()
            ) {
                ranges += openAt..(offset + line.length)
                openAt = -1
            }
        }
        offset += line.length + 1
    }
    if (openAt >= 0) ranges += openAt..source.length
    return ranges
}

internal fun isDiagramLanguage(language: String?): Boolean {
    val name = language?.lowercase() ?: return false
    return name in DIAGRAM_LANGUAGES
}

internal fun markdownTitleOf(body: String): String? = body.lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("#") }
    ?.trimStart('#')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

private val DIAGRAM_LANGUAGES = setOf(
    "mermaid",
    "dot",
    "graphviz",
    "plantuml",
    "puml",
    "flowchart",
    "sequencediagram"
)
