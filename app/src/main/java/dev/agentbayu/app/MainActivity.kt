package dev.agentbayu.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.agentbayu.app.ai.CrashLog
import dev.agentbayu.app.domain.tools.PermissionKind
import dev.agentbayu.app.platform.NotificationAccess
import dev.agentbayu.app.platform.files.AllFilesAccess
import dev.agentbayu.app.platform.tasks.EXTRA_TASK_ID
import dev.agentbayu.app.ui.chat.ChatRoute
import dev.agentbayu.app.ui.components.AmbientBackground
import dev.agentbayu.app.ui.components.CardPager
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.GlassOverlayController
import dev.agentbayu.app.ui.components.GlassOverlayHost
import dev.agentbayu.app.ui.components.GlassTabsProgress
import dev.agentbayu.app.ui.components.LocalGlassOverlay
import dev.agentbayu.app.ui.components.PageStackProgress
import dev.agentbayu.app.ui.components.ToolApprovalSheet
import dev.agentbayu.app.ui.history.HistoryDrawer
import dev.agentbayu.app.ui.history.HistoryDrawerState
import dev.agentbayu.app.ui.history.rememberHistoryDrawerState
import dev.agentbayu.app.ui.nav.AgentBayuBottomBar
import dev.agentbayu.app.ui.nav.AgentBayuDestination
import dev.agentbayu.app.ui.nav.AppPageController
import dev.agentbayu.app.ui.nav.AppPageHost
import dev.agentbayu.app.ui.onboarding.OnboardingRoute
import dev.agentbayu.app.ui.settings.SettingsRoute
import dev.agentbayu.app.ui.tasks.TasksRoute
import dev.agentbayu.app.ui.theme.AgentBayuAppTheme
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingTaskId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTaskId.value = intent?.getStringExtra(EXTRA_TASK_ID)
        splashScreen.setKeepOnScreenCondition { !AppGraph.readiness.value }
        AppGraph.warmUp(applicationContext)
        installCrashLogger(applicationContext)
        setContent {
            val ready by AppGraph.readiness.collectAsState()
            if (ready) {
                AgentBayuAppTheme {
                    SystemBarAppearance()
                    AgentBayuApp(pendingTaskId = pendingTaskId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TASK_ID)?.let { pendingTaskId.value = it }
    }

    private fun installCrashLogger(context: android.content.Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { CrashLog.record(context, error) }
            previous?.uncaughtException(thread, error)
        }
    }
}

