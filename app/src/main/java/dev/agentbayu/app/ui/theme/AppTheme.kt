package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.agentbayu.app.AppGraph

@Composable
fun AgentBayuAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val themeMode by settings.themeMode.collectAsState()
    val systemDarkness by animateFloatAsState(
        targetValue = if (isSystemInDarkTheme()) 1f else 0f,
        animationSpec = AgentBayuMotion.themeMorph,
        label = "systemDarkness"
    )
    val modePosition by animateFloatAsState(
        targetValue = themeMode.ordinal.toFloat(),
        animationSpec = AgentBayuMotion.themeMorph,
        label = "themeModePosition"
    )
    val scrub = remember { ThemeScrub() }
    val position = scrub.read() ?: modePosition
    val darkFraction = interpolateDarkness(themeDarknessStops(systemDarkness), position)
    CompositionLocalProvider(LocalThemeScrub provides scrub) {
        AgentBayuTheme(darkFraction = darkFraction, content = content)
    }
}
