package dev.agentbayu.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.agentbayu.app.ui.theme.AppleBlueDark
import dev.agentbayu.app.ui.theme.AppleIndigoDark
import dev.agentbayu.app.ui.theme.AppleTealDark

@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = baseColor)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraPrimary, Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.85f, size.height * 0.15f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraSecondary, Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.65f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.15f, size.height * 0.65f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(auraTertiary, Color.Transparent),
                    center = Offset(size.width * 0.7f, size.height * 0.9f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.7f, size.height * 0.9f)
            )
        }

        content()
    }
}
