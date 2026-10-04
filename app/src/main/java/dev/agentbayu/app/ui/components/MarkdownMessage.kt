package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun MarkdownMessage(
    content: String,
    modifier: Modifier = Modifier,
    autoEmbedImages: Boolean = false
) {
    val source = remember(content, autoEmbedImages) {
        val prepared = sanitiseMarkdown(normaliseMarkdownFences(content))
        if (autoEmbedImages) embedImageReferences(prepared) else prepared
    }
    val blocks = remember(source) { splitMarkup(source) }
    if (blocks.size == 1 && blocks.first() is MarkupBlock.Markdown) {
        MarkdownBody(source = source, modifier = modifier)
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
private fun headingStyle(size: TextUnit, lineHeight: TextUnit): TextStyle =
    MaterialTheme.typography.titleLarge.copy(
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = FontWeight.SemiBold
    )

private val HEADING_1_SIZE = 22.sp
private val HEADING_1_LINE = 30.sp
private val HEADING_2_SIZE = 20.sp
private val HEADING_2_LINE = 28.sp
private val HEADING_3_SIZE = 18.sp
private val HEADING_3_LINE = 26.sp
private val HEADING_4_SIZE = 17.sp
private val HEADING_4_LINE = 24.sp
private val HEADING_5_SIZE = 16.sp
private val HEADING_5_LINE = 24.sp
private val HEADING_6_SIZE = 15.sp
private val HEADING_6_LINE = 22.sp

@Composable
private fun MarkdownBody(
    source: String,
    modifier: Modifier = Modifier,
    nested: Boolean = false
) {
    val darkTheme = LocalDarkTheme.current
    val markdownState = rememberMarkdownState(source, retainState = true)
    val plainCode = stringResource(R.string.code_plain)
    val markdownLabel = stringResource(R.string.code_markdown)
    Markdown(
        markdownState = markdownState,
        typography = markdownTypography(
            h1 = headingStyle(HEADING_1_SIZE, HEADING_1_LINE),
            h2 = headingStyle(HEADING_2_SIZE, HEADING_2_LINE),
            h3 = headingStyle(HEADING_3_SIZE, HEADING_3_LINE),
            h4 = headingStyle(HEADING_4_SIZE, HEADING_4_LINE),
            h5 = headingStyle(HEADING_5_SIZE, HEADING_5_LINE),
            h6 = headingStyle(HEADING_6_SIZE, HEADING_6_LINE)
        ),
        colors = markdownColor(
            codeBackground = Color.Transparent,
            inlineCodeBackground = Color.Transparent,
            tableBackground = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth(),
        dimens = markdownDimens(tableCellWidth = 110.dp, tableCellPadding = 10.dp),
        imageTransformer = NetworkImageTransformer,
        components = markdownComponents(
                image = { model -> MarkdownImageContent(model) },
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
                        CodeBody(
                            code = fence.body,
                            language = null,
                            darkTheme = darkTheme
                        )
                    }
                },
                codeFence = { model ->
                    val fence = fenceOf(model.source())
                    when {
                        !nested && isMarkdownLanguage(fence.language) -> CodeCard(
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

                        isDiagramLanguage(fence.language) -> CodeCard(
                            label = fence.language ?: plainCode,
                            icon = R.drawable.ic_diagram,
                            code = fence.body
                        ) {
                            CodeBody(
                                code = fence.body,
                                language = null,
                                darkTheme = darkTheme
                            )
                        }

                        else -> CodeCard(
                            label = fence.language ?: plainCode,
                            icon = null,
                            code = fence.body
                        ) {
                            CodeBody(
                                code = fence.body,
                                language = fence.language,
                                darkTheme = darkTheme
                            )
                        }
                    }
                },
                table = { model ->
                    MarkdownTable(
                        content = model.content,
                        node = model.node,
                        style = model.typography.text,
                        headerBlock = { cells, header, tableWidth, style ->
                            MarkdownTableHeader(
                                content = cells,
                                header = header,
                                tableWidth = tableWidth,
                                style = style,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip
                            )
                        },
                        rowBlock = { cells, row, tableWidth, style ->
                            MarkdownTableRow(
                                content = cells,
                                header = row,
                                tableWidth = tableWidth,
                                style = style,
                                maxLines = Int.MAX_VALUE,
                                overflow = TextOverflow.Clip
                            )
                        }
                    )
                }
        )
    )
}

private fun MarkdownComponentModel.source(): String {
    val start = node.startOffset.coerceIn(0, content.length)
    val end = node.endOffset.coerceIn(start, content.length)
    return content.substring(start, end)
}
