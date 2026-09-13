package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.domain.tasks.TaskStore
import dev.agentbayu.app.platform.InMemoryTaskStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadTaskToolTest {

    @Test
    fun itReturnsTheFullTaskIncludingNotes() = runBlocking {
        val store = TaskStore(InMemoryTaskStorage(), FakeClock(1L))
        val list = store.createList("Inbox")
        val taskId = store.createTask(list, "Buy milk")
        val task = store.find(taskId)!!
        store.upsertTask(task.copy(details = "Two liters, low fat"))
        val tool = ReadTaskTool { store }

        val result = tool.run(ToolCall("call", "read_task", "{\"task_id\":\"" + taskId + "\"}"))

        assertTrue(!result.isError)
        assertTrue(result.content.contains("Title: Buy milk"))
        assertTrue(result.content.contains("Notes: Two liters, low fat"))
        assertTrue(result.content.contains("List: Inbox"))
    }

    @Test
    fun itListsTheSubtasksOfTheTask() = runBlocking {
        val store = TaskStore(InMemoryTaskStorage(), FakeClock(1L))
        val list = store.createList("Inbox")
        val parentId = store.createTask(list, "Pack bags")
        store.createTask(list, "Passport", parentId)
        val tool = ReadTaskTool { store }

        val result = tool.run(ToolCall("call", "read_task", "{\"task_id\":\"" + parentId + "\"}"))

        assertTrue(!result.isError)
        assertTrue(result.content.contains("Subtasks:"))
        assertTrue(result.content.contains("Passport"))
    }

    @Test
    fun itRejectsAnUnknownTask() = runBlocking {
        val store = TaskStore(InMemoryTaskStorage(), FakeClock(1L))
        val tool = ReadTaskTool { store }

        val result = tool.run(ToolCall("call", "read_task", "{\"task_id\":\"missing\"}"))

        assertTrue(result.isError)
        assertEquals("No task with id missing", result.content)
    }
}
