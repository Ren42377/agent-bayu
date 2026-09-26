package dev.agentbayu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalDarkTheme = staticCompositionLocalOf { false }

private val lightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0A0A0A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF141414),
    secondary = Color(0xFF545454),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9E9),
    onSecondaryContainer = Color(0xFF202020),
    tertiary = Color(0xFF7A7A7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2F2F2),
    onTertiaryContainer = Color(0xFF2E2E2E),
    error = AppleRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFD8D6),
    onErrorContainer = Color(0xFF8A0A04),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA),
    inverseSurface = SurfaceDark,
    inverseOnSurface = TextPrimaryDark,
    inversePrimary = Color(0xFFFFFFFF),
    scrim = ScrimBlack
)

private val darkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFF9A9A9A),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF242424),
    onTertiaryContainer = Color(0xFFE0E0E0),
    error = AppleRedDark,
    onError = Color.Black,
    errorContainer = Color(0xFF8A0A04),
    onErrorContainer = Color(0xFFFFD8D6),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
    inverseSurface = SurfaceLight,
    inverseOnSurface = TextPrimaryLight,
    inversePrimary = Color(0xFF0A0A0A),
    scrim = ScrimBlack
)

@Composable
fun AgentBayuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColors
        else -> lightColors
    }
    val glassStyle = currentGlassStyle(darkTheme)
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalDarkTheme provides darkTheme,
        LocalGlassStyle provides glassStyle,
        LocalContentColor provides colorScheme.onSurface
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AgentBayuTypography,
            shapes = AgentBayuShapes,
            content = content
        )
    }
}

private val NoIndication: IndicationNodeFactory = object : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): Modifier.Node = EmptyIndicationNode()

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = javaClass.hashCode()
}

private class EmptyIndicationNode : Modifier.Node()
