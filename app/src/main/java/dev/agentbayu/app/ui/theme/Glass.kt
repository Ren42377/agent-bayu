package dev.agentbayu.app.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
    val vibrant: Boolean = true,
    val surfaceTop: Color = fill,
    val surfaceBottom: Color = fill,
    val surfaceEdgeTop: Color = highlight,
    val surfaceEdgeBottom: Color = highlight,
    val surfaceElevation: Dp = elevation
)

val LocalGlassStyle = compositionLocalOf {
    GlassStyle(
        fill = GlassFillDark,
        highlight = GlassHighlightDark,
        surfaceTop = GlassSurfaceTopDark,
        surfaceBottom = GlassSurfaceBottomDark,
        surfaceEdgeTop = GlassEdgeTopDark,
        surfaceEdgeBottom = GlassEdgeBottomDark
    )
}

val LocalGlassBackdrop = staticCompositionLocalOf { emptyBackdrop() }

@Composable
fun currentGlassStyle(darkTheme: Boolean = LocalDarkTheme.current): GlassStyle {
    return if (darkTheme) {
        GlassStyle(
            fill = GlassFillDark,
            highlight = GlassHighlightDark,
            strokeWidth = 1.dp,
            sheenAlpha = 0.14f,
            elevation = 6.dp,
            highlightAlpha = 0.7f,
            brightness = 0f,
            surfaceTop = GlassSurfaceTopDark,
            surfaceBottom = GlassSurfaceBottomDark,
            surfaceEdgeTop = GlassEdgeTopDark,
            surfaceEdgeBottom = GlassEdgeBottomDark,
            surfaceElevation = 2.dp
        )
    } else {
        GlassStyle(
            fill = GlassFillLight,
            highlight = GlassHighlightLight,
            strokeWidth = 1.dp,
            sheenAlpha = 0.16f,
            elevation = 4.dp,
            highlightAlpha = 0.7f,
            brightness = 0.12f,
            surfaceTop = GlassSurfaceTopLight,
            surfaceBottom = GlassSurfaceBottomLight,
            surfaceEdgeTop = GlassEdgeTopLight,
            surfaceEdgeBottom = GlassEdgeBottomLight,
            surfaceElevation = 5.dp
        )
    }
}

@Composable
fun solidGlassStyle(darkTheme: Boolean = LocalDarkTheme.current): GlassStyle {
    val style = LocalGlassStyle.current
    return if (darkTheme) {
        style.copy(
            fill = GlassSolidDark,
            surfaceTop = GlassSurfaceSolidTopDark,
            surfaceBottom = GlassSurfaceSolidBottomDark
        )
    } else {
        style.copy(
            fill = GlassSolidLight,
            surfaceTop = GlassSurfaceSolidTopLight,
            surfaceBottom = GlassSurfaceSolidBottomLight
        )
    }
}

@Composable
fun chromeGlassStyle(darkTheme: Boolean = LocalDarkTheme.current): GlassStyle {
    val style = LocalGlassStyle.current
    return if (darkTheme) {
        style.copy(fill = GlassChromeDark, saturation = CHROME_SATURATION)
    } else {
        style.copy(fill = GlassChromeLight, saturation = CHROME_SATURATION)
    }
}

private const val CHROME_SATURATION = 0.9f

fun glassSheenBrush(sheenAlpha: Float): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = sheenAlpha),
            Color.Transparent,
            Color.Black.copy(alpha = sheenAlpha * 0.25f)
        )
    )
}

fun glassSurfaceSheenBrush(sheenAlpha: Float): Brush {
    return Brush.verticalGradient(
        0f to Color.White.copy(alpha = (sheenAlpha * 1.4f).coerceAtMost(1f)),
        0.3f to Color.White.copy(alpha = sheenAlpha * 0.28f),
        0.62f to Color.Transparent,
        1f to Color.Black.copy(alpha = sheenAlpha * 0.22f)
    )
}

@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassCardShape,
    style: GlassStyle = LocalGlassStyle.current,
    tint: Color = Color.Unspecified,
    elevation: Dp = style.surfaceElevation
): Modifier {
    val fillBrush = remember(style.surfaceTop, style.surfaceBottom) {
        Brush.verticalGradient(listOf(style.surfaceTop, style.surfaceBottom))
    }
    val sheenBrush = remember(style.sheenAlpha) { glassSurfaceSheenBrush(style.sheenAlpha) }
    val tintBrush = remember(tint) {
        if (tint.isSpecified) {
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = STATIC_TINT_TOP_ALPHA),
                    tint.copy(alpha = STATIC_TINT_BOTTOM_ALPHA)
                )
            )
        } else {
            null
        }
    }
    val edgeBrush = remember(style.surfaceEdgeTop, style.surfaceEdgeBottom, style.highlightAlpha) {
        Brush.verticalGradient(
            listOf(
                style.surfaceEdgeTop.copy(
                    alpha = style.surfaceEdgeTop.alpha * style.highlightAlpha
                ),
                style.surfaceEdgeBottom.copy(
                    alpha = style.surfaceEdgeBottom.alpha * style.highlightAlpha
                )
            )
        )
    }
    return this
        .shadow(elevation = elevation, shape = shape, clip = true)
        .drawBehind {
            drawRect(brush = fillBrush)
            tintBrush?.let { drawRect(brush = it) }
            drawRect(brush = sheenBrush)
        }
        .border(width = style.strokeWidth, brush = edgeBrush, shape = shape)
}

private const val STATIC_TINT_TOP_ALPHA = 0.9f
private const val STATIC_TINT_BOTTOM_ALPHA = 1f

@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassCardShape,
    style: GlassStyle = LocalGlassStyle.current,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    tint: Color = Color.Unspecified,
    tintAlpha: Float = LIQUID_TINT_ALPHA,
    tintProvider: (() -> Color)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null
): Modifier {
    val sheenBrush = remember(style.sheenAlpha) { glassSheenBrush(style.sheenAlpha) }
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
            val activeTint = tintProvider?.invoke() ?: tint
            if (activeTint.isSpecified) {
                drawRect(color = activeTint, blendMode = BlendMode.Hue)
                drawRect(color = activeTint.copy(alpha = tintAlpha))
            }
            drawRect(brush = sheenBrush)
        }
    )
}

private const val LIQUID_TINT_ALPHA = 0.75f
