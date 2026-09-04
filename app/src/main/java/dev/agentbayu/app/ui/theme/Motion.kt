package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object AgentBayuMotion {
    const val ScrimAlpha = 0.5f
    const val PanelDismissFraction = 0.25f
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

    val snappySpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    val snappyColorSpring: AnimationSpec<Color> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    val gentleSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessLow
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

    val panelSizeSpec: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize.VisibilityThreshold
    )

    val quickFade: AnimationSpec<Float> = tween(durationMillis = 180)
}
