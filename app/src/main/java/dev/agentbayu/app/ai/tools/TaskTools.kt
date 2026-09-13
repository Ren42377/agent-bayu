package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.domain.tasks.REMINDER_HOUR
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateTaskTool(
    private val store: () -> TaskStore,
    private val defaultListTitle: String,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Create a task in the owner task lists. It shows up in the Tasks tab " +
            "right away and schedules its own reminder when a due date is given. A task can " +
            "also carry extra reminder moments on several dates and times at once.",
        parameters = toolSchema(
            ToolField("title", "string", "What the task is about"),
            ToolField("details", "string", "Longer notes for the task", required = false),
            ToolField(
                name = "due",
                type = "string",
                description = "Due date as 2026-09-05, or a due moment as 2026-09-05T14:30, " +
                    "in the owner local time",
                required = false
            ),
            ToolField(
                name = "has_time",
                type = "boolean",
                description = "True when the due value carries a clock time that matters",
                required = false
            ),
            ToolField(
                name = "reminders",
                type = "string",
                description = "Extra reminder moments as a comma separated list, each as " +
                    "2026-09-05T14:30 in the owner local time; a bare date 2026-09-05 " +
                    "reminds at 09:00",
                required = false
            ),
            ToolField(
                name = "starred",
                type = "boolean",
                description = "Mark the task as starred",
                required = false
            ),
            ToolField(
                name = "list",
                type = "string",
                description = "Name of the list to put the task in, created when missing",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val title = arguments.text("title")
            ?: return@withContext call.problem("A title is required")
        val tasks = store()
        val due = arguments.text("due")
        val moment = due?.let { parseMoment(it, zone()) }
        if (due != null && moment == null) {
            return@withContext call.problem("Cannot read the due date: " + due)
        }
        val reminders = parseReminderMoments(arguments.text("reminders").orEmpty(), zone())
            ?: return@withContext call.problem(
                "Cannot read the reminder moments: " + arguments.text("reminders")
            )
        val listId = listIdFor(tasks, arguments.text("list"))
        val id = tasks.createTask(listId, title)
        if (id.isEmpty()) return@withContext call.problem("A title is required")
        val created = tasks.find(id)
            ?: return@withContext call.problem("The task was not stored")
        tasks.upsertTask(
            created.copy(
                details = arguments.text("details").orEmpty(),
                dueAtMillis = moment?.atMillis,
                hasTime = arguments.flag("has_time", moment?.hasTime ?: false),
                reminders = reminders
            )
        )
        if (arguments.flag("starred")) tasks.setStarred(id, true)
        val listName = tasks.findList(listId)?.title.orEmpty()
        call.reply("Created " + id + ": " + title + " in " + listName)
    }

    private fun listIdFor(tasks: TaskStore, wanted: String?): String {
        if (wanted != null) {
            val match = tasks.lists.value.firstOrNull { it.title.equals(wanted, true) }
            return match?.id ?: tasks.createList(wanted)
        }
        val active = tasks.activeListId.value
        if (active != null && tasks.findList(active) != null) return active
        val first = tasks.lists.value.firstOrNull()
        return first?.id ?: tasks.createList(defaultListTitle)
    }

    private companion object {
        const val NAME = "create_task"
    }
}

class ListTasksTool(private val store: () -> TaskStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "List the owner tasks with their ids, so a task can be completed later.",
        parameters = toolSchema(
            ToolField(
                name = "list",
                type = "string",
                description = "Name of one list to read, otherwise every list is read",
                required = false
            ),
            ToolField(
                name = "include_completed",
                type = "boolean",
                description = "Include tasks that are already done",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val tasks = store()
        val wanted = arguments.text("list")
        val lists = if (wanted == null) {
            tasks.lists.value
        } else {
            tasks.lists.value.filter { it.title.equals(wanted, true) }
        }
        if (lists.isEmpty()) {
            return@withContext call.reply(
                if (wanted == null) "There are no lists yet" else "No list named " + wanted
            )
        }
        val includeCompleted = arguments.flag("include_completed")
        val all = tasks.tasks.value
        val lines = ArrayList<String>()
        lists.sortedBy { it.position }.forEach { list ->
            lines += list.title
            val rows = all
                .filter { it.listId == list.id && (includeCompleted || !it.completed) }
                .sortedWith(compareBy({ it.position }, { it.createdAtMillis }))
            if (rows.isEmpty()) {
                lines += INDENT + "no tasks"
            } else {
                rows.forEach { task -> lines += INDENT + describe(task) }
            }
        }
        call.reply(lines.joinToString("\n"))
    }

    private fun describe(task: TaskItem): String {
        val marks = ArrayList<String>()
        if (task.completed) marks += "done"
        if (task.starred) marks += "starred"
        task.dueAtMillis?.let { marks += "due " + formatMoment(it, task.hasTime) }
        task.deadlineAtMillis?.let { marks += "deadline " + formatMoment(it, false) }
        if (task.reminders.isNotEmpty()) {
            marks += "reminders " + task.reminders.sorted()
                .joinToString(", ") { formatMoment(it, true) }
        }
        if (task.details.isNotEmpty()) marks += "has notes"
        val suffix = if (marks.isEmpty()) "" else "  [" + marks.joinToString(", ") + "]"
        return task.id + "  " + task.title + suffix
    }

    private companion object {
        const val NAME = "list_tasks"
        const val INDENT = "  "
    }
}

class DeleteTaskTool(private val store: () -> TaskStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Delete one task and its subtasks. Read the ids with list_tasks first.",
        parameters = toolSchema(
            ToolField("task_id", "string", "Id of the task, as reported by list_tasks")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val taskId = ToolArguments(call.arguments).text("task_id")
            ?: return@withContext call.problem("A task_id is required")
        val tasks = store()
        val target = tasks.find(taskId)
            ?: return@withContext call.problem("No task with id " + taskId)
        tasks.removeTask(taskId)
        call.reply("Deleted " + target.title)
    }

    private companion object {
        const val NAME = "delete_task"
    }
}

class CompleteTaskTool(private val store: () -> TaskStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Mark one task as done. Read the ids with list_tasks first.",
        parameters = toolSchema(
            ToolField("task_id", "string", "Id of the task, as reported by list_tasks")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val taskId = ToolArguments(call.arguments).text("task_id")
            ?: return@withContext call.problem("A task_id is required")
        val tasks = store()
        val target = tasks.find(taskId)
            ?: return@withContext call.problem("No task with id " + taskId)
        if (target.completed) return@withContext call.reply("Already done: " + target.title)
        tasks.setCompleted(taskId, true)
        call.reply("Completed " + target.title)
    }

    private companion object {
        const val NAME = "complete_task"
    }
}

class SetTaskTimeTool(
    private val store: () -> TaskStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Set or clear the due date and clock time of one existing task. " +
            "This is the moment the task is scheduled for and it drives the task reminder. " +
            "Read the ids with list_tasks first.",
        parameters = toolSchema(
            ToolField("task_id", "string", "Id of the task, as reported by list_tasks"),
            ToolField(
                name = "due",
                type = "string",
                description = "Due date as 2026-09-05, or a due moment as 2026-09-05T14:30, " +
                    "in the owner local time",
                required = false
            ),
            ToolField(
                name = "has_time",
                type = "boolean",
                description = "True when the due value carries a clock time that matters",
                required = false
            ),
            ToolField(
                name = "clear",
                type = "boolean",
                description = "True to remove the current due date and time instead of setting one",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val taskId = arguments.text("task_id")
            ?: return@withContext call.problem("A task_id is required")
        val tasks = store()
        val target = tasks.find(taskId)
            ?: return@withContext call.problem("No task with id " + taskId)
        if (arguments.flag("clear")) {
            tasks.upsertTask(target.copy(dueAtMillis = null, hasTime = false))
            return@withContext call.reply("Cleared the due date of " + target.title)
        }
        val due = arguments.text("due")
            ?: return@withContext call.problem("A due value is required, or clear=true")
        val moment = parseMoment(due, zone())
            ?: return@withContext call.problem("Cannot read the due date: " + due)
        tasks.upsertTask(
            target.copy(
                dueAtMillis = moment.atMillis,
                hasTime = arguments.flag("has_time", moment.hasTime)
            )
        )
        call.reply(
            "Set " + target.title + " due to " + formatMoment(moment.atMillis, moment.hasTime)
        )
    }

    private companion object {
        const val NAME = "set_task_time"
    }
}

class SetTaskDeadlineTool(
    private val store: () -> TaskStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Set or clear the deadline of one existing task. The deadline is the last " +
            "date the task may be finished on; its reminder rings at 09:00 on that day and any " +
            "clock time in the value is ignored. Read the ids with list_tasks first.",
        parameters = toolSchema(
            ToolField("task_id", "string", "Id of the task, as reported by list_tasks"),
            ToolField(
                name = "deadline",
                type = "string",
                description = "Deadline date as 2026-09-05 in the owner local time",
                required = false
            ),
            ToolField(
                name = "clear",
                type = "boolean",
                description = "True to remove the current deadline instead of setting one",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val taskId = arguments.text("task_id")
            ?: return@withContext call.problem("A task_id is required")
        val tasks = store()
        val target = tasks.find(taskId)
            ?: return@withContext call.problem("No task with id " + taskId)
        if (arguments.flag("clear")) {
            tasks.upsertTask(target.copy(deadlineAtMillis = null))
            return@withContext call.reply("Cleared the deadline of " + target.title)
        }
        val deadline = arguments.text("deadline")
            ?: return@withContext call.problem("A deadline value is required, or clear=true")
        val moment = parseMoment(deadline, zone())
            ?: return@withContext call.problem("Cannot read the deadline: " + deadline)
        tasks.upsertTask(target.copy(deadlineAtMillis = moment.atMillis))
        call.reply(
            "Set " + target.title + " deadline to " + formatMoment(moment.atMillis, false)
        )
    }

    private companion object {
        const val NAME = "set_task_deadline"
    }
}

class SetTaskRemindersTool(
    private val store: () -> TaskStore,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Replace or add the extra reminder moments of one existing task, so one " +
            "task can remind on several dates and times at once. Every extra reminder rings " +
            "exactly at its moment on top of the due date reminder. Read the ids with " +
            "list_tasks first.",
        parameters = toolSchema(
            ToolField("task_id", "string", "Id of the task, as reported by list_tasks"),
            ToolField(
                name = "reminders",
                type = "string",
                description = "Comma separated moments, each as 2026-09-05T14:30 in the owner " +
                    "local time; a bare date 2026-09-05 reminds at 09:00. An empty value " +
                    "removes every extra reminder",
                required = false
            ),
            ToolField(
                name = "add",
                type = "boolean",
                description = "True to add these moments to the existing ones instead of " +
                    "replacing them",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val taskId = arguments.text("task_id")
            ?: return@withContext call.problem("A task_id is required")
        val tasks = store()
        val target = tasks.find(taskId)
            ?: return@withContext call.problem("No task with id " + taskId)
        val raw = arguments.text("reminders").orEmpty()
        val moments = parseReminderMoments(raw, zone())
            ?: return@withContext call.problem("Cannot read the reminder moments: " + raw)
        val merged = if (arguments.flag("add")) {
            (target.reminders + moments).distinct().sorted()
        } else {
            moments.distinct().sorted()
        }
        tasks.upsertTask(target.copy(reminders = merged))
        call.reply(
            when {
                merged.isEmpty() -> "Removed every extra reminder of " + target.title
                else -> "Set " + merged.size + " extra reminder(s) on " + target.title + ": " +
                    merged.joinToString(", ") { formatMoment(it, true) }
            }
        )
    }

    private companion object {
        const val NAME = "set_task_reminders"
    }
}

internal class TaskMoment(val atMillis: Long, val hasTime: Boolean)

internal fun parseMoment(raw: String, zone: ZoneId): TaskMoment? {
    val text = raw.trim().replace(' ', 'T')
    if (text.isEmpty()) return null
    if (!text.contains('T')) {
        val date = try {
            LocalDate.parse(text)
        } catch (error: DateTimeParseException) {
            return null
        }
        return TaskMoment(date.atStartOfDay(zone).toInstant().toEpochMilli(), false)
    }
    val local = try {
        LocalDateTime.parse(text)
    } catch (error: DateTimeParseException) {
        null
    }
    if (local != null) {
        return TaskMoment(local.atZone(zone).toInstant().toEpochMilli(), true)
    }
    return try {
        TaskMoment(Instant.parse(text).toEpochMilli(), true)
    } catch (error: DateTimeParseException) {
        null
    }
}

internal fun parseReminderMoments(raw: String, zone: ZoneId): List<Long>? {
    val tokens = raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    val moments = ArrayList<Long>(tokens.size)
    tokens.forEach { token ->
        val moment = parseMoment(token, zone) ?: return null
        moments += if (moment.hasTime) {
            moment.atMillis
        } else {
            Instant.ofEpochMilli(moment.atMillis).atZone(zone).toLocalDate()
                .atTime(LocalTime.of(REMINDER_HOUR, 0))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
    }
    return moments
}

internal fun formatMoment(atMillis: Long, hasTime: Boolean): String {
    val moment = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
    val pattern = if (hasTime) MOMENT_PATTERN else DATE_PATTERN
    return DateTimeFormatter.ofPattern(pattern).format(moment)
}

private const val DATE_PATTERN = "yyyy-MM-dd"
private const val MOMENT_PATTERN = "yyyy-MM-dd HH:mm"
