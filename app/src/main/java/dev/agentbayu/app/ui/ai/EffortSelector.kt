package dev.agentbayu.app.ui.ai

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.agentbayu.app.ai.ReasoningEffort
import dev.agentbayu.app.ui.components.DampedDragAnimation
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleMagentaLight
import dev.agentbayu.app.ui.theme.AppleOrangeLight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.AppleYellowLight
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun EffortSelector(
    options: List<ReasoningEffort>,
    selected: ReasoningEffort?,
    onSelect: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.size < MIN_EFFORT_OPTIONS) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val colors = remember(options) { options.map { effortColor(it) } }
    val stars = remember { starField() }
    val phase = remember { mutableFloatStateOf(0f) }
    val drift = remember { mutableFloatStateOf(0f) }
    var previewValue by remember(options) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val pace by rememberUpdatedState(paceAt(options, previewValue))

    LaunchedEffect(options, selectedIndex) {
        previewValue = selectedIndex.toFloat()
    }

    LaunchedEffect(Unit) {
        var lastFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val deltaSeconds = ((frame - lastFrame) / NANOS_PER_SECOND).fastCoerceIn(0f, 0.1f)
            lastFrame = frame
            val level = pace
            drift.floatValue += level.driftSpeed * deltaSeconds
            phase.floatValue = (phase.floatValue + level.twinkleSpeed * deltaSeconds) % TWO_PI
        }
    }

    EffortSlider(
        stopCount = options.size,
        selectedIndex = selectedIndex,
        onSelect = { index -> options.getOrNull(index)?.let(onSelect) },
        modifier = modifier,
        tint = colors[selectedIndex],
        tintProvider = { value -> gradientColor(colors, value) },
        onValueChange = { value -> previewValue = value },
        decoration = { value, _ ->
            drawStars(stars, phase.floatValue, value, drift.floatValue)
        }
    )
}

