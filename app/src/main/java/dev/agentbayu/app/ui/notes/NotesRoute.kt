package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Composable
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
import dev.agentbayu.app.ui.tasks.TaskAction
import dev.agentbayu.app.ui.tasks.TaskActionSheet

@Composable
fun NotesRoute(
    onMessage: (String) -> Unit,
    onOpenNote: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.notes(context) }
    val notes by store.notes.collectAsState()
    val deletedMessage = stringResource(R.string.notes_deleted)

    var query by rememberSaveable { mutableStateOf("") }
    var rowMenuNote by remember { mutableStateOf<NoteItem?>(null) }

    val visibleNotes = remember(notes, query) {
        val trimmed = query.trim()
        notes
            .filter { note ->
                trimmed.isEmpty() ||
                    note.title.contains(trimmed, ignoreCase = true) ||
                    note.content.contains(trimmed, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<NoteItem> { it.pinned }
                    .thenByDescending { it.updatedAtMillis }
            )
    }

    NotesScreen(
        notes = visibleNotes,
        query = query,
        onQueryChange = { query = it },
        onAddNote = { onOpenNote(null) },
        onOpenNote = { note -> onOpenNote(note.id) },
        onNoteMenu = { note -> rowMenuNote = note },
        modifier = modifier
    )

    TaskActionSheet(
        visible = rowMenuNote != null,
        title = rowMenuNote?.title.orEmpty(),
        actions = rowMenuActions(
            note = rowMenuNote,
            onTogglePin = {
                rowMenuNote?.let { store.setPinned(it.id, !it.pinned) }
            },
            onDelete = {
                rowMenuNote?.let { store.removeNote(it.id) }
                onMessage(deletedMessage)
            }
        ),
        onDismiss = { rowMenuNote = null }
    )
}

@Composable
private fun rowMenuActions(
    note: NoteItem?,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
): List<TaskAction> {
    if (note == null) return emptyList()
    return listOf(
        TaskAction(
            label = stringResource(
                if (note.pinned) R.string.notes_unpin else R.string.notes_pin
            ),
            onClick = onTogglePin
        ),
        TaskAction(
            label = stringResource(R.string.notes_delete),
            destructive = true,
            onClick = onDelete
        )
    )
}
