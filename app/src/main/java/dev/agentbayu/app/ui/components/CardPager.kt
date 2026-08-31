package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import kotlin.math.abs

@Composable
fun CardPager(
    pageCount: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    pageContent: @Composable (page: Int) -> Unit
) {
    Box(modifier = modifier) {
        repeat(pageCount) { page ->
            key(page) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val distance = page - progress()
                            val depth = abs(distance).fastCoerceIn(0f, 1f)
                            val scale = lerp(1f, PAGE_MIN_SCALE, depth)
                            translationX = distance * size.width * PAGE_TRAVEL_RATIO
                            translationY = depth * PAGE_LIFT.toPx()
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - depth
                        }
                ) {
                    pageContent(page)
                }
            }
        }
    }
}

private const val PAGE_MIN_SCALE = 0.9f
private const val PAGE_TRAVEL_RATIO = 0.92f
private val PAGE_LIFT = 12.dp