@Composable
private fun EffortSlider(
    stopCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color,
    tintProvider: ((Float) -> Color)?,
    onValueChange: ((Float) -> Unit)?,
    decoration: (DrawScope.(Float, Float) -> Unit)? = null
) {
    if (stopCount < 2) return
    val lastIndex = stopCount - 1
    val safeSelectedIndex = selectedIndex.fastCoerceIn(0, lastIndex)
    val darkTheme = LocalDarkTheme.current
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (darkTheme) SLIDER_TRACK_ALPHA_DARK else SLIDER_TRACK_ALPHA_LIGHT
    )
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SLIDER_DOT_REST_ALPHA)
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var currentIndex by remember { mutableIntStateOf(safeSelectedIndex) }
    var shaking by remember { mutableStateOf(false) }
    var shakeOffset by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier = modifier
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

        val trackLayerBackdrop = rememberLayerBackdrop()
        val thumbBackdrop = rememberCombinedBackdrop(
            LocalGlassBackdrop.current,
            rememberBackdrop(trackLayerBackdrop) { drawBackdrop -> drawBackdrop() }
        )

        val dragAnimation = remember(animationScope, lastIndex) {
            var travel = 0f
            var downIndex = safeSelectedIndex
            var dragAnchor = 0f
            var dragDistance = 0f
            var lastTickIndex = safeSelectedIndex
            var maxAnnounced = false
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
                    shaking = false
                    animateToValue(selected.toFloat(), pressed = false)
                    currentOnSelect(selected)
                },
                onDrag = { _, dragAmount ->
                    travel += abs(dragAmount.x)
                    dragDistance += dragAmount.x
                    val rawTarget = dragAnchor + dragDistance / (travelPx / lastIndex)
                    val target = rawTarget.fastCoerceIn(0f, lastIndex.toFloat())
                    val atMax = rawTarget > lastIndex
                    shaking = atMax
                    if (atMax && !maxAnnounced) {
                        maxAnnounced = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (!atMax) maxAnnounced = false
                    val tickIndex = target.fastRoundToInt()
                    if (tickIndex != lastTickIndex) {
                        lastTickIndex = tickIndex
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    updateValue(target)
                },
                onDragCanceled = {
                    shaking = false
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
            snapshotFlow { dragAnimation.value }.collect { value ->
                currentOnValueChange?.invoke(value)
            }
        }
        LaunchedEffect(dragAnimation) {
            withFrameNanos { }
            dragAnimation.prewarm()
        }
        val shakeAmplitudePx = with(density) { SLIDER_SHAKE_AMPLITUDE.toPx() }
        LaunchedEffect(shaking) {
            if (!shaking) {
                shakeOffset = 0f
                return@LaunchedEffect
            }
            var start = 0L
            while (true) {
                withFrameNanos { frame ->
                    if (start == 0L) start = frame
                    val elapsedMs = (frame - start) / 1_000_000f
                    shakeOffset = shakeAmplitudePx * sin(elapsedMs * SLIDER_SHAKE_RATE).toFloat()
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(trackLayerBackdrop)
                .graphicsLayer { translationX = shakeOffset }
                .drawBehind {
                    val trackRadius = size.height / 2f
                    val fillFraction = (dragAnimation.value / lastIndex).fastCoerceIn(0f, 1f)
                    val fillRight = thumbRadiusPx + fillFraction * travelPx
                    val maxBlend = ((dragAnimation.value - (lastIndex - 1)).fastCoerceIn(0f, 1f))
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(trackRadius)
                    )
                    if (fillRight > 0f) {
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
                        val fillColor = tintProvider?.invoke(dragAnimation.value) ?: tint
                        drawPath(fill, fillColor)
                        if (maxBlend > 0f) {
                            clipPath(fill) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            SLIDER_GALAXY_START,
                                            SLIDER_GALAXY_MID,
                                            AppleMagentaLight
                                        ),
                                        startX = 0f,
                                        endX = fillRight
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(fillRight, size.height),
                                    alpha = maxBlend
                                )
                            }
                        }
                        decoration?.let {
                            clipPath(fill) {
                                it(dragAnimation.value, dragAnimation.velocity)
                            }
                        }
                    }
                    val dotRadius = SLIDER_DOT_DIAMETER.toPx() / 2f
                    for (index in 0..lastIndex) {
                        val center = stopCenterPx(index)
                        drawCircle(
                            color = if (center <= fillRight) {
                                Color.White.copy(alpha = SLIDER_DOT_ON_FILL_ALPHA)
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
                    translationX = (dragAnimation.value / lastIndex) * travelPx + shakeOffset
                }
                .drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { CircleShape },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        lens(
                            SLIDER_THUMB_LENS_HEIGHT.toPx() * progress,
                            SLIDER_THUMB_LENS_AMOUNT.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
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
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - dragAnimation.pressProgress))
                    }
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dragAnimation.modifier)
        )
    }
}

private data class StarPace(val driftSpeed: Float, val twinkleSpeed: Float)

private fun paceAt(options: List<ReasoningEffort>, value: Float): StarPace {
    val last = options.lastIndex
    if (last < 0) return paceOf(null)
    val clamped = value.fastCoerceIn(0f, last.toFloat())
    val low = floor(clamped).toInt()
    val high = ceil(clamped).toInt()
    val lowPace = paceOf(options[low])
    if (low == high) return lowPace
    val highPace = paceOf(options[high])
    val fraction = clamped - low
    return StarPace(
        driftSpeed = lowPace.driftSpeed + (highPace.driftSpeed - lowPace.driftSpeed) * fraction,
        twinkleSpeed = lowPace.twinkleSpeed +
            (highPace.twinkleSpeed - lowPace.twinkleSpeed) * fraction
    )
}

