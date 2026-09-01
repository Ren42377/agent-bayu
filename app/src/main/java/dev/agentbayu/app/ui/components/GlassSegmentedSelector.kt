package dev.agentbayu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.LocalGlassStyle
import dev.agentbayu.app.ui.theme.liquidGlass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

@Composable
internal fun GlassSegmentedSelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    tintProvider: ((Float) -> Color)? = null,
    decoration: (DrawScope.(Float) -> Unit)? = null
) {
    if (labels.isEmpty()) {
        return
    }
    val lastIndex = labels.lastIndex
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val indicatorStyle = LocalGlassStyle.current.copy(
        elevation = SELECTOR_ELEVATION,
        highlightAlpha = SELECTOR_HIGHLIGHT_ALPHA
    )
    val animationScope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SELECTOR_HEIGHT)
    ) {
        val segmentWidth = maxWidth / labels.size
        val segmentWidthPx = constraints.maxWidth.toFloat() / labels.size
        val dragAnimation = remember(animationScope, segmentWidthPx, lastIndex) {
            var travel = 0f
            var downIndex = selectedIndex
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..lastIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = SELECTOR_PRESSED_SCALE,
                onDragStarted = { position ->
                    travel = 0f
                    downIndex = (position.x / segmentWidthPx).toInt().fastCoerceIn(0, lastIndex)
                },
                onDragStopped = {
                    currentIndex = if (travel < touchSlop) {
                        downIndex
                    } else {
                        targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    }
                    animateToValue(currentIndex.toFloat())
                },
                onDrag = { _, dragAmount ->
                    travel += abs(dragAmount.x)
                    updateValue(
                        (targetValue + dragAmount.x / segmentWidthPx)
                            .fastCoerceIn(0f, lastIndex.toFloat())
                    )
                }
            )
        }
        LaunchedEffect(selectedIndex) {
            currentIndex = selectedIndex
        }
        LaunchedEffect(dragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dragAnimation.animateToValue(index.toFloat())
                    currentOnSelect(index)
                }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = trackColor, shape = CapsuleShape)
        )
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = dragAnimation.value * segmentWidthPx }
                .liquidGlass(
                    shape = CapsuleShape,
                    style = indicatorStyle,
                    tint = tint,
                    tintAlpha = SELECTOR_TINT_ALPHA,
                    tintProvider = tintProvider?.let { provider ->
                        { provider(dragAnimation.value) }
                    },
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / SELECTOR_VELOCITY_SCALE
                        scaleX /= 1f - (velocity * 0.75f)
                            .fastCoerceIn(-SELECTOR_SQUISH, SELECTOR_SQUISH)
                        scaleY *= 1f - (velocity * 0.25f)
                            .fastCoerceIn(-SELECTOR_SQUISH, SELECTOR_SQUISH)
                    }
                )
                .then(
                    if (decoration != null) {
                        Modifier
                            .clip(CapsuleShape)
                            .drawBehind { decoration(dragAnimation.value) }
                    } else {
                        Modifier
                    }
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
        ) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            selected = index == currentIndex
                            onClick {
                                currentIndex = index
                                true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - abs(index - dragAnimation.value))
                                .fastCoerceIn(0f, 1f)
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dragAnimation.modifier)
        )
    }
}

private const val TRACK_ALPHA = 0.06f
private const val SELECTOR_HIGHLIGHT_ALPHA = 0.9f
private const val SELECTOR_TINT_ALPHA = 0.88f
private const val SELECTOR_PRESSED_SCALE = 1.1f
private const val SELECTOR_VELOCITY_SCALE = 10f
private const val SELECTOR_SQUISH = 0.2f
private val SELECTOR_HEIGHT = 36.dp
private val SELECTOR_ELEVATION = 3.dp
