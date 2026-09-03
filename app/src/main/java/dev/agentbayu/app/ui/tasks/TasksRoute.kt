package dev.agentbayu.app.ui.tasks

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.completedTasks
import dev.agentbayu.app.domain.tasks.pendingRows
import dev.agentbayu.app.ui.components.GlassDialog

@Composable
fun TasksRoute(
    onMessage: (String) -> Unit,
    onOpenTask: (String?, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.tasks(context) }
    val alarms = remember(context) { AppGraph.taskAlarms(context) }
    val lists by store.lists.collectAsState()
    val tasks by store.tasks.collectAsState()
    val sort by store.sort.collectAsState()
    val activeId by store.activeListId.collectAsState()
    val defaultListTitle = stringResource(R.string.tasks_list_default)
    val deletedMessage = stringResource(R.string.tasks_deleted)
    val settingsUnavailable = stringResource(R.string.dialog_settings_unavailable)
    val permissionDenied = stringResource(R.string.tasks_permission_denied)

    var notificationsAllowed by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    var exactAlarmsAllowed by remember { mutableStateOf(alarms.canScheduleExact()) }
    var listMenuOpen by remember { mutableStateOf(false) }
    var newListOpen by remember { mutableStateOf(false) }
    var renameListOpen by remember { mutableStateOf(false) }
    var deleteListOpen by remember { mutableStateOf(false) }
    var clearCompletedOpen by remember { mutableStateOf(false) }
    var rowMenuTask by remember { mutableStateOf<TaskItem?>(null) }
    var moveTargetTask by remember { mutableStateOf<TaskItem?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = hasNotificationPermission(context)
                exactAlarmsAllowed = alarms.canScheduleExact()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        if (!granted) {
            onMessage(permissionDenied)
        }
    }

    LaunchedEffect(lists.isEmpty()) {
        if (lists.isEmpty()) {
            store.setActiveList(store.createList(defaultListTitle))
        }
    }

    val activeList = lists.firstOrNull { it.id == activeId } ?: lists.firstOrNull()
    val listId = activeList?.id
    val rows = remember(tasks, listId, sort) {
        if (listId == null) emptyList() else pendingRows(tasks, listId, sort)
    }
    val completed = remember(tasks, listId) {
        if (listId == null) emptyList() else completedTasks(tasks, listId)
    }

    TasksScreen(
        lists = lists,
        activeList = activeList,
        rows = rows,
        completed = completed,
        sort = sort,
        notificationsAllowed = notificationsAllowed,
        exactAlarmsAllowed = exactAlarmsAllowed,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!openNotificationSettings(context)) {
                onMessage(settingsUnavailable)
            }
        },
        onRequestExactAlarms = {
            if (!openExactAlarmSettings(context)) {
                onMessage(settingsUnavailable)
            }
        },
        onSelectList = store::setActiveList,
        onListMenu = { listMenuOpen = true },
        onSortChange = store::setSort,
        onAddTask = { listId?.let { onOpenTask(null, it, null) } },
        onOpenTask = { task -> onOpenTask(task.id, task.listId, task.parentId) },
        onToggleCompleted = { task -> store.setCompleted(task.id, !task.completed) },
        onToggleStarred = { task -> store.setStarred(task.id, !task.starred) },
        onRowMenu = { task -> rowMenuTask = task },
        modifier = modifier
    )

    TasksMenus(
        store = store,
        lists = lists,
        activeList = activeList,
        completedCount = completed.size,
        listMenuOpen = listMenuOpen,
        onListMenuDismiss = { listMenuOpen = false },
        onNewList = { newListOpen = true },
        onRenameList = { renameListOpen = true },
        onDeleteList = { deleteListOpen = true },
        onClearCompleted = { clearCompletedOpen = true },
        newListOpen = newListOpen,
        onNewListDismiss = { newListOpen = false },
        renameListOpen = renameListOpen,
        onRenameListDismiss = { renameListOpen = false },
        rowMenuTask = rowMenuTask,
        onRowMenuDismiss = { rowMenuTask = null },
        moveTargetTask = moveTargetTask,
        onMoveTargetDismiss = { moveTargetTask = null },
        onMoveToList = { task -> moveTargetTask = task },
        onDeleted = { onMessage(deletedMessage) }
    )

    GlassDialog(
        visible = deleteListOpen && activeList != null,
        title = stringResource(R.string.tasks_list_delete),
        body = stringResource(R.string.tasks_list_delete_body),
        confirmLabel = stringResource(R.string.tasks_list_delete),
        onConfirm = {
            deleteListOpen = false
            activeList?.let { store.removeList(it.id) }
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { deleteListOpen = false }
    )

    GlassDialog(
        visible = clearCompletedOpen && activeList != null,
        title = stringResource(R.string.tasks_clear_completed),
        body = stringResource(R.string.tasks_clear_completed_body),
        confirmLabel = stringResource(R.string.tasks_clear_completed),
        onConfirm = {
            clearCompletedOpen = false
            activeList?.let { store.clearCompleted(it.id) }
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { clearCompletedOpen = false }
    )
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openNotificationSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
}

private fun openExactAlarmSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
}
