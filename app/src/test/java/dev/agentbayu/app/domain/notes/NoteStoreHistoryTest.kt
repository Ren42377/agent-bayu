package dev.agentbayu.app.domain.notes

import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.platform.InMemoryTaskStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NoteStoreHistoryTest {

    @Test
    fun editHistorySurvivesStoreReload() {
        val storage = InMemoryTaskStorage()
        val clock = FakeClock(1L)
        val store = NoteStore(storage, clock)
        val undoHistory = listOf("first", "second")
        val redoHistory = listOf("future")

        val id = store.createNote(
            title = "Note",
            content = "current",
            undoHistory = undoHistory,
            redoHistory = redoHistory
        )

        val restored = NoteStore(storage, clock).find(id)

        assertNotNull(restored)
        assertEquals(undoHistory, restored?.undoHistory)
        assertEquals(redoHistory, restored?.redoHistory)
    }

    @Test
    fun oldNoteFilesLoadWithEmptyHistory() {
        val storage = InMemoryTaskStorage()
        storage.write("note-old.json", """{"id":"note-old","title":"Old","content":"Body"}""")

        val restored = NoteStore(storage, FakeClock(1L)).find("note-old")

        assertNotNull(restored)
        assertEquals(emptyList<String>(), restored?.undoHistory)
        assertEquals(emptyList<String>(), restored?.redoHistory)
    }

    @Test
    fun upsertLimitsBothHistoryStacksToTenEntries() {
        val store = NoteStore(InMemoryTaskStorage(), FakeClock(1L))
        val id = store.createNote("Note", "current")
        val history = (0..MAX_NOTE_HISTORY_ENTRIES).map { it.toString() }

        store.upsertNote(
            store.find(id)!!.copy(
                undoHistory = history,
                redoHistory = history
            )
        )

        assertEquals(history.takeLast(MAX_NOTE_HISTORY_ENTRIES), store.find(id)?.undoHistory)
        assertEquals(history.takeLast(MAX_NOTE_HISTORY_ENTRIES), store.find(id)?.redoHistory)
    }
}
