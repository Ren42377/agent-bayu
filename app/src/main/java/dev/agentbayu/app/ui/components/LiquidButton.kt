package dev.agentbayu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.LocalAppSurfaces
import dev.agentbayu.app.ui.theme.liftShadow

object GlassButtonDefaults {

    val ContentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

    val IconPadding: PaddingValues = PaddingValues(0.dp)

    val IconButtonSize: Dp = 40.dp
}

private const val BUTTON_PRESS_SCALE_DELTA = 0.04f
private const val BUTTON_DISABLED_ALPHA = 0.5f

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = GlassButtonDefaults.IconButtonSize,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .pressScaleFeedback(enabled),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    shape: Shape = CapsuleShape,
    contentPadding: PaddingValues = GlassButtonDefaults.ContentPadding,
    horizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, claimDrag = false)
    }
    val tinted = tint.isSpecified
    val containerColor = if (tinted) tint else LocalAppSurfaces.current.control
    val contentColor = when {
        !tinted -> scheme.onSurface
        tint == scheme.primary -> scheme.onPrimary
        else -> Color.White
    }

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else BUTTON_DISABLED_ALPHA)
            .graphicsLayer {
                val scale = 1f + BUTTON_PRESS_SCALE_DELTA * highlight.pressProgress
                scaleX = scale
                scaleY = scale
            }
            .then(if (tinted) Modifier else Modifier.liftShadow(shape))
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (enabled) highlight.gestureModifier else Modifier)
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
