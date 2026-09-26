package dev.agentbayu.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import dev.agentbayu.app.R
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import java.io.File

internal object NetworkImageTransformer : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData {
        val placeholder = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        val glyph = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageModelFor(link))
                .size(CoilSize.ORIGINAL)
                .crossfade(true)
                .build()
        )
        return ImageData(StatefulImagePainter(painter, placeholder, glyph))
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
        if (painter is StatefulImagePainter) {
            val state = painter.source.state.collectAsState()
            val updated = state.value.painter?.intrinsicSize
            if (updated != null && !updated.isUnspecified) size = updated
        }
        return size
    }
}

internal fun imageModelFor(destination: String): Any {
    val trimmed = destination.trim()
    return if (trimmed.startsWith("/") && !trimmed.startsWith("//")) File(trimmed) else trimmed
}

private class StatefulImagePainter(
    internal val source: AsyncImagePainter,
    private val placeholder: Color,
    private val glyph: Color
) : Painter() {

    override val intrinsicSize: Size
        get() = source.intrinsicSize

    override fun DrawScope.onDraw() {
        when (val state = source.state.value) {
            is AsyncImagePainter.State.Success -> with(state.painter) { draw(size) }
            else -> drawUnavailable()
        }
    }

    private fun DrawScope.drawUnavailable() {
        val side = 96.dp.toPx()
        val width = if (size.width > 0f) size.width else side
        val height = if (size.height > 0f) size.height else side
        drawRoundRect(
            color = placeholder,
            cornerRadius = CornerRadius(12.dp.toPx())
        )
        val boxWidth = minOf(width, side) * 0.5f
        val boxHeight = minOf(height, side) * 0.5f
        val left = (width - boxWidth) / 2f
        val top = (height - boxHeight) / 2f
        drawRoundRect(
            color = glyph,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )
        drawLine(
            color = glyph,
            start = Offset(left, top),
            end = Offset(left + boxWidth, top + boxHeight),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
internal fun MarkdownImageContent(model: MarkdownComponentModel) {
    val destination = model.node.imageDestination(model.content) ?: return
    val alt = model.node.imageAltText(model.content)
    val failedLabel = stringResource(R.string.markdown_image_failed)
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(imageModelFor(destination))
            .size(CoilSize.ORIGINAL)
            .crossfade(true)
            .build(),
        contentDescription = alt,
        modifier = Modifier.fillMaxWidth(),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        },
        error = {
            MarkdownImageError(failedLabel, alt)
        },
        success = { state ->
            Image(
                painter = state.painter,
                contentDescription = alt,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    )
}

@Composable
private fun MarkdownImageError(label: String, alt: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_image),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alt != null) {
                Text(
                    text = alt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun ASTNode.imageDestination(content: String): String? =
    findRecursive(MarkdownElementTypes.LINK_DESTINATION)?.textIn(content)

private fun ASTNode.imageAltText(content: String): String? =
    findRecursive(MarkdownElementTypes.LINK_TEXT)?.textIn(content)?.trim('[', ']')
        ?: findRecursive(MarkdownElementTypes.LINK_LABEL)?.textIn(content)?.trim('[', ']')

private fun ASTNode.findRecursive(type: org.intellij.markdown.IElementType): ASTNode? {
    if (this.type == type) return this
    children.forEach { child ->
        child.findRecursive(type)?.let { return it }
    }
    return null
}

private fun ASTNode.textIn(content: String): String? =
    content.substring(startOffset, endOffset).trim().takeIf { it.isNotBlank() }
