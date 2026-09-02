package dev.agentbayu.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.BuildConfig

@Composable
fun SettingsRoute(
    onMessage: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val useScreenContext by settings.useScreenContext.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    SettingsScreen(
        versionName = BuildConfig.VERSION_NAME,
        useScreenContext = useScreenContext,
        themeMode = themeMode,
        onThemeModeChange = settings::setThemeMode,
        onScreenContextChange = settings::setUseScreenContext,
        onOpenProviders = onOpenProviders,
        onOpenLogs = onOpenLogs,
        onOpenOnboarding = settings::showOnboarding,
        modifier = modifier
    )
}
