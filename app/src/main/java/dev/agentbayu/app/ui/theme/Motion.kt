package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object AgentBayuMotion {

    const val ScrimAlpha = 0.45f
    const val PanelDismissFraction = 0.3f

    val panelSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow
    )

    val quickFade: AnimationSpec<Float> = tween(durationMillis = 180)
}
