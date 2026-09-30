package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun ThemeCrossfade(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = remember { ThemeCrossfadeState() }
    val veil = remember { Animatable(0f) }
    val veilColor = MaterialTheme.colorScheme.background
    LaunchedEffect(darkTheme) {
        if (!state.onTheme(darkTheme)) return@LaunchedEffect
        veil.snapTo(1f)
        veil.animateTo(0f, AgentBayuMotion.themeCrossfade)
        state.onFinished()
    }
    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (state.active) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = veil.value }
                    .background(veilColor)
            )
        }
    }
}
