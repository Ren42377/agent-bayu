package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.agentbayu.app.ui.components.DampedDragAnimation
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import kotlin.math.abs

val CONTEXT_WINDOW_STOPS = listOf(131_072, 262_144, 524_288, 1_048_576)

fun contextWindowStopOf(override: Int?): Int =
    override?.let { value -> CONTEXT_WINDOW_STOPS.indexOfFirst { it == value } } ?: -1

fun contextWindowLabel(tokens: Int): String = when {
    tokens >= 1_048_576 && tokens % 1_048_576 == 0 -> (tokens / 1_048_576).toString() + "M"
    tokens >= 1_000_000 -> compactContext(tokens / 1_000_000.0) + "M"
    tokens >= 1_024 && tokens % 1_024 == 0 -> (tokens / 1_024).toString() + "K"
    tokens >= 1_000 -> compactContext(tokens / 1_000.0) + "K"
    else -> tokens.toString()
}

private fun compactContext(value: Double): String {
    val factor = if (value >= 100.0) 1.0 else 10.0
    val rounded = (value * factor).fastRoundToInt() / factor
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
internal fun ContextWindowSlider(
    stopCount: Int,
    selectedIndex: Int,
    labelOf: (Int) -> String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (stopCount < 2) return
    val lastIndex = stopCount - 1
    val safeSelectedIndex = selectedIndex.fastCoerceIn(0, lastIndex)
    val darkTheme = LocalDarkTheme.current
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (darkTheme) SLIDER_TRACK_ALPHA_DARK else SLIDER_TRACK_ALPHA_LIGHT
    )
    val fillColor = MaterialTheme.colorScheme.onSurface
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SLIDER_DOT_REST_ALPHA)
    val dotOnFillColor = MaterialTheme.colorScheme.surface.copy(alpha = SLIDER_DOT_ON_FILL_ALPHA)
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var currentIndex by remember { mutableIntStateOf(safeSelectedIndex) }
    var hasSelection by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        currentIndex = selectedIndex.fastCoerceIn(0, lastIndex)
        hasSelection = selectedIndex
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(SLIDER_TRACK_HEIGHT)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        safeSelectedIndex / lastIndex.toFloat(),
                        0f..1f,
                        lastIndex
                    )
                }
        ) {
            val density = LocalDensity.current
            val thumbRadiusPx = with(density) { SLIDER_THUMB_DIAMETER.toPx() } / 2f
            val travelPx = (constraints.maxWidth - 2 * thumbRadiusPx).coerceAtLeast(1f)
            fun stopCenterPx(index: Int): Float = thumbRadiusPx + index * travelPx / lastIndex

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
                    pressedScale = SLIDER_THUMB_PRESSED_SCALE,
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
                        val selected = if (travel < touchSlop) {
                            downIndex
                        } else {
                            targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                        }
                        currentIndex = selected
                        hasSelection = selected
                        animateToValue(selected.toFloat(), pressed = false)
                        currentOnSelect(selected)
                    },
                    onDrag = { _, dragAmount ->
                        travel += abs(dragAmount.x)
                        dragDistance += dragAmount.x
                        val target = (dragAnchor + dragDistance / (travelPx / lastIndex))
                            .fastCoerceIn(0f, lastIndex.toFloat())
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
            LaunchedEffect(dragAnimation) {
                withFrameNanos { }
                dragAnimation.prewarm()
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val trackRadius = size.height / 2f
                        val fillFraction = (dragAnimation.value / lastIndex).fastCoerceIn(0f, 1f)
                        val fillRight = thumbRadiusPx + fillFraction * travelPx
                        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(trackRadius))
                        val fill = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = 0f,
                                    top = 0f,
                                    right = fillRight,
                                    bottom = size.height,
                                    topLeftCornerRadius = CornerRadius(trackRadius),
                                    bottomLeftCornerRadius = CornerRadius(trackRadius)
                                )
                            )
                        }
                        drawPath(fill, fillColor)
                        val dotRadius = SLIDER_DOT_DIAMETER.toPx() / 2f
                        for (index in 0..lastIndex) {
                            val center = stopCenterPx(index)
                            drawCircle(
                                color = if (center <= fillRight) {
                                    dotOnFillColor
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
                    .size(SLIDER_THUMB_DIAMETER)
                    .graphicsLayer {
                        translationX = (dragAnimation.value / lastIndex) * travelPx
                    }
                    .drawBackdrop(
                        backdrop = LocalGlassBackdrop.current,
                        shape = { CircleShape },
                        effects = { },
                        highlight = {
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = dragAnimation.pressProgress
                            )
                        },
                        shadow = {
                            Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))
                        },
                        innerShadow = {
                            val progress = dragAnimation.pressProgress
                            InnerShadow(radius = 4.dp * progress, alpha = progress)
                        },
                        layerBlock = {
                            scaleX = dragAnimation.scaleX
                            scaleY = dragAnimation.scaleY
                            val velocity = dragAnimation.velocity / 50f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = { drawRect(Color.White) }
                    )
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(dragAnimation.modifier)
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SLIDER_LABEL_HEIGHT)
        ) {
            val labelThumbRadiusPx = with(LocalDensity.current) {
                SLIDER_THUMB_DIAMETER.toPx()
            } / 2f
            val labelTravelPx = (constraints.maxWidth - 2 * labelThumbRadiusPx).coerceAtLeast(1f)
            (0..lastIndex).forEach { index ->
                var labelWidth by remember { mutableFloatStateOf(0f) }
                Text(
                    text = labelOf(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == hasSelection) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { size -> labelWidth = size.width.toFloat() }
                        .graphicsLayer {
                            translationX = (
                                labelThumbRadiusPx +
                                    index * labelTravelPx / lastIndex -
                                    labelWidth / 2f
                                ).coerceIn(0f, (constraints.maxWidth - labelWidth).coerceAtLeast(0f))
                        }
                )
            }
        }
    }
}

private const val SLIDER_TRACK_ALPHA_DARK = 0.10f
private const val SLIDER_TRACK_ALPHA_LIGHT = 0.07f
private const val SLIDER_DOT_REST_ALPHA = 0.30f
private const val SLIDER_DOT_ON_FILL_ALPHA = 0.45f
private const val SLIDER_THUMB_PRESSED_SCALE = 1.15f
private val SLIDER_TRACK_HEIGHT = 26.dp
private val SLIDER_THUMB_DIAMETER = 32.dp
private val SLIDER_DOT_DIAMETER = 4.dp
private val SLIDER_LABEL_HEIGHT = 18.dp
