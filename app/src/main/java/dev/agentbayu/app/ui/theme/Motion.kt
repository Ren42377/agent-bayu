package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object AgentBayuMotion {
    const val ScrimAlpha = 0.5f
    const val PressDampingRatio = 0.5f
    const val PressStiffness = 300f

    val panelSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow
    )

    val assistantPanelSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMedium
    )

    val snappySpring: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    val pressSpring: AnimationSpec<Float> = spring(
        dampingRatio = PressDampingRatio,
        stiffness = PressStiffness,
        visibilityThreshold = 0.001f
    )

    val navFadeSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMediumLow
    )

    val navSlideSpec: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val quickFade: FiniteAnimationSpec<Float> = tween(durationMillis = 180)
}
