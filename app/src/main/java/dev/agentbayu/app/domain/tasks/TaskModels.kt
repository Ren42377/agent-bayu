package dev.agentbayu.app.domain.tasks

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

@Serializable
enum class TaskSort { MY_ORDER, DATE, STARRED }

@Serializable
data class TaskList(
    val id: String,
    val title: String,
    val position: Int = 0,
    val createdAtMillis: Long = 0L
)

@Serializable
data class TaskRepeat(
    val every: Int = 1,
    val unit: RepeatUnit = RepeatUnit.DAY,
    val weekdays: List<Int> = emptyList(),
    val startAtMillis: Long? = null,
    val endAtMillis: Long? = null,
    val endAfterCount: Int? = null
)

@Serializable
data class TaskItem(
    val id: String,
    val listId: String,
    val parentId: String? = null,
    val title: String = "",
    val details: String = "",
    val dueAtMillis: Long? = null,
    val hasTime: Boolean = false,
    val deadlineAtMillis: Long? = null,
    val repeat: TaskRepeat? = null,
    val starred: Boolean = false,
    val starredAtMillis: Long? = null,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val occurrenceIndex: Int = 0,
    val position: Int = 0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

@Serializable
data class TaskFile(
    val version: Int = 1,
    val lists: List<TaskList> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val sort: TaskSort = TaskSort.MY_ORDER,
    val activeListId: String? = null
)

val TaskItem.isSubtask: Boolean
    get() = parentId != null

val TaskItem.scheduleAtMillis: Long?
    get() = dueAtMillis ?: deadlineAtMillis
