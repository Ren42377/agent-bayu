package dev.agentbayu.app.platform.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileAccessTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun readsAFileInsideTheRoot() {
        val root = temporary.newFolder("shared")
        File(root, "note.txt").writeText("hello")
        val access = FileAccess(listOf(root))
        assertEquals("hello", access.read("note.txt"))
    }

    @Test
    fun refusesAPathOutsideTheRoot() {
        val root = temporary.newFolder("shared")
        val outside = temporary.newFolder("elsewhere")
        File(outside, "note.txt").writeText("hello")
        val access = FileAccess(listOf(root))
        val error = assertThrows(FileAccessException::class.java) {
            access.read(File(outside, "note.txt").path)
        }
        assertTrue(error.message.orEmpty().contains("Outside the allowed storage"))
    }

    @Test
    fun refusesABlockedDirectoryByAbsolutePath() {
        val root = temporary.newFolder("shared")
        val private = File(root, "private").apply { mkdirs() }
        File(private, "keys.bin").writeText("secret")
        val access = FileAccess(listOf(root), listOf(private))
        val error = assertThrows(FileAccessException::class.java) {
            access.read(File(private, "keys.bin").path)
        }
        assertTrue(error.message.orEmpty().contains("Off limits"))
    }

    @Test
    fun refusesABlockedDirectoryByRelativePath() {
        val root = temporary.newFolder("shared")
        val private = File(root, "private").apply { mkdirs() }
        File(private, "keys.bin").writeText("secret")
        val access = FileAccess(listOf(root), listOf(private))
        assertThrows(FileAccessException::class.java) { access.list("private") }
        assertThrows(FileAccessException::class.java) { access.read("private/keys.bin") }
        assertThrows(FileAccessException::class.java) { access.write("private/keys.bin", "gone") }
        assertThrows(FileAccessException::class.java) { access.delete("private/keys.bin") }
        assertEquals("secret", File(private, "keys.bin").readText())
    }

    @Test
    fun refusesABlockedDirectoryReachedByTraversal() {
        val root = temporary.newFolder("shared")
        val private = File(root, "private").apply { mkdirs() }
        File(root, "public").mkdirs()
        File(private, "keys.bin").writeText("secret")
        val access = FileAccess(listOf(root), listOf(private))
        assertThrows(FileAccessException::class.java) {
            access.read("public/../private/keys.bin")
        }
    }

    @Test
    fun refusesToMoveIntoABlockedDirectory() {
        val root = temporary.newFolder("shared")
        val private = File(root, "private").apply { mkdirs() }
        File(root, "note.txt").writeText("hello")
        val access = FileAccess(listOf(root), listOf(private))
        assertThrows(FileAccessException::class.java) {
            access.move("note.txt", "private/note.txt")
        }
        assertTrue(File(root, "note.txt").exists())
    }

    @Test
    fun searchSkipsBlockedDirectories() {
        val root = temporary.newFolder("shared")
        val private = File(root, "private").apply { mkdirs() }
        File(private, "keys.txt").writeText("needle")
        File(root, "note.txt").writeText("needle")
        val access = FileAccess(listOf(root), listOf(private))
        val matches = access.search(".", "needle")
        assertTrue(matches.contains("note.txt"))
        assertTrue(!matches.contains("keys.txt"))
    }
}
