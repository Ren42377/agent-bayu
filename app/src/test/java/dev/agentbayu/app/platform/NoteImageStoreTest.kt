package dev.agentbayu.app.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteImageStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun onlyOldUnreferencedImagesAreRemoved() {
        val directory = folder.newFolder("images")
        val kept = File(directory, "img-a-1.jpg").apply {
            writeText("a")
            setLastModified(1_000L)
        }
        val orphan = File(directory, "img-b-1.jpg").apply {
            writeText("b")
            setLastModified(1_000L)
        }
        val fresh = File(directory, "img-c-1.jpg").apply {
            writeText("c")
            setLastModified(9_000L)
        }

        val removed = pruneUnreferencedImages(
            directory = directory,
            referencedText = "![x](/files/note-images/img-a-1.jpg)",
            nowMillis = 10_000L,
            minAgeMillis = 5_000L
        )

        assertEquals(1, removed)
        assertTrue(kept.exists())
        assertFalse(orphan.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun aMissingDirectoryRemovesNothing() {
        val missing = File(folder.root, "none")

        assertEquals(0, pruneUnreferencedImages(missing, "", 10_000L, 0L))
    }
}
