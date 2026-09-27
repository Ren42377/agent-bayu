package dev.agentbayu.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val claimDrag: Boolean = true
) {

    private val pressProgressAnimationSpec = AgentBayuMotion.pressSpring
    private val positionAnimationSpec = spring(
        AgentBayuMotion.PressDampingRatio,
        AgentBayuMotion.PressStiffness,
        Offset.VisibilityThreshold
    )

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
            claimDrag = claimDrag
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun settle() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }

    suspend fun prewarm() {
        pressProgressAnimation.snapTo(PREWARM_PROGRESS)
        pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
    }
}

private const val PREWARM_PROGRESS = 0.05f
private const val PRESS_SCALE_DELTA = 0.04f

@Composable
internal fun Modifier.pressScaleFeedback(enabled: Boolean = true): Modifier {
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) {
        InteractiveHighlight(animationScope, claimDrag = false)
    }
    return this
        .graphicsLayer {
            val scale = 1f - PRESS_SCALE_DELTA * highlight.pressProgress
            scaleX = scale
            scaleY = scale
        }
        .then(if (enabled) highlight.gestureModifier else Modifier)
}
