package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import dev.agentbayu.app.ai.ReasoningEffort
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import kotlin.math.abs

private val COMPACT_TRACK_HEIGHT = 18.dp
private val COMPACT_THUMB_DIAMETER = 20.dp
private val COMPACT_DOT_DIAMETER = 3.5.dp
private val ACTIVE_BLUE = Color(0xFF1D7CFF)

@Composable
fun CompactEffortSlider(
    options: List<ReasoningEffort>,
    selected: ReasoningEffort?,
    onSelect: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.size < 2) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val lastIndex = options.size - 1
    val safeSelectedIndex = selectedIndex.fastCoerceIn(0, lastIndex)
    val darkTheme = LocalDarkTheme.current
    val hapticFeedback = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnSelect by rememberUpdatedState(onSelect)

    val activeTrackColor = ACTIVE_BLUE
    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (darkTheme) 0.12f else 0.08f
    )
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    var currentIndex by remember { mutableIntStateOf(safeSelectedIndex) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(COMPACT_THUMB_DIAMETER)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    safeSelectedIndex / lastIndex.toFloat(),
                    0f..1f,
                    lastIndex
                )
            }
    ) {
        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { COMPACT_THUMB_DIAMETER.toPx() } / 2f
        val thumbSizePx = thumbRadiusPx * 2f
        val trackWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val travelPx = (trackWidthPx - thumbSizePx).coerceAtLeast(1f)
        val trackHeightPx = with(density) { COMPACT_TRACK_HEIGHT.toPx() }
        val trackTopPx = (thumbSizePx - trackHeightPx) / 2f

        fun stopCenterPx(index: Int): Float =
            thumbRadiusPx + (index.toFloat() / lastIndex) * travelPx

        val dragAnimation = remember(animationScope, lastIndex) {
            var travel = 0f
            var downIndex = safeSelectedIndex
            var dragAnchor = 0f
            var dragDistance = 0f
            var lastTickIndex = safeSelectedIndex
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = safeSelectedIndex.toFloat(),
                valueRange = 0f..lastIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.12f,
                onDragStarted = { position ->
                    travel = 0f
                    dragAnchor = value
                    dragDistance = 0f
                    lastTickIndex = value.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    downIndex = ((position.x - thumbRadiusPx) / (travelPx / lastIndex))
                        .fastRoundToInt()
                        .fastCoerceIn(0, lastIndex)
                },
                onDragStopped = {
                    val targetIdx = if (travel < touchSlop) {
                        downIndex
                    } else {
                        targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    }
                    currentIndex = targetIdx
                    animateToValue(targetIdx.toFloat(), pressed = false)
                    options.getOrNull(targetIdx)?.let(currentOnSelect)
                },
                onDrag = { _, dragAmount ->
                    travel += abs(dragAmount.x)
                    dragDistance += dragAmount.x
                    val rawTarget = dragAnchor + dragDistance / (travelPx / lastIndex)
                    val target = rawTarget.fastCoerceIn(0f, lastIndex.toFloat())
                    val tickIndex = target.fastRoundToInt()
                    if (tickIndex != lastTickIndex) {
                        lastTickIndex = tickIndex
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    updateValue(target)
                },
                onDragCanceled = {
                    animateToValue(currentIndex.toFloat(), pressed = false)
                }
            )
        }

        LaunchedEffect(dragAnimation, selectedIndex) {
            val safeIndex = selectedIndex.fastCoerceIn(0, lastIndex)
            currentIndex = safeIndex
            if (!dragAnimation.isGestureActive) {
                dragAnimation.animateToValue(safeIndex.toFloat(), pressed = false)
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val trackRadius = trackHeightPx / 2f
                    val trackPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = trackTopPx,
                                right = size.width,
                                bottom = trackTopPx + trackHeightPx,
                                cornerRadius = CornerRadius(trackRadius)
                            )
                        )
                    }

                    val fillFraction = (dragAnimation.value / lastIndex).fastCoerceIn(0f, 1f)
                    val fillRight = if (fillFraction <= 0f) {
                        0f
                    } else if (fillFraction >= 1f) {
                        size.width
                    } else {
                        thumbRadiusPx + fillFraction * travelPx
                    }

                    clipPath(trackPath) {
                        drawRoundRect(
                            color = trackBgColor,
                            topLeft = Offset(0f, trackTopPx),
                            size = Size(size.width, trackHeightPx),
                            cornerRadius = CornerRadius(trackRadius)
                        )
                        if (fillRight > 0f) {
                            drawRect(
                                color = activeTrackColor,
                                topLeft = Offset(0f, trackTopPx),
                                size = Size(fillRight, trackHeightPx)
                            )
                        }
                    }

                    val dotRadius = with(density) { COMPACT_DOT_DIAMETER.toPx() } / 2f
                    for (index in 0..lastIndex) {
                        val center = stopCenterPx(index)
                        drawCircle(
                            color = if (center <= fillRight) {
                                Color.White.copy(alpha = 0.55f)
                            } else {
                                dotColor
                            },
                            radius = dotRadius,
                            center = Offset(center, size.height / 2f)
                        )
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(COMPACT_THUMB_DIAMETER)
                .graphicsLayer {
                    val progress = (dragAnimation.value / lastIndex).fastCoerceIn(0f, 1f)
                    translationX = progress * travelPx
                    val scale = dragAnimation.scaleX
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(elevation = 3.dp, shape = CircleShape)
                .drawBehind {
                    drawCircle(color = Color.White)
                }
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dragAnimation.modifier)
        )
    }
}
