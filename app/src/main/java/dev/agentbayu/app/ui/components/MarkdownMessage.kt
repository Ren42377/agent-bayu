package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

@Composable
fun MarkdownMessage(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { splitMarkup(content) }
    if (blocks.size == 1 && blocks.first() is MarkupBlock.Markdown) {
        MarkdownBody(source = content, modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkupBlock.Markdown -> MarkdownBody(source = block.source)
                is MarkupBlock.Math -> MathBlock(latex = block.latex)
            }
        }
    }
}

@Composable
private fun MarkdownBody(
    source: String,
    modifier: Modifier = Modifier,
    nested: Boolean = false
) {
    val darkTheme = LocalDarkTheme.current
    val markdownState = rememberMarkdownState(source, retainState = true)
    val highlightsBuilder = remember(darkTheme) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = darkTheme))
    }
    val plainCode = stringResource(R.string.code_plain)
    val markdownLabel = stringResource(R.string.code_markdown)
    Markdown(
        markdownState = markdownState,
        modifier = modifier.fillMaxWidth(),
        components = markdownComponents(
            paragraph = { model ->
                val text = model.source()
                if (hasInlineMath(text)) {
                    InlineMathText(
                        source = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    MarkdownParagraph(content = model.content, node = model.node)
                }
            },
            codeBlock = { model ->
                val fence = fenceOf(model.source())
                CodeCard(label = plainCode, icon = null, code = fence.body) {
                    MarkdownHighlightedCodeBlock(
                        content = model.content,
                        node = model.node,
                        highlightsBuilder = highlightsBuilder,
                        showHeader = false
                    )
                }
            },
            codeFence = { model ->
                val fence = fenceOf(model.source())
                if (!nested && isMarkdownLanguage(fence.language)) {
                    CodeCard(
                        label = markdownTitleOf(fence.body) ?: markdownLabel,
                        icon = R.drawable.ic_note,
                        code = fence.body
                    ) {
                        MarkdownBody(
                            source = fence.body,
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                bottom = 12.dp
                            ),
                            nested = true
                        )
                    }
                } else {
                    CodeCard(
                        label = fence.language ?: plainCode,
                        icon = null,
                        code = fence.body
                    ) {
                        MarkdownHighlightedCodeFence(
                            content = model.content,
                            node = model.node,
                            highlightsBuilder = highlightsBuilder,
                            showHeader = false
                        )
                    }
                }
            }
        )
    )
}

private fun MarkdownComponentModel.source(): String {
    val start = node.startOffset.coerceIn(0, content.length)
    val end = node.endOffset.coerceIn(start, content.length)
    return content.substring(start, end)
}
