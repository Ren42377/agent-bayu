package dev.agentbayu.app.ui.notes

import dev.agentbayu.app.domain.notes.MAX_NOTE_HISTORY_ENTRIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditHistoryTest {

    @Test
    fun aNewEditGroupAddsCurrentContentAndClearsRedo() {
        val history = NoteEditHistory(
            undo = (0 until MAX_NOTE_HISTORY_ENTRIES).map { it.toString() },
            redo = listOf("future")
        )

        val updated = recordNoteEdit(
            currentContent = "current",
            history = history,
            now = 1_000L,
            lastEditTime = 0L
        )

        assertEquals(
            (1 until MAX_NOTE_HISTORY_ENTRIES).map { it.toString() } + "current",
            updated.undo
        )
        assertTrue(updated.redo.isEmpty())
    }

    @Test
    fun undoAndRedoRestoreContentAndMoveSnapshotsBetweenStacks() {
        val initial = NoteEditHistory(undo = listOf("before"), redo = emptyList())

        val undone = undoNoteEdit("after", initial)!!
        val redone = redoNoteEdit(undone.content, undone.history)!!

        assertEquals("before", undone.content)
        assertEquals(emptyList<String>(), undone.history.undo)
        assertEquals(listOf("after"), undone.history.redo)
        assertEquals("after", redone.content)
        assertEquals(listOf("before"), redone.history.undo)
        assertEquals(emptyList<String>(), redone.history.redo)
    }

    @Test
    fun editsWithinTheGroupingWindowDoNotAddDuplicateSnapshots() {
        val history = NoteEditHistory(undo = listOf("before"), redo = emptyList())

        val updated = recordNoteEdit(
            currentContent = "current",
            history = history,
            now = 500L,
            lastEditTime = 1L
        )

        assertEquals(listOf("before"), updated.undo)
    }
}
