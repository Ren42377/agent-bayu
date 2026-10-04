package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.ui.components.GlassDialog

@Composable
fun NotesRoute(
    onMessage: (String) -> Unit,
    onOpenNote: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.notes(context) }
    val notes by store.notes.collectAsState()
    val groups by store.groups.collectAsState()
    val activeGId by store.activeGroupId.collectAsState()
    val deletedMessage = stringResource(R.string.notes_deleted)
    val defaultGroupTitle = stringResource(R.string.notes_group_default)

    var query by rememberSaveable { mutableStateOf("") }
    var rowMenuNote by remember { mutableStateOf<NoteItem?>(null) }
    var pinnedOpen by rememberSaveable { mutableStateOf(false) }
    var groupMenuOpen by remember { mutableStateOf(false) }
    var newGroupOpen by remember { mutableStateOf(false) }
    var renameGroupOpen by remember { mutableStateOf(false) }
    var deleteGroupOpen by remember { mutableStateOf(false) }
    var moveTargetNote by remember { mutableStateOf<NoteItem?>(null) }

    LaunchedEffect(groups.isEmpty()) {
        if (groups.isEmpty()) {
            store.setActiveGroup(store.createGroup(defaultGroupTitle))
        }
    }

    val activeGroup = groups.firstOrNull { it.id == activeGId } ?: groups.firstOrNull()
    val groupId = activeGroup?.id

    val visibleNotes = remember(notes, query, groupId, pinnedOpen) {
        val trimmed = query.trim()
        notes
            .filter { note ->
                val matchesGroup = when {
                    pinnedOpen -> note.pinned
                    groupId != null -> note.groupId == groupId
                    else -> true
                }
                val matchesQuery = trimmed.isEmpty() ||
                    note.title.contains(trimmed, ignoreCase = true) ||
                    note.content.contains(trimmed, ignoreCase = true)
                matchesGroup && matchesQuery
            }
            .sortedWith(
                compareByDescending<NoteItem> { it.pinned }
                    .thenByDescending { it.updatedAtMillis }
            )
    }

    NotesScreen(
        groups = groups,
        activeGroup = activeGroup,
        pinnedOpen = pinnedOpen,
        notes = visibleNotes,
        query = query,
        onQueryChange = { query = it },
        onAddNote = { onOpenNote(null) },
        onOpenNote = { note -> onOpenNote(note.id) },
        onNoteMenu = { note -> rowMenuNote = note },
        onSelectPinned = { pinnedOpen = true },
        onSelectGroup = { id ->
            pinnedOpen = false
            store.setActiveGroup(id)
        },
        onNewGroup = { newGroupOpen = true },
        onGroupMenu = { groupMenuOpen = true },
        modifier = modifier
    )

    NotesMenus(
        store = store,
        groups = groups,
        activeGroup = activeGroup,
        groupMenuOpen = groupMenuOpen,
        onGroupMenuDismiss = { groupMenuOpen = false },
        onNewGroup = { newGroupOpen = true },
        onRenameGroup = { renameGroupOpen = true },
        onDeleteGroup = { deleteGroupOpen = true },
        newGroupOpen = newGroupOpen,
        onNewGroupDismiss = { newGroupOpen = false },
        renameGroupOpen = renameGroupOpen,
        onRenameGroupDismiss = { renameGroupOpen = false },
        rowMenuNote = rowMenuNote,
        onRowMenuDismiss = { rowMenuNote = null },
        moveTargetNote = moveTargetNote,
        onMoveTargetDismiss = { moveTargetNote = null },
        onMoveToGroup = { note -> moveTargetNote = note },
        onTogglePin = {
            rowMenuNote?.let { store.setPinned(it.id, !it.pinned) }
        },
        onDeleted = {
            rowMenuNote?.let { store.removeNote(it.id) }
            onMessage(deletedMessage)
        }
    )

    GlassDialog(
        visible = deleteGroupOpen && activeGroup != null,
        title = stringResource(R.string.notes_group_delete),
        body = stringResource(R.string.notes_group_delete_body),
        confirmLabel = stringResource(R.string.notes_group_delete),
        onConfirm = {
            deleteGroupOpen = false
            activeGroup?.let { store.removeGroup(it.id) }
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { deleteGroupOpen = false }
    )
}
