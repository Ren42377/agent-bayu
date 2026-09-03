package dev.agentbayu.app.domain.tasks

data class TaskRow(
    val task: TaskItem,
    val subtask: Boolean,
    val hasSubtasks: Boolean
)

fun pendingRows(tasks: List<TaskItem>, listId: String, sort: TaskSort): List<TaskRow> {
    val inList = tasks.filter { it.listId == listId }
    val ids = inList.map { it.id }.toSet()
    val children = inList
        .filter { !it.completed && it.parentId != null && it.parentId in ids }
        .groupBy { it.parentId }
    val parents = inList.filter { !it.completed && (it.parentId == null || it.parentId !in ids) }
    return sortTasks(parents, sort).flatMap { parent ->
        val kids = sortTasks(children[parent.id].orEmpty(), TaskSort.MY_ORDER)
        listOf(TaskRow(parent, subtask = false, hasSubtasks = kids.isNotEmpty())) +
            kids.map { TaskRow(it, subtask = true, hasSubtasks = false) }
    }
}

fun completedTasks(tasks: List<TaskItem>, listId: String): List<TaskItem> = tasks
    .filter { it.listId == listId && it.completed }
    .sortedByDescending { it.completedAtMillis ?: it.updatedAtMillis }

fun subtasksOf(tasks: List<TaskItem>, taskId: String): List<TaskItem> = sortTasks(
    tasks.filter { it.parentId == taskId },
    TaskSort.MY_ORDER
)

fun canRepeat(tasks: List<TaskItem>, task: TaskItem): Boolean =
    task.parentId == null && tasks.none { it.parentId == task.id }
