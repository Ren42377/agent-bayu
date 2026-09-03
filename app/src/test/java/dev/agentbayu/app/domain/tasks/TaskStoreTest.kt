package dev.agentbayu.app.domain.tasks

import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.platform.InMemoryStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStoreTest {

    private val storage = InMemoryStorage()
    private val clock = FakeClock(at("2026-09-03", "08:00"))

    private fun store(): TaskStore = TaskStore(storage, clock) { TEST_ZONE }

    private fun orderedIds(store: TaskStore, listId: String): List<String> = sortTasks(
        store.tasks.value.filter { it.listId == listId && it.parentId == null },
        TaskSort.MY_ORDER
    ).map { it.id }

    @Test
    fun theFirstListBecomesTheActiveOne() {
        val store = store()
        val first = store.createList("Rumah")
        val second = store.createList("Kerja")
        assertEquals(first, store.activeListId.value)
        store.setActiveList(second)
        assertEquals(second, store.activeListId.value)
        store.setActiveList("missing")
        assertEquals(second, store.activeListId.value)
    }

    @Test
    fun aBlankTitleCreatesNothing() {
        val store = store()
        val listId = store.createList("Rumah")
        assertEquals("", store.createTask(listId, "   "))
        assertTrue(store.tasks.value.isEmpty())
    }

    @Test
    fun renamingIgnoresBlankTitles() {
        val store = store()
        val listId = store.createList("Rumah")
        store.renameList(listId, "   ")
        assertEquals("Rumah", store.findList(listId)?.title)
        store.renameList(listId, "  Kerja  ")
        assertEquals("Kerja", store.findList(listId)?.title)
    }

    @Test
    fun removingAListDropsItsTasksAndMovesTheActiveOne() {
        val store = store()
        val first = store.createList("Rumah")
        val second = store.createList("Kerja")
        store.createTask(first, "Sapu")
        store.removeList(first)
        assertEquals(listOf(second), store.lists.value.map { it.id })
        assertTrue(store.tasks.value.isEmpty())
        assertEquals(second, store.activeListId.value)
    }

    @Test
    fun indentingAdoptsTheTaskAndDropsBothRepeats() {
        val store = store()
        val listId = store.createList("Rumah")
        val parent = store.createTask(listId, "Pertama")
        val child = store.createTask(listId, "Kedua")
        store.upsertTask(store.find(parent)!!.copy(repeat = daily()))
        store.upsertTask(store.find(child)!!.copy(repeat = daily()))
        store.indent(child)
        assertEquals(parent, store.find(child)?.parentId)
        assertNull(store.find(child)?.repeat)
        assertNull(store.find(parent)?.repeat)
    }

    @Test
    fun theFirstTaskCannotBeIndented() {
        val store = store()
        val listId = store.createList("Rumah")
        val first = store.createTask(listId, "Pertama")
        store.createTask(listId, "Kedua")
        store.indent(first)
        assertNull(store.find(first)?.parentId)
    }

    @Test
    fun unindentingPutsTheTaskBackBelowItsParent() {
        val store = store()
        val listId = store.createList("Rumah")
        val first = store.createTask(listId, "Pertama")
        val second = store.createTask(listId, "Kedua")
        val third = store.createTask(listId, "Ketiga")
        store.indent(second)
        store.unindent(second)
        assertNull(store.find(second)?.parentId)
        assertEquals(listOf(first, second, third), orderedIds(store, listId))
    }

    @Test
    fun movingToAnotherListCarriesTheSubtasks() {
        val store = store()
        val home = store.createList("Rumah")
        val work = store.createList("Kerja")
        val parent = store.createTask(home, "Pertama")
        val child = store.createTask(home, "Anak", parent)
        store.moveToList(parent, work)
        assertEquals(work, store.find(parent)?.listId)
        assertNull(store.find(parent)?.parentId)
        assertEquals(work, store.find(child)?.listId)
        assertEquals(parent, store.find(child)?.parentId)
    }

    @Test
    fun movingToAnUnknownListIsIgnored() {
        val store = store()
        val home = store.createList("Rumah")
        val taskId = store.createTask(home, "Pertama")
        store.moveToList(taskId, "missing")
        assertEquals(home, store.find(taskId)?.listId)
    }

    @Test
    fun clearingCompletedOnlyTouchesOneList() {
        val store = store()
        val home = store.createList("Rumah")
        val work = store.createList("Kerja")
        val done = store.createTask(home, "Selesai")
        val pending = store.createTask(home, "Belum")
        val other = store.createTask(work, "Lain")
        store.setCompleted(done, true)
        store.setCompleted(other, true)
        store.clearCompleted(home)
        assertEquals(setOf(pending, other), store.tasks.value.map { it.id }.toSet())
    }

    @Test
    fun completingARepeatingTaskRollsForwardAndKeepsAHistoryCopy() {
        val store = store()
        val listId = store.createList("Rumah")
        val taskId = store.createTask(listId, "Minum obat")
        val due = at("2026-09-03", "08:00")
        store.upsertTask(
            store.find(taskId)!!.copy(dueAtMillis = due, hasTime = true, repeat = daily())
        )
        clock.set(at("2026-09-03", "10:00"))
        store.setCompleted(taskId, true)
        val live = store.find(taskId)!!
        assertFalse(live.completed)
        assertEquals(at("2026-09-04", "08:00"), live.dueAtMillis)
        assertEquals(1, live.occurrenceIndex)
        val history = store.tasks.value.single { it.id != taskId }
        assertTrue(history.completed)
        assertNull(history.repeat)
        assertEquals(due, history.dueAtMillis)
        assertEquals(at("2026-09-03", "10:00"), history.completedAtMillis)
    }

    @Test
    fun aRepeatingParentWithSubtasksIsCompletedInPlace() {
        val store = store()
        val listId = store.createList("Rumah")
        val parent = store.createTask(listId, "Pertama")
        val child = store.createTask(listId, "Anak", parent)
        store.upsertTask(
            store.find(parent)!!.copy(
                dueAtMillis = at("2026-09-03", "08:00"),
                hasTime = true,
                repeat = daily()
            )
        )
        store.setCompleted(parent, true)
        assertEquals(2, store.tasks.value.size)
        assertTrue(store.find(parent)!!.completed)
        assertTrue(store.find(child)!!.completed)
    }

    @Test
    fun reopeningASubtaskReopensItsParent() {
        val store = store()
        val listId = store.createList("Rumah")
        val parent = store.createTask(listId, "Pertama")
        val child = store.createTask(listId, "Anak", parent)
        store.setCompleted(parent, true)
        store.setCompleted(child, false)
        assertFalse(store.find(child)!!.completed)
        assertFalse(store.find(parent)!!.completed)
        assertNull(store.find(parent)!!.completedAtMillis)
    }

    @Test
    fun starringStampsTheTimeAndUnstarringClearsIt() {
        val store = store()
        val listId = store.createList("Rumah")
        val taskId = store.createTask(listId, "Pertama")
        clock.set(at("2026-09-03", "11:00"))
        store.setStarred(taskId, true)
        assertTrue(store.find(taskId)!!.starred)
        assertEquals(at("2026-09-03", "11:00"), store.find(taskId)!!.starredAtMillis)
        store.setStarred(taskId, false)
        assertFalse(store.find(taskId)!!.starred)
        assertNull(store.find(taskId)!!.starredAtMillis)
    }

    @Test
    fun removingATaskRemovesItsSubtasks() {
        val store = store()
        val listId = store.createList("Rumah")
        val parent = store.createTask(listId, "Pertama")
        store.createTask(listId, "Anak", parent)
        store.removeTask(parent)
        assertTrue(store.tasks.value.isEmpty())
    }

    @Test
    fun movingATaskReordersItsSiblings() {
        val store = store()
        val listId = store.createList("Rumah")
        val first = store.createTask(listId, "Pertama")
        val second = store.createTask(listId, "Kedua")
        val third = store.createTask(listId, "Ketiga")
        store.move(third, -1)
        assertEquals(listOf(first, third, second), orderedIds(store, listId))
        store.move(first, -1)
        assertEquals(listOf(first, third, second), orderedIds(store, listId))
    }

    @Test
    fun everythingSurvivesARestart() {
        val store = store()
        val listId = store.createList("Rumah")
        val taskId = store.createTask(listId, "Minum obat")
        store.upsertTask(
            store.find(taskId)!!.copy(
                dueAtMillis = at("2026-09-04", "08:00"),
                hasTime = true,
                repeat = daily(endAfterCount = 3)
            )
        )
        store.setStarred(taskId, true)
        store.setSort(TaskSort.DATE)
        val restored = store()
        assertEquals(listOf("Rumah"), restored.lists.value.map { it.title })
        assertEquals(listId, restored.activeListId.value)
        assertEquals(TaskSort.DATE, restored.sort.value)
        val task = restored.find(taskId)!!
        assertEquals(at("2026-09-04", "08:00"), task.dueAtMillis)
        assertEquals(3, task.repeat?.endAfterCount)
        assertTrue(task.starred)
    }

    @Test
    fun clearingWipesTheStoredFile() {
        val store = store()
        store.createTask(store.createList("Rumah"), "Pertama")
        store.clear()
        assertTrue(store.lists.value.isEmpty())
        assertTrue(store.tasks.value.isEmpty())
        assertNull(store.activeListId.value)
        assertNull(storage.read(TaskStore.FILE_NAME))
    }
}
