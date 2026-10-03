package dev.agentbayu.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalGlassStyle
import dev.agentbayu.app.ui.theme.LocalThemeDarkFraction
import kotlin.math.abs

@Composable
internal fun GlassSegmentedSelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<Painter> = emptyList(),
    onScrub: (reader: (() -> Float)?) -> Unit = {}
) {
    if (labels.isEmpty()) {
        return
    }
    val lastIndex = labels.lastIndex
    val safeSelectedIndex = selectedIndex.fastCoerceIn(0, lastIndex)
    val backdrop = LocalGlassBackdrop.current
    val containerBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(backdrop, containerBackdrop)
    val animationScope = rememberCoroutineScope()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var currentIndex by remember { mutableIntStateOf(safeSelectedIndex) }
    var scrubbing by remember { mutableStateOf(false) }
    val pressedFill = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(if (icons.isEmpty()) SELECTOR_HEIGHT else SELECTOR_WITH_ICONS_HEIGHT)
    ) {
        val segmentWidth = maxWidth / labels.size
        val segmentWidthPx = (constraints.maxWidth.toFloat() / labels.size).coerceAtLeast(1f)
        val dragAnimation = remember(animationScope, segmentWidthPx, lastIndex) {
            var travel = 0f
            var downIndex = safeSelectedIndex
            var dragAnchor = 0f
            var dragDistance = 0f
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = safeSelectedIndex.toFloat(),
                valueRange = 0f..lastIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = SELECTOR_PRESSED_SCALE,
                onDragStarted = { dragPosition ->
                    travel = 0f
                    scrubbing = false
                    downIndex = (dragPosition.x / segmentWidthPx).toInt().fastCoerceIn(0, lastIndex)
                    dragAnchor = value
                    dragDistance = 0f
                },
                onDragStopped = {
                    scrubbing = false
                    val selected = if (travel < touchSlop) {
                        downIndex
                    } else {
                        targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                    }
                    currentIndex = selected
                    animateToValue(selected.toFloat(), pressed = false)
                    currentOnSelect(selected)
                },
                onDrag = { _, dragAmount ->
                    travel += abs(dragAmount.x)
                    dragDistance += dragAmount.x
                    if (!scrubbing && travel >= touchSlop) {
                        scrubbing = true
                    }
                    val target = (dragAnchor + dragDistance / segmentWidthPx)
                        .fastCoerceIn(0f, lastIndex.toFloat())
                    updateValue(target)
                },
                onDragCanceled = {
                    scrubbing = false
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
        val positionReader: () -> Float = remember(dragAnimation) { { dragAnimation.value } }
        val scrubActive = scrubbing
        DisposableEffect(positionReader, scrubActive) {
            if (scrubActive) {
                currentOnScrub(positionReader)
            }
            onDispose {
                if (scrubActive) {
                    currentOnScrub(null)
                }
            }
        }

        SelectorTrack(modifier = Modifier.matchParentSize())
        SelectorMirror(
            labels = labels,
            icons = icons,
            dragAnimation = dragAnimation,
            segmentWidthPx = segmentWidthPx,
            containerBackdrop = containerBackdrop,
            modifier = Modifier.matchParentSize()
        )
        SelectorLabels(
            labels = labels,
            icons = icons,
            isSelected = { index -> index == currentIndex },
            onItemClick = { index ->
                currentIndex = index
                dragAnimation.animateToValue(index.toFloat(), pressed = false)
                currentOnSelect(index)
            },
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = dragAnimation.value * segmentWidthPx }
                .drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { CapsuleShape },
                    effects = {
                        if (supportsRefraction) {
                            val strength = dragAnimation.pressProgress
                            lens(
                                SELECTOR_LENS_HEIGHT.toPx() * strength,
                                SELECTOR_LENS_AMOUNT.toPx() * strength,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dragAnimation.pressProgress
                        InnerShadow(radius = SELECTOR_INNER_SHADOW * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = indicatorScaleX(dragAnimation)
                        scaleY = indicatorScaleY(dragAnimation)
                    },
                    onDrawSurface = {
                        drawRect(
                            pressedFill.copy(alpha = SELECTOR_PRESSED_FILL_ALPHA * dragAnimation.pressProgress)
                        )
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

@Composable
private fun SelectorTrack(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val darkFraction = LocalThemeDarkFraction.current
    Box(
        modifier = modifier
            .background(
                color = colors.onSurface.copy(
                    alpha = lerp(TRACK_ALPHA, DARK_TRACK_ALPHA, darkFraction)
                ),
                shape = CapsuleShape
            )
            .border(
                width = 1.dp,
                color = colors.onSurface.copy(
                    alpha = lerp(BORDER_ALPHA, DARK_BORDER_ALPHA, darkFraction)
                ),
                shape = CapsuleShape
            )
    )
}

@Composable
private fun SelectorMirror(
    labels: List<String>,
    icons: List<Painter>,
    dragAnimation: DampedDragAnimation,
    segmentWidthPx: Float,
    containerBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val darkFraction = LocalThemeDarkFraction.current
    val surfaceColor = LocalGlassStyle.current.surface
    val trackAlpha = lerp(TRACK_ALPHA, DARK_TRACK_ALPHA, darkFraction)
    val rimAlpha = lerp(BORDER_ALPHA, DARK_BORDER_ALPHA, darkFraction)
    val rimColor = colors.onSurface
    val trackColor = colors.onSurface
    val tintColor = colors.primary
    val flip = ((dragAnimation.pressProgress - CONTENT_FLIP_START) / CONTENT_FLIP_SPAN)
        .fastCoerceIn(0f, 1f)
    val contentColor = lerp(colors.onPrimary, colors.primary, flip)
    Box(
        modifier = modifier
            .clearAndSetSemantics {}
            .alpha(0f)
            .layerBackdrop(containerBackdrop)
            .drawBehind {
                val overflow = MIRROR_OVERFLOW.toPx()
                drawRect(
                    color = surfaceColor,
                    topLeft = Offset(-overflow, -overflow),
                    size = Size(size.width + overflow * 2f, size.height + overflow * 2f)
                )
                val press = dragAnimation.pressProgress
                drawRoundRect(
                    color = trackColor.copy(alpha = trackAlpha * (1f + MIRROR_TRACK_BOOST * press)),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
                val rimWidth = MIRROR_RIM_WIDTH.toPx()
                drawRoundRect(
                    color = rimColor.copy(alpha = lerp(rimAlpha, MIRROR_RIM_PRESSED_ALPHA, press)),
                    topLeft = Offset(rimWidth / 2f, rimWidth / 2f),
                    size = Size(size.width - rimWidth, size.height - rimWidth),
                    cornerRadius = CornerRadius((size.height - rimWidth) / 2f),
                    style = Stroke(width = rimWidth)
                )
                val tintAlpha = lerp(SELECTOR_TINT_ALPHA, 0f, press)
                if (tintAlpha > 0f) {
                    val left = dragAnimation.value * segmentWidthPx
                    scale(
                        scaleX = indicatorScaleX(dragAnimation),
                        scaleY = indicatorScaleY(dragAnimation),
                        pivot = Offset(left + segmentWidthPx / 2f, size.height / 2f)
                    ) {
                        drawRoundRect(
                            color = tintColor.copy(alpha = tintAlpha),
                            topLeft = Offset(left, 0f),
                            size = Size(segmentWidthPx, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                }
            }
    ) {
        SelectorRow(
            labels = labels,
            icons = icons,
            color = contentColor,
            itemModifier = {
                Modifier.graphicsLayer {
                    val magnify = lerp(1f, SELECTOR_CONTENT_PRESS_SCALE, dragAnimation.pressProgress)
                    scaleX = magnify
                    scaleY = magnify
                }
            }
        )
    }
}

@Composable
private fun SelectorLabels(
    labels: List<String>,
    icons: List<Painter>,
    isSelected: (Int) -> Boolean,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SelectorRow(
        labels = labels,
        icons = icons,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.selectableGroup(),
        itemModifier = { index ->
            Modifier.semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected(index)
                onClick {
                    onItemClick(index)
                    true
                }
            }
        }
    )
}

@Composable
private fun SelectorRow(
    labels: List<String>,
    icons: List<Painter>,
    color: Color,
    modifier: Modifier = Modifier,
    itemModifier: (Int) -> Modifier = { Modifier }
) {
    Row(modifier = modifier.fillMaxSize()) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(itemModifier(index)),
                contentAlignment = Alignment.Center
            ) {
                SelectorLabel(
                    label = label,
                    icon = icons.getOrNull(index),
                    color = color
                )
            }
        }
    }
}

@Composable
private fun SelectorLabel(
    label: String,
    icon: Painter?,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (icon == null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = modifier
        )
        return
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

private fun indicatorScaleX(animation: DampedDragAnimation): Float {
    val velocity = animation.velocity / SELECTOR_VELOCITY_SCALE
    val squish = (velocity * 0.75f).fastCoerceIn(-SELECTOR_SQUISH, SELECTOR_SQUISH)
    return animation.scaleX / (1f - squish)
}

private fun indicatorScaleY(animation: DampedDragAnimation): Float {
    val velocity = animation.velocity / SELECTOR_VELOCITY_SCALE
    val squish = (velocity * 0.25f).fastCoerceIn(-SELECTOR_SQUISH, SELECTOR_SQUISH)
    return animation.scaleY * (1f - squish)
}

private const val TRACK_ALPHA = 0.06f
private const val DARK_TRACK_ALPHA = 0.035f
private const val BORDER_ALPHA = 0.08f
private const val DARK_BORDER_ALPHA = 0.06f
private const val SELECTOR_TINT_ALPHA = 0.92f
private const val SELECTOR_PRESSED_FILL_ALPHA = 0.1f
private const val MIRROR_TRACK_BOOST = 2f
private const val MIRROR_RIM_PRESSED_ALPHA = 0.3f
private const val SELECTOR_PRESSED_SCALE = 78f / 56f
private const val SELECTOR_CONTENT_PRESS_SCALE = 1.12f
private const val SELECTOR_VELOCITY_SCALE = 10f
private const val SELECTOR_SQUISH = 0.2f
private const val CONTENT_FLIP_START = 0.2f
private const val CONTENT_FLIP_SPAN = 0.5f
private val SELECTOR_HEIGHT = 36.dp
private val SELECTOR_WITH_ICONS_HEIGHT = 52.dp
private val SELECTOR_LENS_HEIGHT = 10.dp
private val SELECTOR_LENS_AMOUNT = 14.dp
private val MIRROR_RIM_WIDTH = 1.5.dp
private val SELECTOR_INNER_SHADOW = 8.dp
private val MIRROR_OVERFLOW = 24.dp

private val supportsRefraction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
