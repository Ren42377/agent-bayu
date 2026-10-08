package dev.agentbayu.app.ui.notes

import dev.agentbayu.app.domain.notes.MAX_NOTE_HISTORY_ENTRIES

internal data class NoteEditHistory(
    val undo: List<String>,
    val redo: List<String>
)

internal data class NoteHistoryTransition(
    val content: String,
    val history: NoteEditHistory
)

internal fun recordNoteEdit(
    currentContent: String,
    history: NoteEditHistory,
    now: Long,
    lastEditTime: Long
): NoteEditHistory {
    val startsNewGroup = now - lastEditTime > EDIT_GROUP_WINDOW_MILLIS || history.undo.isEmpty()
    val undo = if (startsNewGroup) {
        (history.undo + currentContent).takeLast(MAX_NOTE_HISTORY_ENTRIES)
    } else {
        history.undo
    }
    return NoteEditHistory(undo = undo, redo = emptyList())
}

internal fun undoNoteEdit(
    currentContent: String,
    history: NoteEditHistory
): NoteHistoryTransition? {
    val restored = history.undo.lastOrNull() ?: return null
    return NoteHistoryTransition(
        content = restored,
        history = NoteEditHistory(
            undo = history.undo.dropLast(1),
            redo = (history.redo + currentContent).takeLast(MAX_NOTE_HISTORY_ENTRIES)
        )
    )
}

internal fun redoNoteEdit(
    currentContent: String,
    history: NoteEditHistory
): NoteHistoryTransition? {
    val restored = history.redo.lastOrNull() ?: return null
    return NoteHistoryTransition(
        content = restored,
        history = NoteEditHistory(
            undo = (history.undo + currentContent).takeLast(MAX_NOTE_HISTORY_ENTRIES),
            redo = history.redo.dropLast(1)
        )
    )
}

private const val EDIT_GROUP_WINDOW_MILLIS = 600L
