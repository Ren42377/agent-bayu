package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color

object AgentBayuMotion {
    const val ScrimAlpha = 0.5f
    const val PanelDismissFraction = 0.25f

    val panelSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow
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

    val quickFade: AnimationSpec<Float> = tween(durationMillis = 180)
}
