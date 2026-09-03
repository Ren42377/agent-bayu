package dev.agentbayu.app.domain.tasks

import java.time.LocalDateTime
import java.time.ZoneId

internal val TEST_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")

internal fun at(date: String, time: String): Long = LocalDateTime.parse(date + "T" + time)
    .atZone(TEST_ZONE)
    .toInstant()
    .toEpochMilli()

internal fun task(
    id: String = "task-1",
    listId: String = "list-1",
    parentId: String? = null,
    title: String = "Task",
    dueAtMillis: Long? = null,
    hasTime: Boolean = false,
    deadlineAtMillis: Long? = null,
    repeat: TaskRepeat? = null,
    starred: Boolean = false,
    starredAtMillis: Long? = null,
    completed: Boolean = false,
    occurrenceIndex: Int = 0,
    position: Int = 0
): TaskItem = TaskItem(
    id = id,
    listId = listId,
    parentId = parentId,
    title = title,
    dueAtMillis = dueAtMillis,
    hasTime = hasTime,
    deadlineAtMillis = deadlineAtMillis,
    repeat = repeat,
    starred = starred,
    starredAtMillis = starredAtMillis,
    completed = completed,
    occurrenceIndex = occurrenceIndex,
    position = position
)

internal fun daily(
    every: Int = 1,
    endAtMillis: Long? = null,
    endAfterCount: Int? = null
): TaskRepeat = TaskRepeat(
    every = every,
    unit = RepeatUnit.DAY,
    endAtMillis = endAtMillis,
    endAfterCount = endAfterCount
)
