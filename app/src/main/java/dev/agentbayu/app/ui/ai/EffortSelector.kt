package dev.agentbayu.app.ui.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val transition = rememberInfiniteTransition(label = "effortStars")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = STAR_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "effortStarPhase"
    )
    GlassSegmentedSelector(
        labels = options.map { it.label },
        selectedIndex = selectedIndex,
        onSelect = { index -> onSelect(options[index]) },
        modifier = modifier,
        tint = colors[selectedIndex],
        tintProvider = { value -> gradientColor(colors, value) },
        decoration = { value -> drawStars(stars, phase.value, value) }
    )
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

private fun DrawScope.drawStars(stars: List<Star>, phase: Float, value: Float) {
    stars.forEach { star ->
        val twinkle = (sin(phase + star.offset) + 1f) * 0.5f
        val alpha = STAR_MIN_ALPHA + twinkle * (STAR_MAX_ALPHA - STAR_MIN_ALPHA)
        val shifted = (star.x + value * STAR_PARALLAX) % 1f
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
private const val STAR_SEED = 20260901L
private const val STAR_COUNT = 24
private const val STAR_CYCLE_MILLIS = 4200
private const val STAR_MIN_RADIUS = 0.6f
private const val STAR_MAX_RADIUS = 1.4f
private const val STAR_MIN_ALPHA = 0.12f
private const val STAR_MAX_ALPHA = 0.85f
private const val STAR_MARGIN = 0.12f
private const val STAR_PARALLAX = 0.14f
