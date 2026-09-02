package dev.agentbayu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.agentbayu.app.ui.theme.AppleBlueDark
import dev.agentbayu.app.ui.theme.AppleIndigoDark
import dev.agentbayu.app.ui.theme.AppleTealDark
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    canvasModifier: Modifier = Modifier,
    darkTheme: Boolean = LocalDarkTheme.current,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val baseColor = MaterialTheme.colorScheme.background
        val auraPrimary = if (darkTheme) {
            AppleIndigoDark.copy(alpha = 0.18f)
        } else {
            AppleIndigoDark.copy(alpha = 0.08f)
        }
        val auraSecondary = if (darkTheme) {
            AppleBlueDark.copy(alpha = 0.15f)
        } else {
            AppleBlueDark.copy(alpha = 0.07f)
        }
        val auraTertiary = if (darkTheme) {
            AppleTealDark.copy(alpha = 0.12f)
        } else {
            AppleTealDark.copy(alpha = 0.05f)
        }

        val drift = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(DRIFT_STEP_MILLIS)
                drift.floatValue = (drift.floatValue + DRIFT_STEP_MILLIS) % DRIFT_WRAP_MILLIS
            }
        }

        Canvas(modifier = canvasModifier.fillMaxSize()) {
            val elapsed = drift.floatValue
            drawRect(color = baseColor)

            val primaryCenter = driftCenter(
                size = size,
                baseX = 0.85f,
                baseY = 0.15f,
                phase = elapsed / PRIMARY_CYCLE_MILLIS,
                xTurns = 1f,
                yTurns = 2f,
                offset = 0f
            )
            val primaryRadius = size.width * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraPrimary, Color.Transparent),
                    center = primaryCenter,
                    radius = primaryRadius
                ),
                radius = primaryRadius,
                center = primaryCenter
            )

            val secondaryCenter = driftCenter(
                size = size,
                baseX = 0.15f,
                baseY = 0.65f,
                phase = elapsed / SECONDARY_CYCLE_MILLIS,
                xTurns = 2f,
                yTurns = 1f,
                offset = HALF_PI
            )
            val secondaryRadius = size.width * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraSecondary, Color.Transparent),
                    center = secondaryCenter,
                    radius = secondaryRadius
                ),
                radius = secondaryRadius,
                center = secondaryCenter
            )

            val tertiaryCenter = driftCenter(
                size = size,
                baseX = 0.7f,
                baseY = 0.9f,
                phase = elapsed / TERTIARY_CYCLE_MILLIS,
                xTurns = 1f,
                yTurns = 3f,
                offset = PI.toFloat()
            )
            val tertiaryRadius = size.width * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraTertiary, Color.Transparent),
                    center = tertiaryCenter,
                    radius = tertiaryRadius
                ),
                radius = tertiaryRadius,
                center = tertiaryCenter
            )
        }

        content()
    }
}

private fun driftCenter(
    size: Size,
    baseX: Float,
    baseY: Float,
    phase: Float,
    xTurns: Float,
    yTurns: Float,
    offset: Float
): Offset {
    val angle = phase * TWO_PI
    val amplitude = size.width * DRIFT_AMPLITUDE
    return Offset(
        x = size.width * baseX + sin(angle * xTurns + offset) * amplitude,
        y = size.height * baseY + sin(angle * yTurns + offset + HALF_PI) * amplitude
    )
}

private const val PRIMARY_CYCLE_MILLIS = 45_000f
private const val SECONDARY_CYCLE_MILLIS = 60_000f
private const val TERTIARY_CYCLE_MILLIS = 48_000f
private const val DRIFT_STEP_MILLIS = 500L
private const val DRIFT_WRAP_MILLIS = 3_600_000f
private const val DRIFT_AMPLITUDE = 0.08f
private val TWO_PI = (PI * 2.0).toFloat()
private val HALF_PI = (PI / 2.0).toFloat()
