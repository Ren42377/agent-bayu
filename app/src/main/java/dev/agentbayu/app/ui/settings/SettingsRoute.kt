package dev.agentbayu.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.BuildConfig
import dev.agentbayu.app.R
import dev.agentbayu.app.platform.files.AllFilesAccess

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
    val toolApprovalMode by settings.toolApprovalMode.collectAsState()
    val storageUnavailable = stringResource(R.string.settings_storage_unavailable)
    var storageGranted by remember(context) { mutableStateOf(AllFilesAccess.granted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageGranted = AllFilesAccess.granted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreen(
        versionName = BuildConfig.VERSION_NAME,
        useScreenContext = useScreenContext,
        themeMode = themeMode,
        toolApprovalMode = toolApprovalMode,
        storageGranted = storageGranted,
        onThemeModeChange = settings::setThemeMode,
        onToolApprovalModeChange = settings::setToolApprovalMode,
        onScreenContextChange = settings::setUseScreenContext,
        onOpenProviders = onOpenProviders,
        onOpenLogs = onOpenLogs,
        onOpenStorageSettings = {
            if (!AllFilesAccess.open(context)) {
                onMessage(storageUnavailable)
            }
        },
        modifier = modifier
    )
}
