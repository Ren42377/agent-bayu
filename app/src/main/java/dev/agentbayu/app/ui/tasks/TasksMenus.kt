package dev.agentbayu.app.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskList
import dev.agentbayu.app.domain.tasks.TaskStore

@Composable
internal fun TasksMenus(
    store: TaskStore,
    lists: List<TaskList>,
    activeList: TaskList?,
    completedCount: Int,
    listMenuOpen: Boolean,
    onListMenuDismiss: () -> Unit,
    onNewList: () -> Unit,
    onRenameList: () -> Unit,
    onDeleteList: () -> Unit,
    onClearCompleted: () -> Unit,
    newListOpen: Boolean,
    onNewListDismiss: () -> Unit,
    renameListOpen: Boolean,
    onRenameListDismiss: () -> Unit,
    rowMenuTask: TaskItem?,
    onRowMenuDismiss: () -> Unit,
    moveTargetTask: TaskItem?,
    onMoveTargetDismiss: () -> Unit,
    onMoveToList: (TaskItem) -> Unit,
    onDeleted: () -> Unit
) {
    TaskActionSheet(
        visible = listMenuOpen,
        title = activeList?.title ?: stringResource(R.string.tasks_title),
        actions = listMenuActions(
            hasList = activeList != null,
            completedCount = completedCount,
            onNewList = onNewList,
            onRenameList = onRenameList,
            onDeleteList = onDeleteList,
            onClearCompleted = onClearCompleted
        ),
        onDismiss = onListMenuDismiss
    )

    TaskTextDialog(
        visible = newListOpen,
        title = stringResource(R.string.tasks_list_new),
        hint = stringResource(R.string.tasks_list_name_hint),
        initialValue = "",
        confirmLabel = stringResource(R.string.tasks_detail_save),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { title ->
            onNewListDismiss()
            store.setActiveList(store.createList(title))
        },
        onDismiss = onNewListDismiss
    )

    TaskTextDialog(
        visible = renameListOpen && activeList != null,
        title = stringResource(R.string.tasks_list_rename),
        hint = stringResource(R.string.tasks_list_name_hint),
        initialValue = activeList?.title.orEmpty(),
        confirmLabel = stringResource(R.string.tasks_detail_save),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { title ->
            onRenameListDismiss()
            activeList?.let { store.renameList(it.id, title) }
        },
        onDismiss = onRenameListDismiss
    )

    TaskActionSheet(
        visible = rowMenuTask != null,
        title = rowMenuTask?.title.orEmpty(),
        actions = rowMenuActions(
            store = store,
            task = rowMenuTask,
            listCount = lists.size,
            onMoveToList = onMoveToList,
            onDeleted = onDeleted
        ),
        onDismiss = onRowMenuDismiss
    )

    TaskActionSheet(
        visible = moveTargetTask != null,
        title = stringResource(R.string.tasks_move_to_list),
        actions = lists
            .filter { it.id != moveTargetTask?.listId }
            .map { target ->
                TaskAction(label = target.title) {
                    moveTargetTask?.let { store.moveToList(it.id, target.id) }
                }
            },
        onDismiss = onMoveTargetDismiss
    )
}

@Composable
private fun listMenuActions(
    hasList: Boolean,
    completedCount: Int,
    onNewList: () -> Unit,
    onRenameList: () -> Unit,
    onDeleteList: () -> Unit,
    onClearCompleted: () -> Unit
): List<TaskAction> {
    val actions = mutableListOf(
        TaskAction(label = stringResource(R.string.tasks_list_new), onClick = onNewList)
    )
    if (hasList) {
        actions += TaskAction(
            label = stringResource(R.string.tasks_list_rename),
            onClick = onRenameList
        )
        if (completedCount > 0) {
            actions += TaskAction(
                label = stringResource(R.string.tasks_clear_completed),
                onClick = onClearCompleted
            )
        }
        actions += TaskAction(
            label = stringResource(R.string.tasks_list_delete),
            destructive = true,
            onClick = onDeleteList
        )
    }
    return actions
}

@Composable
private fun rowMenuActions(
    store: TaskStore,
    task: TaskItem?,
    listCount: Int,
    onMoveToList: (TaskItem) -> Unit,
    onDeleted: () -> Unit
): List<TaskAction> {
    if (task == null) return emptyList()
    val actions = mutableListOf<TaskAction>()
    if (!task.completed) {
        actions += TaskAction(label = stringResource(R.string.tasks_move_up)) {
            store.move(task.id, -1)
        }
        actions += TaskAction(label = stringResource(R.string.tasks_move_down)) {
            store.move(task.id, 1)
        }
        if (task.parentId == null) {
            actions += TaskAction(label = stringResource(R.string.tasks_indent)) {
                store.indent(task.id)
            }
        } else {
            actions += TaskAction(label = stringResource(R.string.tasks_unindent)) {
                store.unindent(task.id)
            }
        }
    }
    actions += TaskAction(
        label = stringResource(
            if (task.starred) R.string.tasks_unstar else R.string.tasks_star
        )
    ) {
        store.setStarred(task.id, !task.starred)
    }
    actions += TaskAction(
        label = stringResource(
            if (task.completed) R.string.tasks_reopen else R.string.tasks_complete
        )
    ) {
        store.setCompleted(task.id, !task.completed)
    }
    if (listCount > 1) {
        actions += TaskAction(label = stringResource(R.string.tasks_move_to_list)) {
            onMoveToList(task)
        }
    }
    actions += TaskAction(
        label = stringResource(R.string.tasks_delete),
        destructive = true
    ) {
        store.removeTask(task.id)
        onDeleted()
    }
    return actions
}
