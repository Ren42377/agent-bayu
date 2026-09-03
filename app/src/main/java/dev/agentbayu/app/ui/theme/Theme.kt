package dev.agentbayu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalDarkTheme = staticCompositionLocalOf { false }

private val lightColors: ColorScheme = lightColorScheme(
    primary = AppleBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF003F8A),
    secondary = AppleIndigoLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E4FF),
    onSecondaryContainer = Color(0xFF262378),
    tertiary = AppleTealLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2F5FA),
    onTertiaryContainer = Color(0xFF004550),
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
    inversePrimary = AppleBlueDark,
    scrim = ScrimBlack
)

private val darkColors: ColorScheme = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003875),
    onPrimaryContainer = Color(0xFFDCEBFF),
    secondary = AppleIndigoDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF23206B),
    onSecondaryContainer = Color(0xFFE5E4FF),
    tertiary = AppleTealDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF003F4A),
    onTertiaryContainer = Color(0xFFD2F5FA),
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
    inversePrimary = AppleBlueLight,
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
        LocalIndication provides GlassIndication(darkTheme),
        LocalDarkTheme provides darkTheme,
        LocalGlassStyle provides glassStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AgentBayuTypography,
            shapes = AgentBayuShapes,
            content = content
        )
    }
}
