package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.launch

@Stable
class LiquidIndication(private val darkTheme: Boolean) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): Modifier.Node {
        return LiquidIndicationNode(interactionSource, darkTheme)
    }

    override fun equals(other: Any?): Boolean {
        return other is LiquidIndication && other.darkTheme == darkTheme
    }

    override fun hashCode(): Int {
        return darkTheme.hashCode()
    }
}

private class LiquidIndicationNode(
    private val interactionSource: InteractionSource,
    darkTheme: Boolean
) : Modifier.Node(), DrawModifierNode {

    private val progress = Animatable(0f, PROGRESS_THRESHOLD)
    private val baseAlpha = if (darkTheme) DARK_BASE_ALPHA else LIGHT_BASE_ALPHA
    private val glowAlpha = if (darkTheme) DARK_GLOW_ALPHA else LIGHT_GLOW_ALPHA

    private var pressCenter = Offset.Zero
    private var shader: RuntimeShader? = null
    private var shaderBrush: ShaderBrush? = null

    override fun onAttach() {
        coroutineScope.launch {
            progress.snapTo(0f)
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressCenter = interaction.pressPosition
                        prepareShader()
                        launch { progress.animateTo(1f, AgentBayuMotion.pressSpring) }
                    }

                    is PressInteraction.Release -> launch { settle() }
                    is PressInteraction.Cancel -> launch { settle() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val value = progress.value
        if (value > 0f) {
            val cornerRadius = CornerRadius(
                CORNER_RADIUS.toPx().fastCoerceIn(0f, size.minDimension * 0.5f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = baseAlpha * value),
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Plus
            )
            val brush = shaderBrush
            val glow = Color.White.copy(alpha = glowAlpha * value)
            if (brush != null) {
                shader?.apply {
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", glow)
                    setFloatUniform("radius", size.minDimension * GLOW_RADIUS_RATIO)
                    setFloatUniform(
                        "position",
                        pressCenter.x.fastCoerceIn(0f, size.width),
                        pressCenter.y.fastCoerceIn(0f, size.height)
                    )
                }
                drawRoundRect(
                    brush = brush,
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Plus
                )
            } else {
                drawRoundRect(
                    color = glow,
                    cornerRadius = cornerRadius,
                    blendMode = BlendMode.Plus
                )
            }
        }
        drawContent()
    }

    private suspend fun settle() {
        if (progress.value < MIN_VISIBLE_PROGRESS) {
            progress.animateTo(MIN_VISIBLE_PROGRESS, AgentBayuMotion.pressSpring)
        }
        progress.animateTo(0f, AgentBayuMotion.pressSpring)
    }

    private fun prepareShader() {
        if (shaderBrush != null || !isRuntimeShaderSupported()) {
            return
        }
        val created = RuntimeShader(GLASS_HIGHLIGHT_SHADER)
        shader = created
        shaderBrush = ShaderBrush(created.asComposeShader())
    }
}

private const val PROGRESS_THRESHOLD = 0.001f
private const val LIGHT_BASE_ALPHA = 0.05f
private const val DARK_BASE_ALPHA = 0.1f
private const val LIGHT_GLOW_ALPHA = 0.1f
private const val DARK_GLOW_ALPHA = 0.18f
private const val GLOW_RADIUS_RATIO = 1.5f
private const val MIN_VISIBLE_PROGRESS = 0.25f
private val CORNER_RADIUS = 18.dp

internal const val GLASS_HIGHLIGHT_SHADER = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
