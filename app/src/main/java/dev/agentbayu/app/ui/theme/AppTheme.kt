package dev.agentbayu.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import dev.agentbayu.app.AppGraph
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AgentBayuAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val themeMode by settings.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val target = themeTargetDarkness(themeMode, systemDark)
    val scrub = remember { ThemeScrub() }
    val darkness = remember { Animatable(target) }
    LaunchedEffect(scrub, target, systemDark) {
        val stops = themeDarknessStops(if (systemDark) 1f else 0f)
        snapshotFlow { scrub.read() }.collectLatest { position ->
            if (position == null) {
                darkness.animateTo(target, ThemeMorphSpec)
            } else {
                darkness.snapTo(interpolateDarkness(stops, position))
            }
        }
    }
    CompositionLocalProvider(
        LocalThemeScrub provides scrub,
        LocalThemeTargetDarkness provides target
    ) {
        AgentBayuTheme(darkFraction = darkness.value, content = content)
    }
}

private val ThemeMorphSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
