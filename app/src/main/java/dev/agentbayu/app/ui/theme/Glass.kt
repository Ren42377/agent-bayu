package dev.agentbayu.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class GlassStyle(
    val fill: Color,
    val border: Color,
    val highlight: Color,
    val strokeWidth: Dp = 1.dp,
    val sheenAlpha: Float = 0.12f,
    val elevation: Dp = 4.dp
)

val LocalGlassStyle = compositionLocalOf {
    GlassStyle(
        fill = GlassFillDark,
        border = GlassBorderDark,
        highlight = GlassHighlightDark
    )
}

@Composable
fun currentGlassStyle(darkTheme: Boolean = isSystemInDarkTheme()): GlassStyle {
    return if (darkTheme) {
        GlassStyle(
            fill = GlassFillDark,
            border = GlassBorderDark,
            highlight = GlassHighlightDark,
            strokeWidth = 1.dp,
            sheenAlpha = 0.10f,
            elevation = 6.dp
        )
    } else {
        GlassStyle(
            fill = GlassFillLight,
            border = GlassBorderLight,
            highlight = GlassHighlightLight,
            strokeWidth = 1.dp,
            sheenAlpha = 0.16f,
            elevation = 4.dp
        )
    }
}

fun glassBorderBrush(
    highlightColor: Color,
    borderColor: Color
): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            highlightColor,
            borderColor,
            borderColor.copy(alpha = borderColor.alpha * 0.4f)
        )
    )
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
    shape: Shape = RoundedCornerShape(20.dp),
    style: GlassStyle = LocalGlassStyle.current
): Modifier {
    return this
        .shadow(
            elevation = style.elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.2f),
            spotColor = Color.Black.copy(alpha = 0.35f)
        )
        .background(color = style.fill, shape = shape)
        .background(brush = glassSheenBrush(style.sheenAlpha), shape = shape)
        .border(
            border = BorderStroke(style.strokeWidth, glassBorderBrush(style.highlight, style.border)),
            shape = shape
        )
        .clip(shape)
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    style: GlassStyle = LocalGlassStyle.current,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlass(shape = shape, style = style),
        content = content
    )
}
