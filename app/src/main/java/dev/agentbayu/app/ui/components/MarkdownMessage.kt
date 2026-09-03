package dev.agentbayu.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

@Composable
fun MarkdownMessage(
    content: String,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalDarkTheme.current
    val markdownState = rememberMarkdownState(content, retainState = true)
    val highlightsBuilder = remember(darkTheme) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = darkTheme))
    }
    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        components = markdownComponents(
            codeBlock = { model ->
                MarkdownHighlightedCodeBlock(
                    content = model.content,
                    node = model.node,
                    highlightsBuilder = highlightsBuilder,
                    showHeader = true
                )
            },
            codeFence = { model ->
                MarkdownHighlightedCodeFence(
                    content = model.content,
                    node = model.node,
                    highlightsBuilder = highlightsBuilder,
                    showHeader = true
                )
            }
        )
    )
}
