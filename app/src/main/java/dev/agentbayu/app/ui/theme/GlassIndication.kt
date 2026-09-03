package dev.agentbayu.app.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Stable
class GlassIndication(private val darkTheme: Boolean) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): Modifier.Node {
        return GlassIndicationNode(interactionSource, darkTheme)
    }

    override fun equals(other: Any?): Boolean {
        return other is GlassIndication && other.darkTheme == darkTheme
    }

    override fun hashCode(): Int {
        return darkTheme.hashCode()
    }
}

private class GlassIndicationNode(
    private val interactionSource: InteractionSource,
    private val darkTheme: Boolean
) : Modifier.Node(), DrawModifierNode {

    private var pressed by mutableStateOf(false)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release -> pressed = false
                    is PressInteraction.Cancel -> pressed = false
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) {
            val color = if (darkTheme) {
                Color.White.copy(alpha = PressAlpha)
            } else {
                Color.Black.copy(alpha = PressAlpha)
            }
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(18.dp.toPx())
            )
        }
    }

    private companion object {
        const val PressAlpha = 0.07f
    }
}
