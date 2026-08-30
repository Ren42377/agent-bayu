package dev.agentbayu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.agentbayu.app.ui.components.AmbientBackground
import dev.agentbayu.app.ui.nav.AgentBayuBottomBar
import dev.agentbayu.app.ui.nav.AgentBayuDestination
import dev.agentbayu.app.ui.nav.AgentBayuNavHost
import dev.agentbayu.app.ui.theme.AgentBayuTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentBayuTheme {
                AgentBayuApp()
            }
        }
    }
}

@Composable
private fun AgentBayuApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = remember { MutableStateFlow<String?>(null) }
    val pendingMessage by messages.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            messages.value = null
        }
    }

    AmbientBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                AgentBayuBottomBar(
                    currentRoute = currentRoute.orEmpty(),
                    onSelect = { destination -> navigateTo(navController, destination) },
                    windowInsets = if (keyboardVisible) {
                        WindowInsets(0, 0, 0, 0)
                    } else {
                        NavigationBarDefaults.windowInsets
                    }
                )
            }
        ) { innerPadding ->
            AgentBayuNavHost(
                navController = navController,
                onMessage = { message -> messages.value = message },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

private fun navigateTo(controller: NavHostController, destination: AgentBayuDestination) {
    controller.navigate(destination.route) {
        popUpTo(AgentBayuDestination.CHAT.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