@Composable
private fun SystemBarAppearance() {
    val darkTheme = LocalDarkTheme.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    SideEffect {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
private fun AgentBayuApp(pendingTaskId: MutableStateFlow<String?>) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val onboardingVisible by settings.onboardingVisible.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = remember { MutableStateFlow<String?>(null) }
    val pendingMessage by messages.collectAsState()
    val taskDeepLink by pendingTaskId.collectAsState()
    val ambientBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(ambientBackdrop, contentBackdrop)
    val overlayController = remember { GlassOverlayController() }
    val pageController = remember { AppPageController() }
    val pageProgress = remember { PageStackProgress() }
    val tabProgress = remember { GlassTabsProgress() }
    val historyDrawer = rememberHistoryDrawerState()
    val destinations = AgentBayuDestination.entries
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val onMessage: (String) -> Unit = { message -> messages.value = message }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            messages.value = null
        }
    }

    LaunchedEffect(taskDeepLink) {
        val taskId = taskDeepLink ?: return@LaunchedEffect
        pendingTaskId.value = null
        val task = AppGraph.tasks(context).find(taskId) ?: return@LaunchedEffect
        selectedTab = destinations.indexOf(AgentBayuDestination.TASKS)
        pageController.closeAll()
        pageController.openTaskDetail(task.id, task.listId, task.parentId)
    }

    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

    AmbientBackground(
        modifier = Modifier.fillMaxSize(),
        canvasModifier = Modifier.layerBackdrop(ambientBackdrop)
    ) {
        CompositionLocalProvider(LocalGlassOverlay provides overlayController) {
            if (onboardingVisible) {
                CompositionLocalProvider(
                    LocalGlassBackdrop provides ambientBackdrop,
                    LocalScreenInsets provides WindowInsets.systemBars.asPaddingValues()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OnboardingRoute(
                            onFinish = settings::completeOnboarding,
                            onMessage = onMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        )
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        CompositionLocalProvider(LocalGlassBackdrop provides chromeBackdrop) {
                            AgentBayuBottomBar(
                                selectedIndex = selectedTab,
                                onSelect = { index ->
                                    pageController.closeAll()
                                    selectedTab = index
                                },
                                progress = tabProgress,
                                modifier = Modifier
                                    .graphicsLayer {
                                        val cover = pageProgress.value().coerceIn(0f, 1f)
                                        alpha = 1f - cover
                                        translationY = size.height * cover
                                    }
                                    .drawWithContent {
                                        if (pageProgress.value() < BASE_COVER_LIMIT) {
                                            drawContent()
                                        }
                                    },
                                windowInsets = NavigationBarDefaults.windowInsets
                            )
                        }
                    }
                ) { innerPadding ->
                    val pageTopInset = innerPadding.calculateTopPadding()
                    val pageBottomInset =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val pageInsets = remember(pageTopInset, pageBottomInset) {
                        PaddingValues(top = pageTopInset, bottom = pageBottomInset)
                    }
                    CompositionLocalProvider(
                        LocalGlassBackdrop provides ambientBackdrop,
                        LocalScreenInsets provides innerPadding
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .layerBackdrop(contentBackdrop)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val cover = pageProgress.value().coerceIn(0f, 1f)
                                        alpha = 1f - cover
                                        translationX = -size.width * BASE_PARALLAX * cover
                                    }
                                    .drawWithContent {
                                        if (pageProgress.value() < BASE_COVER_LIMIT) {
                                            drawContent()
                                        }
                                    }
                            ) {
                                CardPager(
                                    pageCount = destinations.size,
                                    progress = { tabProgress.value() },
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    TabContent(
                                        destination = destinations[page],
                                        controller = pageController,
                                        onMessage = onMessage,
                                        drawer = historyDrawer
                                    )
                                }
                            }

                            CompositionLocalProvider(LocalScreenInsets provides pageInsets) {
                                AppPageHost(
                                    controller = pageController,
                                    progress = pageProgress,
                                    onMessage = onMessage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            HistoryDrawer(state = historyDrawer, onMessage = onMessage)

            ToolApprovalHost()

            PermissionHost(onMessage = onMessage)

            GlassOverlayHost(
                controller = overlayController,
                backdrop = chromeBackdrop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ToolApprovalHost() {
    val context = LocalContext.current
    val approvals = remember(context) { AppGraph.approvals(context) }
    val pending by approvals.pending.collectAsState()
    pending?.let { request ->
        ToolApprovalSheet(request = request, onDecision = approvals::resolve)
    }
}

@Composable
private fun PermissionHost(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val requests = remember(context) { AppGraph.permissions(context) }
    val pending by requests.pending.collectAsState()
    val settingsUnavailable = stringResource(R.string.dialog_settings_unavailable)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> requests.resolve(granted) }
    var shown by remember { mutableStateOf(PermissionKind.STORAGE) }

    LaunchedEffect(pending) {
        pending?.let { ask -> shown = ask.kind }
    }

    GlassDialog(
        visible = pending != null,
        title = stringResource(R.string.permission_ask_title),
        body = stringResource(permissionBody(shown)),
        confirmLabel = stringResource(R.string.permission_ask_allow),
        onConfirm = {
            val opened = when (shown) {
                PermissionKind.STORAGE -> AllFilesAccess.open(context)

                PermissionKind.NOTIFICATIONS ->
                    if (NotificationAccess.needsRuntimeRequest(context)) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@GlassDialog
                    } else {
                        NotificationAccess.openSettings(context)
                    }

                PermissionKind.EXACT_ALARMS -> NotificationAccess.openExactAlarmSettings(context)
            }
            if (!opened) onMessage(settingsUnavailable)
            requests.resolve(opened)
        },
        dismissLabel = stringResource(R.string.dialog_later),
        onDismiss = { requests.resolve(false) }
    )
}

private fun permissionBody(kind: PermissionKind): Int = when (kind) {
    PermissionKind.STORAGE -> R.string.permission_ask_storage
    PermissionKind.NOTIFICATIONS -> R.string.permission_ask_notifications
    PermissionKind.EXACT_ALARMS -> R.string.permission_ask_alarms
}

@Composable
private fun TabContent(
    destination: AgentBayuDestination,
    controller: AppPageController,
    onMessage: (String) -> Unit,
    drawer: HistoryDrawerState
) {
    when (destination) {
        AgentBayuDestination.CHAT -> ChatRoute(
            onMessage = onMessage,
            onOpenProviders = { controller.openProviders() },
            drawer = drawer,
            modifier = Modifier.fillMaxSize()
        )

        AgentBayuDestination.TASKS -> TasksRoute(
            onMessage = onMessage,
            onOpenTask = { taskId, listId, parentId ->
                controller.openTaskDetail(taskId, listId, parentId)
            },
            modifier = Modifier.fillMaxSize()
        )

        AgentBayuDestination.SETTINGS -> SettingsRoute(
            onMessage = onMessage,
            onOpenProviders = { controller.openProviders() },
            onOpenCustomPrompt = { controller.openCustomPrompt() },
            onOpenLogs = { controller.openLogs() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val BASE_PARALLAX = 0.25f
private const val BASE_COVER_LIMIT = 0.999f
