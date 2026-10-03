package dev.agentbayu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    canvasModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        val baseColor = MaterialTheme.colorScheme.background
        Canvas(modifier = canvasModifier.fillMaxSize()) {
            drawRect(color = baseColor)
        }
        content()
    }
}
