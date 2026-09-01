package dev.agentbayu.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import dev.agentbayu.app.ui.theme.AgentBayuMotion

@Stable
class PageStackProgress {

    internal var reader: () -> Float by mutableStateOf({ 0f })

    fun value(): Float = reader()
}

@Composable
fun <T : Any> PageStackHost(
    pages: List<T>,
    progress: PageStackProgress,
    onDismiss: (T) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val animation = remember { Animatable(0f) }
    val reader: () -> Float = remember(animation) { { animation.value } }
    SideEffect { progress.reader = reader }

    val rendered = remember { mutableStateListOf<T>() }
    pages.forEachIndexed { index, page ->
        if (index < rendered.size) {
            if (rendered[index] != page) {
                rendered[index] = page
            }
        } else {
            rendered.add(page)
        }
    }

    LaunchedEffect(pages.size) {
        animation.animateTo(pages.size.toFloat(), AgentBayuMotion.panelSpring)
        while (rendered.size > pages.size) {
            rendered.removeAt(rendered.lastIndex)
        }
    }

    if (rendered.isEmpty()) {
        return
    }

    val topIndex = pages.lastIndex
    Box(modifier = modifier.fillMaxSize()) {
        rendered.toList().forEachIndexed { index, page ->
            key(index) {
                PageStackEntry(
                    index = index,
                    focused = index == topIndex,
                    progress = reader,
                    onDismiss = { onDismiss(page) }
                ) {
                    content(page)
                }
            }
        }
    }
}

@Composable
private fun PageStackEntry(
    index: Int,
    focused: Boolean,
    progress: () -> Float,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(enabled = focused, onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val depth = pageDepth(progress(), index)
                if (depth <= 0f) {
                    translationX = -depth * size.width
                    alpha = 1f
                } else {
                    translationX = -depth * size.width * COVERED_PARALLAX
                    alpha = 1f - depth
                }
            }
            .drawWithContent {
                val depth = pageDepth(progress(), index)
                if (depth > -PAGE_EDGE && depth < PAGE_EDGE) {
                    drawContent()
                }
            }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        content()
    }
}

private fun pageDepth(progress: Float, index: Int): Float {
    return (progress - (index + 1)).coerceIn(-1f, 1f)
}

private const val COVERED_PARALLAX = 0.25f
private const val PAGE_EDGE = 0.999f
