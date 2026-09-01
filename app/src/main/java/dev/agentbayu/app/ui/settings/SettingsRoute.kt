package dev.agentbayu.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.BuildConfig
import dev.agentbayu.app.R

@Composable
fun SettingsRoute(
    onMessage: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chat = remember(context) { AppGraph.chat(context) }
    val settings = remember(context) { AppGraph.settings(context) }
    val useScreenContext by settings.useScreenContext.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    val clearedMessage = stringResource(R.string.settings_cleared)
    SettingsScreen(
        versionName = BuildConfig.VERSION_NAME,
        useScreenContext = useScreenContext,
        themeMode = themeMode,
        onThemeModeChange = settings::setThemeMode,
        onScreenContextChange = settings::setUseScreenContext,
        onClearConversation = {
            chat.clear()
            onMessage(clearedMessage)
        },
        onOpenProviders = onOpenProviders,
        onOpenLogs = onOpenLogs,
        onOpenOnboarding = settings::showOnboarding,
        modifier = modifier
    )
}
