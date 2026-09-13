package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.domain.tasks.REMINDER_HOUR
import dev.agentbayu.app.domain.tasks.TaskStore
import dev.agentbayu.app.platform.InMemoryTaskStorage
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScheduleToolsTest {

    @Test
    fun itSetsTheDueMomentOfAnExistingTask() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskTimeTool(store = { store }, zone = { ZONE })

        val result = tool.run(
            ToolCall(
                "call",
                "set_task_time",
                "{\"task_id\":\"" + id + "\",\"due\":\"2026-09-05T14:30\"}"
            )
        )

        assertTrue(!result.isError)
        val task = store.find(id)!!
        assertEquals(atMoment("2026-09-05", "14:30"), task.dueAtMillis)
        assertTrue(task.hasTime)
    }

    @Test
    fun itClearsTheDueMomentOnRequest() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskTimeTool(store = { store }, zone = { ZONE })
        tool.run(
            ToolCall(
                "call",
                "set_task_time",
                "{\"task_id\":\"" + id + "\",\"due\":\"2026-09-05T14:30\"}"
            )
        )

        val result = tool.run(
            ToolCall("call", "set_task_time", "{\"task_id\":\"" + id + "\",\"clear\":true}")
        )

        assertTrue(!result.isError)
        val task = store.find(id)!!
        assertNull(task.dueAtMillis)
        assertTrue(!task.hasTime)
    }

    @Test
    fun itSetsTheDeadlineOfAnExistingTask() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskDeadlineTool(store = { store }, zone = { ZONE })

        val result = tool.run(
            ToolCall(
                "call",
                "set_task_deadline",
                "{\"task_id\":\"" + id + "\",\"deadline\":\"2026-09-10\"}"
            )
        )

        assertTrue(!result.isError)
        val task = store.find(id)!!
        assertEquals(atMoment("2026-09-10", "00:00"), task.deadlineAtMillis)
    }

    @Test
    fun itClearsTheDeadlineOnRequest() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskDeadlineTool(store = { store }, zone = { ZONE })
        tool.run(
            ToolCall(
                "call",
                "set_task_deadline",
                "{\"task_id\":\"" + id + "\",\"deadline\":\"2026-09-10\"}"
            )
        )

        val result = tool.run(
            ToolCall("call", "set_task_deadline", "{\"task_id\":\"" + id + "\",\"clear\":true}")
        )

        assertTrue(!result.isError)
        assertNull(store.find(id)!!.deadlineAtMillis)
    }

    @Test
    fun itReplacesTheExtraReminders() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskRemindersTool(store = { store }, zone = { ZONE })

        val result = tool.run(
            ToolCall(
                "call",
                "set_task_reminders",
                "{\"task_id\":\"" + id + "\",\"reminders\":\"2026-09-05T14:30, 2026-09-06\"}"
            )
        )

        assertTrue(!result.isError)
        assertEquals(
            listOf(atMoment("2026-09-05", "14:30"), atMoment("2026-09-06", "09:00")),
            store.find(id)!!.reminders
        )
    }

    @Test
    fun itAddsRemindersWithoutReplacingWhenAsked() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskRemindersTool(store = { store }, zone = { ZONE })
        tool.run(
            ToolCall(
                "call",
                "set_task_reminders",
                "{\"task_id\":\"" + id + "\",\"reminders\":\"2026-09-05T14:30\"}"
            )
        )

        val result = tool.run(
            ToolCall(
                "call",
                "set_task_reminders",
                "{\"task_id\":\"" + id + "\",\"reminders\":\"2026-09-05T14:30, 2026-09-06T08:00\"," +
                    "\"add\":true}"
            )
        )

        assertTrue(!result.isError)
        assertEquals(
            listOf(atMoment("2026-09-05", "14:30"), atMoment("2026-09-06", "08:00")),
            store.find(id)!!.reminders
        )
    }

    @Test
    fun itRemovesEveryExtraReminderWhenTheValueIsEmpty() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskRemindersTool(store = { store }, zone = { ZONE })
        tool.run(
            ToolCall(
                "call",
                "set_task_reminders",
                "{\"task_id\":\"" + id + "\",\"reminders\":\"2026-09-05T14:30\"}"
            )
        )

        val result = tool.run(
            ToolCall("call", "set_task_reminders", "{\"task_id\":\"" + id + "\",\"reminders\":\"\"}")
        )

        assertTrue(!result.isError)
        assertTrue(store.find(id)!!.reminders.isEmpty())
    }

    @Test
    fun itRejectsReminderValuesItCannotRead() = runBlocking {
        val store = newStore()
        val id = firstTaskId(store)
        val tool = SetTaskRemindersTool(store = { store }, zone = { ZONE })

        val result = tool.run(
            ToolCall(
                "call",
                "set_task_reminders",
                "{\"task_id\":\"" + id + "\",\"reminders\":\"2026-09-05T14:30, not-a-date\"}"
            )
        )

        assertTrue(result.isError)
        assertTrue(store.find(id)!!.reminders.isEmpty())
    }

    @Test
    fun itCreatesATaskWithExtraReminders() = runBlocking {
        val store = newStore()
        val tool = CreateTaskTool(
            store = { store },
            defaultListTitle = "Inbox",
            zone = { ZONE }
        )

        val result = tool.run(
            ToolCall(
                "call",
                "create_task",
                "{\"title\":\"Pay rent\",\"reminders\":\"2026-09-05T09:00, 2026-09-06\"}"
            )
        )

        assertTrue(!result.isError)
        val task = store.tasks.value.single()
        assertEquals(
            listOf(atMoment("2026-09-05", "09:00"), atMoment("2026-09-06", "09:00")),
            task.reminders
        )
    }

    @Test
    fun aBareDateReminderFollowsTheMorningReminderHour() {
        assertEquals(
            LocalDateTime.parse("2026-09-06T09:00").atZone(ZONE).toInstant().toEpochMilli(),
            parseReminderMoments("2026-09-06", ZONE)!!.single()
        )
        assertEquals(REMINDER_HOUR, LocalTime.of(REMINDER_HOUR, 0).hour)
    }

    private fun newStore(): TaskStore {
        val store = TaskStore(InMemoryTaskStorage(), FakeClock(1L))
        store.createList("Inbox")
        return store
    }

    private fun firstTaskId(store: TaskStore): String =
        store.createTask(store.lists.value.first().id, "Buy milk")

    private fun atMoment(date: String, time: String): Long =
        LocalDateTime.parse(date + "T" + time).atZone(ZONE).toInstant().toEpochMilli()

    private companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Jakarta")
    }
}
