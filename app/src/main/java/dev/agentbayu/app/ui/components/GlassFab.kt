package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.ui.theme.ControlDark
import dev.agentbayu.app.ui.theme.FilledControlLight
import dev.agentbayu.app.ui.theme.LocalThemeDarkFraction

@Composable
fun GlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val container = lerp(FilledControlLight, ControlDark, LocalThemeDarkFraction.current)
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        enabled = enabled,
        tint = container,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        content()
    }
}
