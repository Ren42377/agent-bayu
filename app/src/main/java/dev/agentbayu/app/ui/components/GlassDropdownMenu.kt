package dev.agentbayu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.AgentBayuMotion

@Composable
fun GlassDropdownMenuHost(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuWidth: Dp? = null,
    scrollable: Boolean = true,
    trigger: @Composable (progress: () -> Float) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    var rawAnchor by remember { mutableStateOf<IntRect?>(null) }
    val progress = remember { Animatable(0f) }
    val progressProvider: () -> Float = remember(progress) { { progress.value } }
    val density = LocalDensity.current
    val anchor = remember(rawAnchor, menuWidth, density) {
        val raw = rawAnchor ?: return@remember null
        if (menuWidth != null) {
            val desiredWidthPx = with(density) { menuWidth.roundToPx() }
            val left = raw.right - desiredWidthPx
            IntRect(left, raw.top, raw.right, raw.bottom)
        } else {
            raw
        }
    }
    LaunchedEffect(expanded) {
        val springSpec = if (expanded) {
            AgentBayuMotion.menuElegantSpring
        } else {
            AgentBayuMotion.menuExitSpring
        }
        progress.animateTo(if (expanded) 1f else 0f, springSpec)
    }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            rawAnchor = IntRect(coordinates.positionInWindow().round(), coordinates.size)
        }
    ) {
        trigger(progressProvider)
    }
    GlassOverlay(
        visible = expanded && anchor != null,
        presentation = GlassOverlayPresentation.MENU,
        anchor = anchor,
        onDismiss = { onExpandedChange(false) }
    ) {
        Column(
            modifier = Modifier
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(6.dp),
            content = menuContent
        )
    }
}

@Composable
fun ColumnScope.GlassDropdownMenuItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    destructive: Boolean = false,
    icon: Int? = null
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, claimDrag = false)
    }
    val labelColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .then(interactiveHighlight.gestureModifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
