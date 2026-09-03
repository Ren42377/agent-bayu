package dev.agentbayu.app.domain.tasks

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.platform.EncryptedStorage
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class TaskStore(
    private val storage: EncryptedStorage,
    private val clock: Clock = RealClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val restored = load()
    private val listsState = MutableStateFlow(restored.lists)
    private val tasksState = MutableStateFlow(restored.tasks)
    private val sortState = MutableStateFlow(restored.sort)
    private val activeState = MutableStateFlow(
        restored.activeListId ?: restored.lists.firstOrNull()?.id
    )

    val lists: StateFlow<List<TaskList>> = listsState.asStateFlow()
    val tasks: StateFlow<List<TaskItem>> = tasksState.asStateFlow()
    val sort: StateFlow<TaskSort> = sortState.asStateFlow()
    val activeListId: StateFlow<String?> = activeState.asStateFlow()

    fun find(taskId: String): TaskItem? = tasksState.value.firstOrNull { it.id == taskId }

    fun findList(listId: String): TaskList? = listsState.value.firstOrNull { it.id == listId }

    fun createList(title: String): String {
        val id = newId(LIST_PREFIX) { candidate -> listsState.value.any { it.id == candidate } }
        val position = listsState.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        listsState.value = listsState.value + TaskList(
            id = id,
            title = title.trim(),
            position = position,
            createdAtMillis = clock.nowMillis()
        )
        if (activeState.value == null) activeState.value = id
        persist()
        return id
    }

    fun renameList(listId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val current = listsState.value
        val index = current.indexOfFirst { it.id == listId }
        if (index < 0 || current[index].title == trimmed) return
        listsState.value = current.toMutableList().apply {
            set(index, current[index].copy(title = trimmed))
        }
        persist()
    }

    fun removeList(listId: String) {
        val remaining = listsState.value.filterNot { it.id == listId }
        if (remaining.size == listsState.value.size) return
        listsState.value = remaining
        tasksState.value = tasksState.value.filterNot { it.listId == listId }
        if (activeState.value == listId) activeState.value = remaining.firstOrNull()?.id
        persist()
    }

    fun reorderLists(orderedIds: List<String>) {
        val ranks = orderedIds.withIndex().associate { (index, id) -> id to index }
        listsState.value = listsState.value
            .map { list -> ranks[list.id]?.let { list.copy(position = it) } ?: list }
        persist()
    }

    fun setActiveList(listId: String) {
        if (activeState.value == listId || findList(listId) == null) return
        activeState.value = listId
        persist()
    }

    fun setSort(sort: TaskSort) {
        if (sortState.value == sort) return
        sortState.value = sort
        persist()
    }

    fun createTask(listId: String, title: String, parentId: String? = null): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return ""
        val now = clock.nowMillis()
        val id = newId(TASK_PREFIX) { candidate -> tasksState.value.any { it.id == candidate } }
        tasksState.value = tasksState.value + TaskItem(
            id = id,
            listId = listId,
            parentId = parentId,
            title = trimmed,
            position = nextPosition(listId, parentId),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        persist()
        return id
    }

    fun upsertTask(task: TaskItem) {
        val now = clock.nowMillis()
        val current = tasksState.value
        val index = current.indexOfFirst { it.id == task.id }
        tasksState.value = if (index >= 0) {
            current.toMutableList().apply { set(index, task.copy(updatedAtMillis = now)) }
        } else {
            current + task.copy(
                position = nextPosition(task.listId, task.parentId),
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
        persist()
    }

    fun removeTask(taskId: String) {
        val remaining = tasksState.value.filterNot { it.id == taskId || it.parentId == taskId }
        if (remaining.size == tasksState.value.size) return
        tasksState.value = remaining
        persist()
    }

    fun clearCompleted(listId: String) {
        val remaining = tasksState.value.filterNot { it.listId == listId && it.completed }
        if (remaining.size == tasksState.value.size) return
        tasksState.value = remaining
        persist()
    }

    fun setCompleted(taskId: String, completed: Boolean) {
        val target = find(taskId) ?: return
        if (target.completed == completed) return
        val now = clock.nowMillis()
        if (!completed) {
            tasksState.value = tasksState.value.map { item ->
                when {
                    item.id == taskId -> item.reopened(now)
                    item.id == target.parentId && item.completed -> item.reopened(now)
                    else -> item
                }
            }
            persist()
            return
        }
        val rolled = target.repeat?.let { rollForward(target, it, now) }
        tasksState.value = if (rolled != null) {
            val history = target.copy(
                id = newId(TASK_PREFIX) { candidate -> tasksState.value.any { it.id == candidate } },
                repeat = null,
                completed = true,
                completedAtMillis = now,
                updatedAtMillis = now
            )
            tasksState.value.map { if (it.id == taskId) rolled else it } + history
        } else {
            tasksState.value.map { item ->
                if (item.id == taskId || item.parentId == taskId) {
                    if (item.completed) item else item.completedAt(now)
                } else {
                    item
                }
            }
        }
        persist()
    }

    fun indent(taskId: String) {
        val target = find(taskId) ?: return
        if (target.parentId != null) return
        val siblings = sortTasks(
            tasksState.value.filter { it.listId == target.listId && it.parentId == null },
            TaskSort.MY_ORDER
        )
        val index = siblings.indexOfFirst { it.id == taskId }
        val parent = siblings.getOrNull(index - 1) ?: return
        val moved = listOf(target) + sortTasks(
            tasksState.value.filter { it.parentId == taskId },
            TaskSort.MY_ORDER
        )
        val base = nextPosition(target.listId, parent.id)
        val positions = moved.withIndex().associate { (offset, item) -> item.id to base + offset }
        val now = clock.nowMillis()
        tasksState.value = tasksState.value.map { item ->
            if (item.id == parent.id) {
                return@map if (item.repeat == null) item else item.copy(
                    repeat = null,
                    updatedAtMillis = now
                )
            }
            val position = positions[item.id] ?: return@map item
            item.copy(
                parentId = parent.id,
                position = position,
                repeat = null,
                updatedAtMillis = now
            )
        }
        persist()
    }

    fun setStarred(taskId: String, starred: Boolean) {
        val target = find(taskId) ?: return
        if (target.starred == starred) return
        val now = clock.nowMillis()
        replace(
            target.copy(
                starred = starred,
                starredAtMillis = if (starred) now else null,
                updatedAtMillis = now
            )
        )
    }

    fun move(taskId: String, delta: Int) {
        val target = find(taskId) ?: return
        val siblings = sortTasks(
            tasksState.value.filter {
                it.listId == target.listId && it.parentId == target.parentId && !it.completed
            },
            TaskSort.MY_ORDER
        ).toMutableList()
        val from = siblings.indexOfFirst { it.id == taskId }
        val to = from + delta
        if (from < 0 || to < 0 || to >= siblings.size) return
        siblings.add(to, siblings.removeAt(from))
        val positions = siblings.withIndex().associate { (index, item) -> item.id to index }
        tasksState.value = tasksState.value.map { item ->
            val position = positions[item.id] ?: return@map item
            if (item.position == position) item else item.copy(position = position)
        }
        persist()
    }

    fun moveToList(taskId: String, listId: String) {
        val target = find(taskId) ?: return
        if (target.listId == listId || findList(listId) == null) return
        val now = clock.nowMillis()
        val position = nextPosition(listId, null)
        tasksState.value = tasksState.value.map { item ->
            when {
                item.id == taskId -> item.copy(
                    listId = listId,
                    parentId = null,
                    position = position,
                    updatedAtMillis = now
                )
                item.parentId == taskId -> item.copy(listId = listId, updatedAtMillis = now)
                else -> item
            }
        }
        persist()
    }

    fun unindent(taskId: String) {
        val target = find(taskId) ?: return
        val parent = target.parentId?.let { find(it) } ?: return
        val position = parent.position + 1
        val now = clock.nowMillis()
        tasksState.value = tasksState.value.map { item ->
            when {
                item.id == taskId -> item.copy(
                    parentId = null,
                    position = position,
                    updatedAtMillis = now
                )
                item.listId == target.listId && item.parentId == null && item.position >= position ->
                    item.copy(position = item.position + 1)
                else -> item
            }
        }
        persist()
    }

    fun clear() {
        listsState.value = emptyList()
        tasksState.value = emptyList()
        activeState.value = null
        storage.delete(FILE_NAME)
    }

    private fun replace(task: TaskItem) {
        val current = tasksState.value
        val index = current.indexOfFirst { it.id == task.id }
        if (index < 0) return
        tasksState.value = current.toMutableList().apply { set(index, task) }
        persist()
    }

    private fun TaskItem.reopened(now: Long): TaskItem =
        copy(completed = false, completedAtMillis = null, updatedAtMillis = now)

    private fun TaskItem.completedAt(now: Long): TaskItem =
        copy(completed = true, completedAtMillis = now, updatedAtMillis = now)

    private fun rollForward(task: TaskItem, repeat: TaskRepeat, now: Long): TaskItem? {
        if (task.parentId != null) return null
        if (tasksState.value.any { it.parentId == task.id }) return null
        var candidate = task.scheduleAtMillis ?: return null
        var occurrence = task.occurrenceIndex
        var steps = 0
        do {
            candidate = nextOccurrence(repeat, candidate, zone())
            occurrence += 1
            steps += 1
            if (isSeriesFinished(repeat, occurrence, candidate)) return null
        } while (candidate <= now && steps < MAX_ROLL_STEPS)
        if (candidate <= now) return null
        return if (task.dueAtMillis != null) {
            task.copy(dueAtMillis = candidate, occurrenceIndex = occurrence, updatedAtMillis = now)
        } else {
            task.copy(
                deadlineAtMillis = candidate,
                occurrenceIndex = occurrence,
                updatedAtMillis = now
            )
        }
    }

    private fun nextPosition(listId: String, parentId: String?): Int = tasksState.value
        .filter { it.listId == listId && it.parentId == parentId }
        .maxOfOrNull { it.position }
        ?.plus(1)
        ?: 0

    private fun newId(prefix: String, taken: (String) -> Boolean): String {
        val stamp = prefix + clock.nowMillis().toString(RADIX)
        if (!taken(stamp)) return stamp
        var suffix = 1
        while (taken(stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX))) suffix += 1
        return stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX)
    }

    private fun load(): TaskFile {
        val raw = storage.read(FILE_NAME) ?: return TaskFile()
        return try {
            json.decodeFromString(TaskFile.serializer(), raw)
        } catch (error: IllegalArgumentException) {
            TaskFile()
        }
    }

    private fun persist() {
        if (listsState.value.isEmpty() && tasksState.value.isEmpty()) {
            storage.delete(FILE_NAME)
            return
        }
        storage.write(
            FILE_NAME,
            json.encodeToString(
                TaskFile.serializer(),
                TaskFile(
                    lists = listsState.value,
                    tasks = tasksState.value,
                    sort = sortState.value,
                    activeListId = activeState.value
                )
            )
        )
    }

    companion object {
        const val FILE_NAME = "tasks.bin"
        private const val LIST_PREFIX = "list-"
        private const val TASK_PREFIX = "task-"
        private const val SUFFIX_SEPARATOR = "-"
        private const val RADIX = 36
        private const val MAX_ROLL_STEPS = 512
    }
}
