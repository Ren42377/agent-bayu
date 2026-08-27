package dev.agentbayu.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.agentbayu.app.ui.chat.ChatRoute
import dev.agentbayu.app.ui.settings.SettingsRoute
import dev.agentbayu.app.ui.setup.SetupRoute

@Composable
fun AgentBayuNavHost(
    navController: NavHostController,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AgentBayuDestination.CHAT.route,
        modifier = modifier
    ) {
        composable(AgentBayuDestination.CHAT.route) {
            ChatRoute(onMessage = onMessage)
        }
        composable(AgentBayuDestination.SETUP.route) {
            SetupRoute(onMessage = onMessage)
        }
        composable(AgentBayuDestination.SETTINGS.route) {
            SettingsRoute(onMessage = onMessage)
        }
    }
}
