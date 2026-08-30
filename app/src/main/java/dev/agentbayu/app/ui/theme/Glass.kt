package dev.agentbayu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

@Immutable
data class GlassStyle(
    val fill: Color,
    val highlight: Color,
    val strokeWidth: Dp = 1.dp,
    val sheenAlpha: Float = 0.12f,
    val elevation: Dp = 4.dp,
    val refractionHeightRatio: Float = 0.5f,
    val refractionAmountRatio: Float = 1f,
    val highlightAlpha: Float = 0.6f,
    val brightness: Float = 0f,
    val saturation: Float = 1f,
    val vibrant: Boolean = true
)

val LocalGlassStyle = compositionLocalOf {
    GlassStyle(
        fill = GlassFillDark,
        highlight = GlassHighlightDark
    )
}

val LocalGlassBackdrop = staticCompositionLocalOf { emptyBackdrop() }

@Composable
fun currentGlassStyle(darkTheme: Boolean = isSystemInDarkTheme()): GlassStyle {
    return if (darkTheme) {
        GlassStyle(
            fill = GlassFillDark,
            highlight = GlassHighlightDark,
            strokeWidth = 1.dp,
            sheenAlpha = 0.10f,
            elevation = 6.dp,
            highlightAlpha = 0.5f,
            brightness = -0.04f
        )
    } else {
        GlassStyle(
            fill = GlassFillLight,
            highlight = GlassHighlightLight,
            strokeWidth = 1.dp,
            sheenAlpha = 0.16f,
            elevation = 4.dp,
            highlightAlpha = 0.7f,
            brightness = 0.12f
        )
    }
}

@Composable
fun solidGlassStyle(darkTheme: Boolean = isSystemInDarkTheme()): GlassStyle {
    val style = LocalGlassStyle.current
    return style.copy(fill = if (darkTheme) GlassSolidDark else GlassSolidLight)
}

fun glassSheenBrush(sheenAlpha: Float): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = sheenAlpha),
            Color.Transparent,
            Color.Black.copy(alpha = sheenAlpha * 0.25f)
        )
    )
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassCardShape,
    style: GlassStyle = LocalGlassStyle.current,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    tint: Color = Color.Unspecified,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null
): Modifier {
    val sheenBrush = glassSheenBrush(style.sheenAlpha)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        effects = {
            colorControls(brightness = style.brightness, saturation = style.saturation)
            if (style.vibrant) {
                vibrancy()
            }
            if (size.isSpecified) {
                val corner = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 0f
                val radius = corner.coerceAtMost(size.minDimension * 0.5f)
                lens(radius * style.refractionHeightRatio, radius * style.refractionAmountRatio)
            }
        },
        highlight = {
            Highlight(
                width = style.strokeWidth,
                alpha = style.highlightAlpha,
                style = HighlightStyle.Default(color = style.highlight)
            )
        },
        shadow = {
            Shadow(
                radius = style.elevation * 2f,
                color = ScrimBlack.copy(alpha = 0.2f)
            )
        },
        onDrawSurface = {
            drawRect(color = style.fill)
            if (tint.isSpecified) {
                drawRect(color = tint, blendMode = BlendMode.Hue)
                drawRect(color = tint.copy(alpha = 0.75f))
            }
            drawRect(brush = sheenBrush)
        }
    )
}
