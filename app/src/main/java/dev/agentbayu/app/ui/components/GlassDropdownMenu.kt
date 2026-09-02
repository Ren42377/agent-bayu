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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

@Composable
fun GlassDropdownMenuHost(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trigger: @Composable () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    var anchorHeight by remember { mutableIntStateOf(0) }
    Box(modifier = modifier.onSizeChanged { size -> anchorHeight = size.height }) {
        trigger()
        if (expanded) {
            val density = LocalDensity.current
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                progress.animateTo(1f, AgentBayuMotion.snappySpring)
            }
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, with(density) { anchorHeight.roundToInt() }),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val value = progress.value
                            alpha = value
                            val scale = lerp(0.92f, 1f, value)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
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
