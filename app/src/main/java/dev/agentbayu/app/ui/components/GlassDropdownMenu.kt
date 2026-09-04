package dev.agentbayu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.liquidGlass

@Composable
fun GlassDropdownMenuHost(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trigger: @Composable (progress: () -> Float) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    var anchorHeight by remember { mutableIntStateOf(0) }
    var rendered by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val progressProvider: () -> Float = remember(progress) { { progress.value } }
    LaunchedEffect(expanded) {
        if (expanded) {
            rendered = true
            progress.animateTo(1f, AgentBayuMotion.panelSpring)
        } else {
            progress.animateTo(0f, AgentBayuMotion.panelSpring)
            rendered = false
        }
    }
    Box(modifier = modifier.onSizeChanged { size -> anchorHeight = size.height }) {
        trigger(progressProvider)
        if (rendered) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, anchorHeight),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = expanded)
            ) {
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val value = progress.value
                            alpha = value
                            val scale = lerp(MENU_MIN_SCALE, 1f, value)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .liquidGlass(
                            shape = GlassTileShape,
                            backdrop = LocalGlassBackdrop.current
                        )
                        .padding(6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = DROPDOWN_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                        content = menuContent
                    )
                }
            }
        }
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

private val DROPDOWN_MAX_HEIGHT = 320.dp
private const val MENU_MIN_SCALE = 0.92f
