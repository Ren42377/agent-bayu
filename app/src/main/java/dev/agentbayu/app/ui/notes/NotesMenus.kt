package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteGroup
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.domain.notes.NoteStore
import dev.agentbayu.app.ui.tasks.TaskAction
import dev.agentbayu.app.ui.tasks.TaskActionSheet
import dev.agentbayu.app.ui.tasks.TaskTextDialog

@Composable
internal fun NotesMenus(
    store: NoteStore,
    groups: List<NoteGroup>,
    activeGroup: NoteGroup?,
    groupMenuOpen: Boolean,
    onGroupMenuDismiss: () -> Unit,
    onNewGroup: () -> Unit,
    onRenameGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    newGroupOpen: Boolean,
    onNewGroupDismiss: () -> Unit,
    renameGroupOpen: Boolean,
    onRenameGroupDismiss: () -> Unit,
    rowMenuNote: NoteItem?,
    onRowMenuDismiss: () -> Unit,
    moveTargetNote: NoteItem?,
    onMoveTargetDismiss: () -> Unit,
    onMoveToGroup: (NoteItem) -> Unit,
    onTogglePin: () -> Unit,
    onDeleted: () -> Unit
) {
    TaskActionSheet(
        visible = groupMenuOpen,
        title = activeGroup?.title ?: stringResource(R.string.notes_title),
        actions = groupMenuActions(
            hasGroup = activeGroup != null,
            onNewGroup = onNewGroup,
            onRenameGroup = onRenameGroup,
            onDeleteGroup = onDeleteGroup
        ),
        onDismiss = onGroupMenuDismiss
    )

    TaskTextDialog(
        visible = newGroupOpen,
        title = stringResource(R.string.notes_group_new),
        hint = stringResource(R.string.notes_group_name_hint),
        initialValue = "",
        confirmLabel = stringResource(R.string.tasks_detail_save),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { title ->
            onNewGroupDismiss()
            store.setActiveGroup(store.createGroup(title))
        },
        onDismiss = onNewGroupDismiss
    )

    TaskTextDialog(
        visible = renameGroupOpen && activeGroup != null,
        title = stringResource(R.string.notes_group_rename),
        hint = stringResource(R.string.notes_group_name_hint),
        initialValue = activeGroup?.title.orEmpty(),
        confirmLabel = stringResource(R.string.tasks_detail_save),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { title ->
            onRenameGroupDismiss()
            activeGroup?.let { store.renameGroup(it.id, title) }
        },
        onDismiss = onRenameGroupDismiss
    )

    TaskActionSheet(
        visible = rowMenuNote != null,
        title = rowMenuNote?.title.orEmpty(),
        actions = rowMenuActions(
            note = rowMenuNote,
            groupCount = groups.size,
            onTogglePin = onTogglePin,
            onMoveToGroup = onMoveToGroup,
            onDeleted = onDeleted
        ),
        onDismiss = onRowMenuDismiss
    )

    TaskActionSheet(
        visible = moveTargetNote != null,
        title = stringResource(R.string.notes_move_to_group),
        actions = groups
            .filter { it.id != moveTargetNote?.groupId }
            .map { target ->
                TaskAction(label = target.title) {
                    moveTargetNote?.let { store.moveToGroup(it.id, target.id) }
                }
            },
        onDismiss = onMoveTargetDismiss
    )
}

@Composable
private fun groupMenuActions(
    hasGroup: Boolean,
    onNewGroup: () -> Unit,
    onRenameGroup: () -> Unit,
    onDeleteGroup: () -> Unit
): List<TaskAction> {
    val actions = mutableListOf(
        TaskAction(label = stringResource(R.string.notes_group_new), onClick = onNewGroup)
    )
    if (hasGroup) {
        actions += TaskAction(
            label = stringResource(R.string.notes_group_rename),
            onClick = onRenameGroup
        )
        actions += TaskAction(
            label = stringResource(R.string.notes_group_delete),
            destructive = true,
            onClick = onDeleteGroup
        )
    }
    return actions
}

@Composable
private fun rowMenuActions(
    note: NoteItem?,
    groupCount: Int,
    onTogglePin: () -> Unit,
    onMoveToGroup: (NoteItem) -> Unit,
    onDeleted: () -> Unit
): List<TaskAction> {
    if (note == null) return emptyList()
    val actions = mutableListOf<TaskAction>()
    
    actions += TaskAction(
        label = stringResource(
            if (note.pinned) R.string.notes_unpin else R.string.notes_pin
        ),
        onClick = onTogglePin
    )
    
    if (groupCount > 1) {
        actions += TaskAction(label = stringResource(R.string.notes_move_to_group)) {
            onMoveToGroup(note)
        }
    }
    
    actions += TaskAction(
        label = stringResource(R.string.notes_delete),
        destructive = true,
        onClick = onDeleted
    )
    
    return actions
}
