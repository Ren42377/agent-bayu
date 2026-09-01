package dev.agentbayu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.agentbayu.app.ui.chat.ChatRoute
import dev.agentbayu.app.ui.components.AmbientBackground
import dev.agentbayu.app.ui.components.CardPager
import dev.agentbayu.app.ui.components.GlassOverlayController
import dev.agentbayu.app.ui.components.GlassOverlayHost
import dev.agentbayu.app.ui.components.GlassTabsProgress
import dev.agentbayu.app.ui.components.LocalGlassOverlay
import dev.agentbayu.app.ui.components.PageStackProgress
import dev.agentbayu.app.ui.nav.AgentBayuBottomBar
import dev.agentbayu.app.ui.nav.AgentBayuDestination
import dev.agentbayu.app.ui.nav.AppPageController
import dev.agentbayu.app.ui.nav.AppPageHost
import dev.agentbayu.app.ui.settings.SettingsRoute
import dev.agentbayu.app.ui.theme.AgentBayuAppTheme
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentBayuAppTheme {
                SystemBarAppearance()
                AgentBayuApp()
            }
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
private fun AgentBayuApp() {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val onboardingVisible by settings.onboardingVisible.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = remember { MutableStateFlow<String?>(null) }
    val pendingMessage by messages.collectAsState()
    val ambientBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chromeBackdrop = rememberCombinedBackdrop(ambientBackdrop, contentBackdrop)
    val overlayController = remember { GlassOverlayController() }
    val pageController = remember { AppPageController() }
    val pageProgress = remember { PageStackProgress() }
    val tabProgress = remember { GlassTabsProgress() }
    val destinations = AgentBayuDestination.entries
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val onMessage: (String) -> Unit = { message -> messages.value = message }

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            messages.value = null
        }
    }

    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

    AmbientBackground(
        modifier = Modifier.fillMaxSize(),
        canvasModifier = Modifier.layerBackdrop(ambientBackdrop)
    ) {
        CompositionLocalProvider(LocalGlassOverlay provides overlayController) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    CompositionLocalProvider(LocalGlassBackdrop provides chromeBackdrop) {
                        AgentBayuBottomBar(
                            selectedIndex = selectedTab,
                            onSelect = { index ->
                                pageController.closeAll()
                                selectedTab = index
                            },
                            progress = tabProgress,
                            windowInsets = if (keyboardVisible) {
                                WindowInsets(0, 0, 0, 0)
                            } else {
                                NavigationBarDefaults.windowInsets
                            }
                        )
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(
                    LocalGlassBackdrop provides ambientBackdrop,
                    LocalScreenInsets provides innerPadding
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                                when (destinations[page]) {
                                    AgentBayuDestination.CHAT -> ChatRoute(
                                        onMessage = onMessage,
                                        onOpenProviders = { pageController.openProviders() },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    AgentBayuDestination.SETTINGS -> SettingsRoute(
                                        onMessage = onMessage,
                                        onOpenProviders = { pageController.openProviders() },
                                        onOpenLogs = { pageController.openLogs() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        AppPageHost(
                            controller = pageController,
                            progress = pageProgress,
                            onboardingVisible = onboardingVisible,
                            onOnboardingFinish = settings::completeOnboarding,
                            onMessage = onMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            GlassOverlayHost(
                controller = overlayController,
                backdrop = chromeBackdrop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val BASE_PARALLAX = 0.25f
private const val BASE_COVER_LIMIT = 0.999f
