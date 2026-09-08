package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.domain.tasks.TaskStore
import dev.agentbayu.app.platform.InMemoryTaskStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteTaskToolTest {

    @Test
    fun itDeletesTheTaskNamedByItsId() = runBlocking {
        val store = TaskStore(InMemoryTaskStorage(), FakeClock(1L))
        val list = store.createList("Inbox")
        val taskId = store.createTask(list, "Buy milk")
        val tool = DeleteTaskTool { store }

        val result = tool.run(ToolCall("call", "delete_task", "{\"task_id\":\"" + taskId + "\"}"))

        assertTrue(!result.isError)
        assertEquals("Deleted Buy milk", result.content)
        assertEquals(null, store.find(taskId))
    }

    @Test
    fun itRejectsAnUnknownTask() = runBlocking {
        val store = TaskStore(InMemoryTaskStorage(), FakeClock(1L))
        val tool = DeleteTaskTool { store }

        val result = tool.run(ToolCall("call", "delete_task", "{\"task_id\":\"missing\"}"))

        assertTrue(result.isError)
        assertEquals("No task with id missing", result.content)
    }
}
