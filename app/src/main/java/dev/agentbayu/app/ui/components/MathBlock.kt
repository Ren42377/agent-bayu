package dev.agentbayu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
internal fun MathBlock(latex: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface
    val sizePx = with(density) { BLOCK_MATH_SIZE.toPx() }
    val drawable = remember(latex, sizePx, color) { latexDrawable(latex, sizePx, color) }
    if (drawable == null) {
        Text(
            text = latex,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val width = with(density) { drawable.intrinsicWidth.toDp() }
    val height = with(density) { drawable.intrinsicHeight.toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.size(width, height)) {
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}

@Composable
internal fun InlineMathText(
    source: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface
    val codeColor = MaterialTheme.colorScheme.primary
    val sizePx = with(density) { style.fontSize.toPx() }
    val runs = remember(source) { splitInlineRuns(source) }
    val drawables = remember(runs, sizePx, color) {
        runs.filterIsInstance<InlineRun.Math>()
            .map { it.latex }
            .distinct()
            .associateWith { latex -> latexDrawable(latex, sizePx, color) }
    }

    val text = buildAnnotatedString {
        runs.forEach { run ->
            when (run) {
                is InlineRun.Text -> appendInlineMarkdown(run.value, codeColor)
                is InlineRun.Math -> if (drawables[run.latex] == null) {
                    append(run.latex)
                } else {
                    appendInlineContent(run.latex, run.latex)
                }
            }
        }
    }
    val inline = drawables.mapNotNull { (latex, drawable) ->
        if (drawable == null) return@mapNotNull null
        latex to InlineTextContent(
            placeholder = Placeholder(
                width = with(density) { drawable.intrinsicWidth.toDp().value.sp },
                height = with(density) { drawable.intrinsicHeight.toDp().value.sp },
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                    drawable.draw(canvas.nativeCanvas)
                }
            }
        }
    }.toMap()

    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inline,
        modifier = modifier
    )
}

private fun latexDrawable(latex: String, sizePx: Float, color: Color): JLatexMathDrawable? {
    val cleaned = latex.trim()
    if (cleaned.isEmpty()) return null
    return try {
        JLatexMathDrawable.builder(cleaned)
            .textSize(sizePx)
            .color(color.toArgb())
            .align(JLatexMathDrawable.ALIGN_LEFT)
            .build()
    } catch (error: Exception) {
        null
    }
}

private val BLOCK_MATH_SIZE = 18.dp
