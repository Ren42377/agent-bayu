package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.agentbayu.app.ai.ReasoningEffort
import dev.agentbayu.app.ui.components.GlassSegmentedSelector
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleMagentaLight
import dev.agentbayu.app.ui.theme.AppleOrangeLight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.AppleYellowLight
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

    GlassSegmentedSelector(
        labels = options.map { it.label },
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

private fun effortColor(effort: ReasoningEffort): Color = when (effort) {
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
