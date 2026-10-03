package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import dev.agentbayu.app.ui.theme.AgentBayuTheme
import dev.agentbayu.app.ui.theme.LocalThemeDarkFraction
import dev.agentbayu.app.ui.theme.LocalThemeTargetDarkness
import kotlin.math.abs

@Composable
fun CardPager(
    pageCount: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    pageContent: @Composable (page: Int) -> Unit
) {
    var prewarming by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        prewarming = false
    }
    val liveFraction = LocalThemeDarkFraction.current
    val targetFraction = LocalThemeTargetDarkness.current
    val currentProgress by rememberUpdatedState(progress)
    Box(modifier = modifier) {
        repeat(pageCount) { page ->
            key(page) {
                val isNear by remember(page) {
                    derivedStateOf { abs(page - currentProgress()) < PAGE_VISIBLE_DISTANCE }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val distance = page - progress()
                            translationX = distance * size.width
                            alpha = if (prewarming && distance != 0f) PREWARM_ALPHA else 1f
                        }
                        .drawWithContent {
                            if (prewarming || abs(page - progress()) < PAGE_VISIBLE_DISTANCE) {
                                drawContent()
                            }
                        }
                ) {
                    AgentBayuTheme(darkFraction = if (isNear) liveFraction else targetFraction) {
                        pageContent(page)
                    }
                }
            }
        }
    }
}

private const val PAGE_VISIBLE_DISTANCE = 0.999f
private const val PREWARM_ALPHA = 0.01f
