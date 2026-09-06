package dev.agentbayu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
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
    trigger: @Composable (progress: () -> Float) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    var anchor by remember { mutableStateOf<IntRect?>(null) }
    val progress = remember { Animatable(0f) }
    val progressProvider: () -> Float = remember(progress) { { progress.value } }
    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f, AgentBayuMotion.panelSpring)
    }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            anchor = IntRect(coordinates.positionInWindow().round(), coordinates.size)
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
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            content = menuContent
        )
    }
}

@Composable
fun ColumnScope.GlassDropdownMenuItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, claimDrag = false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
