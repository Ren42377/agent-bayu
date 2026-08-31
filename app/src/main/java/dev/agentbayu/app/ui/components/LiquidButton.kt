package dev.agentbayu.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.liquidGlass
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

object GlassButtonDefaults {

    val ContentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

    val IconPadding: PaddingValues = PaddingValues(0.dp)
}

private const val RAISED_Z_INDEX = 1f

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
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val raised by remember(interactiveHighlight) {
        derivedStateOf { interactiveHighlight.pressProgress > 0f }
    }

    Row(
        modifier = modifier
            .zIndex(if (raised) RAISED_Z_INDEX else 0f)
            .liquidGlass(
                shape = shape,
                tint = tint,
                layerBlock = if (enabled) {
                    {
                        val width = size.width
                        val height = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4.dp.toPx() / height, progress)

                        val maxOffset = size.minDimension
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)

                        val maxDragScale = 4.dp.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                        scaleY = scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (enabled) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (enabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
