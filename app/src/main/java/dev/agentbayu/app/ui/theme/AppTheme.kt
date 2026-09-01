package dev.agentbayu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.platform.ThemeMode

@Composable
fun AgentBayuAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val themeMode by settings.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    AgentBayuTheme(darkTheme = darkTheme, content = content)
}
