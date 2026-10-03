package dev.agentbayu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

val LocalDarkTheme = compositionLocalOf { false }

val LocalThemeDarkFraction = compositionLocalOf { 0f }

private val lightColors: ColorScheme = lightColorScheme(
    primary = FilledControlLight,
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
    darkFraction: Float,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val fraction = darkFraction.coerceIn(0f, 1f)
    val lightScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        lightColors
    }
    val darkScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColors
    }
    val colorScheme = when {
        fraction <= MORPH_EPSILON -> lightScheme
        fraction >= 1f - MORPH_EPSILON -> darkScheme
        else -> lerpColorScheme(lightScheme, darkScheme, fraction)
    }
    val glassStyle = when {
        fraction <= MORPH_EPSILON -> lightGlassStyle
        fraction >= 1f - MORPH_EPSILON -> darkGlassStyle
        else -> lerpGlassStyle(lightGlassStyle, darkGlassStyle, fraction)
    }
    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalDarkTheme provides (fraction >= DARK_SWITCH_POINT),
        LocalThemeDarkFraction provides fraction,
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

private const val MORPH_EPSILON = 0.001f
private const val DARK_SWITCH_POINT = 0.5f

private fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    fun mix(from: Color, to: Color): Color = lerp(from, to, fraction)
    return start.copy(
        primary = mix(start.primary, stop.primary),
        onPrimary = mix(start.onPrimary, stop.onPrimary),
        primaryContainer = mix(start.primaryContainer, stop.primaryContainer),
        onPrimaryContainer = mix(start.onPrimaryContainer, stop.onPrimaryContainer),
        inversePrimary = mix(start.inversePrimary, stop.inversePrimary),
        secondary = mix(start.secondary, stop.secondary),
        onSecondary = mix(start.onSecondary, stop.onSecondary),
        secondaryContainer = mix(start.secondaryContainer, stop.secondaryContainer),
        onSecondaryContainer = mix(start.onSecondaryContainer, stop.onSecondaryContainer),
        tertiary = mix(start.tertiary, stop.tertiary),
        onTertiary = mix(start.onTertiary, stop.onTertiary),
        tertiaryContainer = mix(start.tertiaryContainer, stop.tertiaryContainer),
        onTertiaryContainer = mix(start.onTertiaryContainer, stop.onTertiaryContainer),
        background = mix(start.background, stop.background),
        onBackground = mix(start.onBackground, stop.onBackground),
        surface = mix(start.surface, stop.surface),
        onSurface = mix(start.onSurface, stop.onSurface),
        surfaceVariant = mix(start.surfaceVariant, stop.surfaceVariant),
        onSurfaceVariant = mix(start.onSurfaceVariant, stop.onSurfaceVariant),
        surfaceTint = mix(start.surfaceTint, stop.surfaceTint),
        inverseSurface = mix(start.inverseSurface, stop.inverseSurface),
        inverseOnSurface = mix(start.inverseOnSurface, stop.inverseOnSurface),
        error = mix(start.error, stop.error),
        onError = mix(start.onError, stop.onError),
        errorContainer = mix(start.errorContainer, stop.errorContainer),
        onErrorContainer = mix(start.onErrorContainer, stop.onErrorContainer),
        outline = mix(start.outline, stop.outline),
        outlineVariant = mix(start.outlineVariant, stop.outlineVariant),
        scrim = mix(start.scrim, stop.scrim),
        surfaceBright = mix(start.surfaceBright, stop.surfaceBright),
        surfaceDim = mix(start.surfaceDim, stop.surfaceDim),
        surfaceContainer = mix(start.surfaceContainer, stop.surfaceContainer),
        surfaceContainerHigh = mix(start.surfaceContainerHigh, stop.surfaceContainerHigh),
        surfaceContainerHighest = mix(start.surfaceContainerHighest, stop.surfaceContainerHighest),
        surfaceContainerLow = mix(start.surfaceContainerLow, stop.surfaceContainerLow),
        surfaceContainerLowest = mix(start.surfaceContainerLowest, stop.surfaceContainerLowest)
    )
}

private val NoIndication: IndicationNodeFactory = object : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): Modifier.Node = EmptyIndicationNode()

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = javaClass.hashCode()
}

private class EmptyIndicationNode : Modifier.Node()
