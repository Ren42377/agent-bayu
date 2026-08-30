package dev.agentbayu.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.agentbayu.app.ui.ai.AiConnectionEditRoute
import dev.agentbayu.app.ui.ai.AiDeviceCodeRoute
import dev.agentbayu.app.ui.ai.AiProvidersRoute
import dev.agentbayu.app.ui.ai.AiUsageRoute
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
            ChatRoute(
                onMessage = onMessage,
                onOpenProviders = { navController.navigate(AiRoutes.PROVIDERS) }
            )
        }
        composable(AgentBayuDestination.SETUP.route) {
            SetupRoute(onMessage = onMessage)
        }
        composable(AgentBayuDestination.SETTINGS.route) {
            SettingsRoute(
                onMessage = onMessage,
                onOpenProviders = { navController.navigate(AiRoutes.PROVIDERS) },
                onOpenUsage = { navController.navigate(AiRoutes.USAGE) }
            )
        }
        composable(AiRoutes.PROVIDERS) {
            AiProvidersRoute(
                onBack = { navController.popBackStack() },
                onEdit = { connectionId -> navController.navigate(AiRoutes.connection(connectionId)) },
                onMessage = onMessage
            )
        }
        composable(
            route = AiRoutes.CONNECTION_PATTERN,
            arguments = listOf(
                navArgument(AiRoutes.CONNECTION_ARG) {
                    type = NavType.StringType
                    defaultValue = AiRoutes.NEW_CONNECTION
                }
            )
        ) { entry ->
            val argument = entry.arguments?.getString(AiRoutes.CONNECTION_ARG)
            AiConnectionEditRoute(
                connectionId = argument?.takeIf { it != AiRoutes.NEW_CONNECTION },
                onBack = { navController.popBackStack() },
                onStartLogin = { connectionId ->
                    navController.navigate(AiRoutes.deviceCode(connectionId)) {
                        popUpTo(AiRoutes.PROVIDERS)
                    }
                },
                onMessage = onMessage
            )
        }
        composable(
            route = AiRoutes.DEVICE_CODE_PATTERN,
            arguments = listOf(
                navArgument(AiRoutes.CONNECTION_ARG) { type = NavType.StringType }
            )
        ) { entry ->
            val argument = entry.arguments?.getString(AiRoutes.CONNECTION_ARG).orEmpty()
            AiDeviceCodeRoute(
                connectionId = argument,
                onBack = { navController.popBackStack() },
                onMessage = onMessage
            )
        }
        composable(AiRoutes.USAGE) {
            AiUsageRoute(
                onBack = { navController.popBackStack() },
                onMessage = onMessage
            )
        }
    }
}

object AiRoutes {
    const val PROVIDERS = "ai/providers"
    const val USAGE = "ai/usage"
    const val CONNECTION_ARG = "connectionId"
    const val NEW_CONNECTION = "new"
    const val CONNECTION_PATTERN = "ai/connection/{" + CONNECTION_ARG + "}"
    const val DEVICE_CODE_PATTERN = "ai/device-code/{" + CONNECTION_ARG + "}"

    fun connection(connectionId: String?): String =
        "ai/connection/" + (connectionId ?: NEW_CONNECTION)

    fun deviceCode(connectionId: String): String = "ai/device-code/" + connectionId
}
