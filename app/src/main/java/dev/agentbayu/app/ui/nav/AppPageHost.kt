package dev.agentbayu.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.agentbayu.app.ui.ai.AiBrowserLoginRoute
import dev.agentbayu.app.ui.ai.AiConnectionEditRoute
import dev.agentbayu.app.ui.ai.AiDeviceCodeRoute
import dev.agentbayu.app.ui.ai.AiLogsRoute
import dev.agentbayu.app.ui.ai.AiProvidersRoute
import dev.agentbayu.app.ui.components.PageStackHost
import dev.agentbayu.app.ui.components.PageStackProgress
import dev.agentbayu.app.ui.onboarding.OnboardingRoute
import dev.agentbayu.app.ui.tasks.TaskDetailRoute

@Composable
fun AppPageHost(
    controller: AppPageController,
    progress: PageStackProgress,
    onboardingVisible: Boolean,
    onOnboardingFinish: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = buildList<AppPage> {
        if (onboardingVisible) {
            add(AppPage.Onboarding)
        }
        addAll(controller.stack)
    }
    PageStackHost(
        pages = pages,
        progress = progress,
        onDismiss = { page ->
            if (page == AppPage.Onboarding) {
                onOnboardingFinish()
            } else {
                controller.back()
            }
        },
        modifier = modifier
    ) { page ->
        when (page) {
            AppPage.Onboarding -> OnboardingRoute(
                onFinish = onOnboardingFinish,
                onMessage = onMessage
            )

            AppPage.Providers -> AiProvidersRoute(
                onBack = controller::back,
                onEdit = controller::openConnection,
                onMessage = onMessage
            )

            AppPage.Logs -> AiLogsRoute(
                onBack = controller::back,
                onMessage = onMessage
            )

            is AppPage.Connection -> AiConnectionEditRoute(
                connectionId = page.connectionId,
                onBack = controller::back,
                onStartLogin = controller::openDeviceCode,
                onStartBrowserLogin = controller::openBrowserLogin,
                onMessage = onMessage
            )

            is AppPage.DeviceCode -> AiDeviceCodeRoute(
                connectionId = page.connectionId,
                onBack = controller::back,
                onMessage = onMessage
            )

            is AppPage.BrowserLogin -> AiBrowserLoginRoute(
                connectionId = page.connectionId,
                onBack = controller::back,
                onMessage = onMessage
            )

            is AppPage.TaskDetail -> TaskDetailRoute(
                taskId = page.taskId,
                listId = page.listId,
                parentId = page.parentId,
                onMessage = onMessage,
                onBack = controller::back
            )
        }
    }
}
