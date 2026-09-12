package dev.agentbayu.app.platform

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskStorageTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun directoryStorageReplacesContentAndIgnoresUncommittedFiles() {
        val directory = temporary.newFolder("tasks")
        val storage = DirectoryTaskStorage(directory)

        storage.write("task-1.json", "old")
        storage.write("task-1.json", "new")
        File(directory, "leftover.json.tmp").writeText("partial")

        assertEquals("new", storage.read("task-1.json"))
        assertEquals(listOf("task-1.json"), storage.names())
        assertNull(storage.read("leftover.json"))
        assertFalse(File(directory, "task-1.json.tmp").exists())
    }

    @Test
    fun anInterruptedInitialCopyIsRecovered() {
        val directory = temporary.newFolder("copy-recovery")
        File(directory, "task-1.json.copy").writeText("complete")
        val storage = DirectoryTaskStorage(directory)

        assertEquals(listOf("task-1.json"), storage.names())
        assertEquals("complete", storage.read("task-1.json"))
        assertFalse(File(directory, "task-1.json.copy").exists())
    }

    @Test
    fun transactionRecoveryFinishesEveryStagedWrite() {
        val directory = temporary.newFolder("transaction-recovery")
        File(directory, "task-1.json").writeText("old-one")
        File(directory, "task-2.json").writeText("old-two")
        File(directory, "task-1.json.staged").writeText("new-one")
        File(directory, "task-2.json.staged").writeText("new-two")
        File(directory, ".transaction.json").writeText(
            """{"writes":["task-1.json","task-2.json"],"deletes":[]}"""
        )
        val storage = DirectoryTaskStorage(directory)

        assertEquals(setOf("task-1.json", "task-2.json"), storage.names().toSet())
        assertEquals("new-one", storage.read("task-1.json"))
        assertEquals("new-two", storage.read("task-2.json"))
        assertFalse(File(directory, ".transaction.json").exists())
    }

    @Test
    fun transactionRecoveryCommitsMetadataAfterTaskFiles() {
        val directory = temporary.newFolder("transaction-order")
        File(directory, "task-1.json").writeText("old-task")
        File(directory, "metadata.json").writeText("old-metadata")
        File(directory, "metadata.json.staged").writeText("new-metadata")
        File(directory, ".transaction.json").writeText(
            """{"writes":["metadata.json","task-1.json"],"deletes":[],"digests":{"metadata.json":"unused","task-1.json":"expected"}}"""
        )
        val storage = DirectoryTaskStorage(directory)

        val failed = runCatching { storage.names() }.isFailure

        assertTrue(failed)
        assertEquals("old-metadata", File(directory, "metadata.json").readText())
    }

    @Test
    fun backupIsRecoveredAfterAnInterruptedReplacement() {
        val directory = temporary.newFolder("backup-recovery")
        File(directory, "task-1.json.bak").writeText("safe")
        File(directory, "task-1.json.tmp").writeText("partial")
        val storage = DirectoryTaskStorage(directory)

        assertEquals(listOf("task-1.json"), storage.names())
        assertEquals("safe", storage.read("task-1.json"))
        assertFalse(File(directory, "task-1.json.bak").exists())
    }

    @Test
    fun missingExternalDirectoryUsesInternalFallback() {
        val fallback = temporary.newFolder("fallback")
        val storage = AppTaskStorage(null, fallback)

        storage.write("metadata.json", "fallback")

        assertEquals("fallback", File(fallback, "metadata.json").readText())
    }

    @Test
    fun fallbackDataMovesToExternalStorageWhenItReturns() {
        val fallback = temporary.newFolder("fallback-migration")
        val external = temporary.newFolder("external")
        DirectoryTaskStorage(fallback).write("metadata.json", metadata("list-fallback", 1L))

        val storage = AppTaskStorage(external, fallback)
        val expected = Json.parseToJsonElement(
            metadata("list-fallback", 1L, taskIds = emptyList())
        )
        val actual = Json.parseToJsonElement(storage.read("metadata.json")!!)

        assertEquals(expected, actual)
        assertEquals(actual, Json.parseToJsonElement(File(external, "metadata.json").readText()))
        assertNull(DirectoryTaskStorage(fallback).read("metadata.json"))
    }

    @Test
    fun fallbackMergePreservesExternalOnlyTasksAndLists() {
        val fallback = temporary.newFolder("fallback-merge")
        val external = temporary.newFolder("external-merge")
        val fallbackStorage = DirectoryTaskStorage(fallback)
        val externalStorage = DirectoryTaskStorage(external)
        fallbackStorage.write("metadata.json", metadata("list-fallback", 2L))
        fallbackStorage.write("task-fallback.json", task("task-fallback", "list-fallback", 20L))
        externalStorage.write("metadata.json", metadata("list-external", 1L))
        externalStorage.write("task-external.json", task("task-external", "list-external", 10L))

        val storage = AppTaskStorage(external, fallback)

        assertTrue(storage.read("task-fallback.json")!!.contains("task-fallback"))
        assertTrue(storage.read("task-external.json")!!.contains("task-external"))
        val lists = Json.parseToJsonElement(storage.read("metadata.json")!!)
            .jsonObject.getValue("lists").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(setOf("list-fallback", "list-external"), lists.toSet())
        assertTrue(DirectoryTaskStorage(fallback).names().isEmpty())
    }

    @Test
    fun malformedFallbackTaskDoesNotHideValidExternalTasks() {
        val fallback = temporary.newFolder("fallback-malformed")
        val external = temporary.newFolder("external-malformed")
        val fallbackStorage = DirectoryTaskStorage(fallback)
        val externalStorage = DirectoryTaskStorage(external)
        fallbackStorage.write("metadata.json", metadata("list-fallback", 2L))
        fallbackStorage.write("task-broken.json", "{")
        externalStorage.write("metadata.json", metadata("list-external", 1L))
        externalStorage.write(
            "task-external.json",
            task("task-external", "list-external", 10L)
        )

        val storage = AppTaskStorage(external, fallback)

        assertNotNull(storage.read("task-external.json"))
        assertNull(storage.read("task-broken.json"))
    }

    @Test
    fun fallbackMergeKeepsTheNewestVersionOfTheSameTask() {
        val fallback = temporary.newFolder("fallback-conflict")
        val external = temporary.newFolder("external-conflict")
        val fallbackStorage = DirectoryTaskStorage(fallback)
        val externalStorage = DirectoryTaskStorage(external)
        fallbackStorage.write("metadata.json", metadata("list-1", 2L))
        fallbackStorage.write("task-same.json", task("task-same", "list-1", 10L, "old"))
        externalStorage.write("metadata.json", metadata("list-1", 3L))
        externalStorage.write("task-same.json", task("task-same", "list-1", 30L, "new"))

        val storage = AppTaskStorage(external, fallback)

        assertTrue(storage.read("task-same.json")!!.contains("new"))
    }

    @Test
    fun clearRemovesActiveFallbackAndExternalFiles() {
        val fallback = temporary.newFolder("fallback-clear")
        val external = temporary.newFolder("external-clear")
        DirectoryTaskStorage(fallback).write("metadata.json", metadata("list-1", 1L))
        DirectoryTaskStorage(external).write("metadata.json", metadata("list-2", 2L))
        DirectoryTaskStorage(external).write("task-2.json", task("task-2", "list-2", 2L))
        val storage = AppTaskStorage(external, fallback)

        storage.clear()

        assertTrue(DirectoryTaskStorage(fallback).names().isEmpty())
        assertTrue(DirectoryTaskStorage(external).names().isEmpty())
    }

    @Test
    fun newerDeletionTombstonesPreventFallbackResurrection() {
        val fallback = temporary.newFolder("fallback-deleted")
        val external = temporary.newFolder("external-deleted")
        val fallbackStorage = DirectoryTaskStorage(fallback)
        val externalStorage = DirectoryTaskStorage(external)
        fallbackStorage.write(
            "metadata.json",
            metadata(
                listId = "list-deleted",
                revision = 1L,
                taskIds = listOf("task-deleted")
            )
        )
        fallbackStorage.write(
            "task-deleted.json",
            task("task-deleted", "list-deleted", 10L)
        )
        externalStorage.write(
            "metadata.json",
            metadata(
                listId = "list-live",
                revision = 2L,
                taskIds = emptyList(),
                deletedTaskIds = listOf("task-deleted"),
                deletedListIds = listOf("list-deleted")
            )
        )

        val storage = AppTaskStorage(external, fallback)

        assertNull(storage.read("task-deleted.json"))
        val metadata = Json.parseToJsonElement(storage.read("metadata.json")!!).jsonObject
        val lists = metadata.getValue("lists").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }
        val deletedTasks = metadata.getValue("deletedTaskIds").jsonArray
            .map { it.jsonPrimitive.content }
        val deletedLists = metadata.getValue("deletedListIds").jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(listOf("list-live"), lists)
        assertTrue("task-deleted" in deletedTasks)
        assertTrue("list-deleted" in deletedLists)
    }

    @Test
    fun olderTombstonesDoNotDeleteNewerRecreatedTasks() {
        val fallback = temporary.newFolder("fallback-recreated")
        val external = temporary.newFolder("external-recreated")
        val fallbackStorage = DirectoryTaskStorage(fallback)
        val externalStorage = DirectoryTaskStorage(external)
        fallbackStorage.write(
            "metadata.json",
            metadata(
                listId = "list-old",
                revision = 1L,
                taskIds = emptyList(),
                deletedTaskIds = listOf("task-recreated")
            )
        )
        externalStorage.write(
            "metadata.json",
            metadata(
                listId = "list-live",
                revision = 2L,
                taskIds = listOf("task-recreated")
            )
        )
        externalStorage.write(
            "task-recreated.json",
            task("task-recreated", "list-live", 20L)
        )

        val storage = AppTaskStorage(external, fallback)

        assertNotNull(storage.read("task-recreated.json"))
        val deletedTasks = Json.parseToJsonElement(storage.read("metadata.json")!!)
            .jsonObject.getValue("deletedTaskIds").jsonArray
            .map { it.jsonPrimitive.content }
        assertFalse("task-recreated" in deletedTasks)
    }

    @Test
    fun newerMetadataWinsListConflicts() {
        val fallback = temporary.newFolder("fallback-list-conflict")
        val external = temporary.newFolder("external-list-conflict")
        DirectoryTaskStorage(fallback).write(
            "metadata.json",
            metadata("list-1", 1L, title = "Old")
        )
        DirectoryTaskStorage(external).write(
            "metadata.json",
            metadata("list-1", 2L, title = "New")
        )

        val storage = AppTaskStorage(external, fallback)

        val title = Json.parseToJsonElement(storage.read("metadata.json")!!)
            .jsonObject.getValue("lists").jsonArray.single()
            .jsonObject.getValue("title").jsonPrimitive.content
        assertEquals("New", title)
    }

    @Test
    fun filenamesCannotEscapeTheStorageDirectory() {
        val storage = DirectoryTaskStorage(temporary.newFolder("safe"))

        val failed = runCatching { storage.write("../outside", "value") }.isFailure

        assertTrue(failed)
    }

    private fun metadata(
        listId: String,
        revision: Long,
        taskIds: List<String>? = null,
        deletedTaskIds: List<String> = emptyList(),
        deletedListIds: List<String> = emptyList(),
        title: String = "List"
    ): String {
        val taskManifest = taskIds?.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        val deletedTasks = deletedTaskIds.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        val deletedLists = deletedListIds.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        val manifestEntry = taskManifest?.let { "\"taskIds\":$it," }.orEmpty()
        return """{"version":1,"revision":$revision,"lists":[{"id":"$listId","title":"$title"}],$manifestEntry"deletedTaskIds":$deletedTasks,"deletedListIds":$deletedLists,"sort":"MY_ORDER","activeListId":"$listId"}"""
    }

    private fun task(
        id: String,
        listId: String,
        updatedAtMillis: Long,
        title: String = "Task"
    ): String =
        """{"id":"$id","listId":"$listId","title":"$title","updatedAtMillis":$updatedAtMillis}"""
}
