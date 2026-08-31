package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import dev.agentbayu.app.ui.components.GlassOverlay
import dev.agentbayu.app.ui.components.GlassOverlayPresentation

@Stable
class AiSheetController {

    internal val stack = mutableStateListOf<AiSheet>()

    val isVisible: Boolean
        get() = stack.isNotEmpty()

    fun openProviders() {
        stack.clear()
        stack.add(AiSheet.Providers)
    }

    fun openLogs() {
        stack.clear()
        stack.add(AiSheet.Logs)
    }

    fun openConnection(connectionId: String?) {
        stack.add(AiSheet.Connection(connectionId))
    }

    fun openDeviceCode(connectionId: String) {
        if (stack.lastOrNull() is AiSheet.Connection) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(AiSheet.DeviceCode(connectionId))
    }

    fun back() {
        if (stack.isNotEmpty()) {
            stack.removeAt(stack.lastIndex)
        }
    }

    fun closeAll() {
        stack.clear()
    }
}

internal sealed interface AiSheet {

    data object Providers : AiSheet

    data object Logs : AiSheet

    data class Connection(val connectionId: String?) : AiSheet

    data class DeviceCode(val connectionId: String) : AiSheet
}

@Composable
fun AiSheetHost(controller: AiSheetController, onMessage: (String) -> Unit) {
    controller.stack.forEachIndexed { index, sheet ->
        key(index) {
            GlassOverlay(
                presentation = GlassOverlayPresentation.SHEET,
                onDismiss = { controller.back() }
            ) {
                when (sheet) {
                    AiSheet.Providers -> AiProvidersRoute(
                        onBack = { controller.back() },
                        onEdit = { connectionId -> controller.openConnection(connectionId) },
                        onMessage = onMessage
                    )

                    AiSheet.Logs -> AiLogsRoute(
                        onBack = { controller.back() },
                        onMessage = onMessage
                    )

                    is AiSheet.Connection -> AiConnectionEditRoute(
                        connectionId = sheet.connectionId,
                        onBack = { controller.back() },
                        onStartLogin = { connectionId -> controller.openDeviceCode(connectionId) },
                        onMessage = onMessage
                    )

                    is AiSheet.DeviceCode -> AiDeviceCodeRoute(
                        connectionId = sheet.connectionId,
                        onBack = { controller.back() },
                        onMessage = onMessage
                    )
                }
            }
        }
    }
}
