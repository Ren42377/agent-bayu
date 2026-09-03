package dev.agentbayu.app.ui.nav

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf

sealed interface AppPage {

    data object Providers : AppPage

    data object History : AppPage

    data object Logs : AppPage

    data class Connection(val connectionId: String?) : AppPage

    data class DeviceCode(val connectionId: String) : AppPage

    data class BrowserLogin(val connectionId: String) : AppPage

    data class TaskDetail(val taskId: String?, val listId: String, val parentId: String? = null) :
        AppPage
}

@Stable
class AppPageController {

    internal val stack = mutableStateListOf<AppPage>()

    fun openProviders() {
        stack.clear()
        stack.add(AppPage.Providers)
    }

    fun openHistory() {
        stack.clear()
        stack.add(AppPage.History)
    }

    fun openLogs() {
        stack.clear()
        stack.add(AppPage.Logs)
    }

    fun openConnection(connectionId: String?) {
        stack.add(AppPage.Connection(connectionId))
    }

    fun openDeviceCode(connectionId: String) {
        if (stack.lastOrNull() is AppPage.Connection) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(AppPage.DeviceCode(connectionId))
    }

    fun openBrowserLogin(connectionId: String) {
        if (stack.lastOrNull() is AppPage.Connection) {
            stack.removeAt(stack.lastIndex)
        }
        stack.add(AppPage.BrowserLogin(connectionId))
    }

    fun openTaskDetail(taskId: String?, listId: String, parentId: String? = null) {
        stack.add(AppPage.TaskDetail(taskId, listId, parentId))
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