private fun paceOf(effort: ReasoningEffort?): StarPace {
    val drift = when (effort) {
        ReasoningEffort.LOW -> 16f
        ReasoningEffort.MEDIUM -> 50f
        ReasoningEffort.HIGH -> 120f
        ReasoningEffort.XHIGH -> 220f
        ReasoningEffort.MAX -> 360f
        null -> 16f
    }
    val twinkle = when (effort) {
        ReasoningEffort.LOW -> 1.2f
        ReasoningEffort.MEDIUM -> 2.5f
        ReasoningEffort.HIGH -> 4.5f
        ReasoningEffort.XHIGH -> 7.0f
        ReasoningEffort.MAX -> 10.0f
        null -> 1.2f
    }
    return StarPace(driftSpeed = drift, twinkleSpeed = twinkle)
}

internal fun effortColor(effort: ReasoningEffort): Color = when (effort) {
    ReasoningEffort.LOW -> AppleGreenLight
    ReasoningEffort.MEDIUM -> AppleYellowLight
    ReasoningEffort.HIGH -> AppleOrangeLight
    ReasoningEffort.XHIGH -> AppleRedLight
    ReasoningEffort.MAX -> AppleMagentaLight
}

private fun gradientColor(colors: List<Color>, value: Float): Color {
    val last = colors.lastIndex
    if (last < 0) return Color.Unspecified
    val clamped = value.fastCoerceIn(0f, last.toFloat())
    val low = floor(clamped).toInt()
    val high = ceil(clamped).toInt()
    if (low == high) return colors[low]
    return lerp(colors[low], colors[high], clamped - low)
}

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val offset: Float
)

private fun starField(): List<Star> {
    val random = Random(STAR_SEED)
    return List(STAR_COUNT) {
        Star(
            x = random.nextFloat(),
            y = STAR_MARGIN + random.nextFloat() * (1f - STAR_MARGIN * 2f),
            radius = STAR_MIN_RADIUS + random.nextFloat() * (STAR_MAX_RADIUS - STAR_MIN_RADIUS),
            offset = random.nextFloat() * TWO_PI
        )
    }
}

private fun DrawScope.drawStars(stars: List<Star>, phase: Float, value: Float, drift: Float) {
    val normalizedDrift = drift / size.width
    stars.forEach { star ->
        val twinkle = (sin(phase + star.offset) + 1f) * 0.5f
        val alpha = STAR_MIN_ALPHA + twinkle * (STAR_MAX_ALPHA - STAR_MIN_ALPHA)
        val shifted = (star.x + value * STAR_PARALLAX + normalizedDrift) % 1f
        val x = if (shifted < 0f) shifted + 1f else shifted
        drawCircle(
            color = Color.White,
            radius = star.radius.dp.toPx(),
            center = Offset(x * size.width, star.y * size.height),
            alpha = alpha
        )
    }
}

private const val MIN_EFFORT_OPTIONS = 2
private const val TWO_PI = 6.2831855f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val STAR_SEED = 20260901L
private const val STAR_COUNT = 24
private const val STAR_MIN_RADIUS = 0.6f
private const val STAR_MAX_RADIUS = 1.4f
private const val STAR_MIN_ALPHA = 0.12f
private const val STAR_MAX_ALPHA = 0.85f
private const val STAR_MARGIN = 0.12f
private const val STAR_PARALLAX = 0.14f
private const val SLIDER_TRACK_ALPHA_DARK = 0.10f
private const val SLIDER_TRACK_ALPHA_LIGHT = 0.07f
private const val SLIDER_DOT_REST_ALPHA = 0.30f
private const val SLIDER_DOT_ON_FILL_ALPHA = 0.45f
private const val SLIDER_THUMB_PRESSED_SCALE = 1.15f
private const val SLIDER_SHAKE_RATE = 0.07f
private val SLIDER_THUMB_LENS_HEIGHT = 4.dp
private val SLIDER_THUMB_LENS_AMOUNT = 8.dp
private val SLIDER_GALAXY_START = Color(0xFF5A6CF3)
private val SLIDER_GALAXY_MID = Color(0xFF9A5CF5)
private val SLIDER_SHAKE_AMPLITUDE = 2.dp
private val SLIDER_TRACK_HEIGHT = 26.dp
private val SLIDER_THUMB_DIAMETER = 32.dp
private val SLIDER_DOT_DIAMETER = 4.dp
