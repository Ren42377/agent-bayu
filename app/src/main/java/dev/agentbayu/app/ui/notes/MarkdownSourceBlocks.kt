package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Immutable
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import dev.agentbayu.app.ui.components.fencedRanges

@Immutable
internal data class MarkdownSourceBlock(
    val start: Int,
    val end: Int,
    val source: String
)

internal fun markdownSourceBlocks(source: String): List<MarkdownSourceBlock> {
    if (source.isBlank()) return emptyList()
    if (requiresWholeDocument(source)) {
        return listOf(MarkdownSourceBlock(0, source.length, source))
    }
    val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
    val blocks = root.children
        .asSequence()
        .mapNotNull { it.toSourceBlock(source) }
        .toList()
    return blocks.ifEmpty { listOf(MarkdownSourceBlock(0, source.length, source)) }
}

private fun requiresWholeDocument(source: String): Boolean {
    val fenced = fencedRanges(source)
    return FOOTNOTE_MARKER.findAll(source).any { match ->
        fenced.none { match.range.first in it }
    } || REFERENCE_DEFINITION.findAll(source).any { match ->
        fenced.none { match.range.first in it }
    }
}

private fun ASTNode.toSourceBlock(source: String): MarkdownSourceBlock? {
    val start = startOffset.coerceIn(0, source.length)
    val end = endOffset.coerceIn(start, source.length)
    if (start == end) return null
    val text = source.substring(start, end)
    if (text.isBlank()) return null
    return MarkdownSourceBlock(start, end, text)
}

private val FOOTNOTE_MARKER = Regex("""\[\^[^\]]+]""")

private val REFERENCE_DEFINITION = Regex("""(?m)^ {0,3}\[[^\]]+]:""")
