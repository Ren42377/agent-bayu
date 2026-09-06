package dev.agentbayu.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val pulse = transition.animateFloat(
        initialValue = MIN_SCALE,
        targetValue = MAX_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typingPulse"
    )
    Box(
        modifier = modifier.size(DOT_BOX),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(DOT_SIZE)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .background(MaterialTheme.colorScheme.onSurface, CircleShape)
        )
    }
}

private const val MIN_SCALE = 0.55f
private const val MAX_SCALE = 1f
private const val PULSE_MILLIS = 620
private val DOT_SIZE = 12.dp
private val DOT_BOX = 20.dp
