package dev.agentbayu.app.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskStore
import dev.agentbayu.app.domain.tasks.canRepeat
import dev.agentbayu.app.domain.tasks.subtasksOf
import dev.agentbayu.app.ui.components.GlassDialog

@Composable
fun TaskDetailRoute(
    taskId: String?,
    listId: String,
    parentId: String?,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.tasks(context) }
    val lists by store.lists.collectAsState()
    val tasks by store.tasks.collectAsState()
    val titleRequired = stringResource(R.string.tasks_detail_title_required)
    val savedMessage = stringResource(R.string.tasks_saved)
    val deletedMessage = stringResource(R.string.tasks_deleted)

    var draft by remember(taskId) {
        mutableStateOf(draftOf(taskId?.let { store.find(it) }, listId))
    }
    var deleteOpen by remember { mutableStateOf(false) }

    val existing = taskId?.let { id -> tasks.firstOrNull { it.id == id } }
    val subtasks = remember(tasks, taskId) {
        if (taskId == null) emptyList() else subtasksOf(tasks, taskId)
    }
    val repeatAllowed = if (existing == null) {
        parentId == null
    } else {
        canRepeat(tasks, existing)
    }

    TaskDetailScreen(
        isNew = existing == null,
        draft = draft,
        lists = lists,
        subtasks = subtasks,
        repeatAllowed = repeatAllowed,
        showSubtasks = existing != null && existing.parentId == null,
        onDraftChange = { draft = it },
        onSubtaskAdd = { title ->
            existing?.let { store.createTask(it.listId, title, it.id) }
        },
        onSubtaskToggle = { subtask -> store.setCompleted(subtask.id, !subtask.completed) },
        onSubtaskDelete = { subtask -> store.removeTask(subtask.id) },
        onSave = {
            val title = draft.title.trim()
            if (title.isEmpty()) {
                onMessage(titleRequired)
            } else {
                save(store, existing?.id, parentId, draft.copy(title = title), repeatAllowed)
                onMessage(savedMessage)
                onBack()
            }
        },
        onDelete = { deleteOpen = true },
        onBack = onBack,
        modifier = modifier
    )

    GlassDialog(
        visible = deleteOpen && existing != null,
        title = stringResource(R.string.tasks_delete),
        body = stringResource(R.string.tasks_delete_body),
        confirmLabel = stringResource(R.string.tasks_delete),
        onConfirm = {
            deleteOpen = false
            existing?.let { store.removeTask(it.id) }
            onMessage(deletedMessage)
            onBack()
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { deleteOpen = false }
    )
}

private fun save(
    store: TaskStore,
    taskId: String?,
    parentId: String?,
    draft: TaskDraft,
    repeatAllowed: Boolean
) {
    val id = taskId ?: store.createTask(
        parentId?.let { store.find(it)?.listId } ?: draft.listId,
        draft.title,
        parentId
    )
    val base = store.find(id) ?: return
    store.upsertTask(
        base.copy(
            title = draft.title,
            details = draft.details.trim(),
            dueAtMillis = draft.dueAtMillis,
            hasTime = draft.hasTime && draft.dueAtMillis != null,
            deadlineAtMillis = draft.deadlineAtMillis,
            repeat = if (repeatAllowed) draft.repeat else null
        )
    )
    if (base.starred != draft.starred) {
        store.setStarred(id, draft.starred)
    }
    if (base.listId != draft.listId) {
        store.moveToList(id, draft.listId)
    }
}

private fun draftOf(task: TaskItem?, listId: String): TaskDraft = TaskDraft(
    title = task?.title ?: "",
    details = task?.details ?: "",
    dueAtMillis = task?.dueAtMillis,
    hasTime = task?.hasTime ?: false,
    deadlineAtMillis = task?.deadlineAtMillis,
    repeat = task?.repeat,
    starred = task?.starred ?: false,
    listId = task?.listId ?: listId
)
