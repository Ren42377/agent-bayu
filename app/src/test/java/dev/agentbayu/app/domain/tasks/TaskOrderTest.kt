package dev.agentbayu.app.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskOrderTest {

    @Test
    fun starredRowsGatherEveryListAndSkipCompleted() {
        val tasks = listOf(
            task(id = "a", listId = "list-1", title = "satu", starred = true, position = 1),
            task(id = "b", listId = "list-2", title = "dua", starred = true, position = 0),
            task(id = "c", listId = "list-1", title = "tiga"),
            task(id = "d", listId = "list-2", title = "empat", starred = true, completed = true)
        )

        val rows = starredRows(tasks, TaskSort.MY_ORDER)

        assertEquals(listOf("dua", "satu"), rows.map { it.task.title })
        assertTrue(rows.none { it.subtask })
    }

    @Test
    fun starredCompletedKeepsOnlyFinishedStars() {
        val tasks = listOf(
            task(id = "a", starred = true),
            task(id = "b", starred = true, completed = true, title = "selesai"),
            task(id = "c", completed = true)
        )

        assertEquals(listOf("selesai"), starredCompleted(tasks).map { it.title })
    }

    @Test
    fun subtasksFollowTheirParentInAList() {
        val tasks = listOf(
            task(id = "parent", title = "induk", position = 0),
            task(id = "child", parentId = "parent", title = "anak", position = 1),
            task(id = "other", title = "lain", position = 2)
        )

        val rows = pendingRows(tasks, "list-1", TaskSort.MY_ORDER)

        assertEquals(listOf("induk", "anak", "lain"), rows.map { it.task.title })
        assertEquals(listOf(false, true, false), rows.map { it.subtask })
        assertTrue(rows.first().hasSubtasks)
    }
}
