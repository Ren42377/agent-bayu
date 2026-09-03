package dev.agentbayu.app.domain.tasks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val REMINDER_HOUR = 9

private const val MAX_ADVANCE_STEPS = 512

fun triggerAtMillis(task: TaskItem, zone: ZoneId): Long? {
    val due = task.dueAtMillis
    if (due != null) return if (task.hasTime) due else atReminderHour(due, zone)
    val deadline = task.deadlineAtMillis ?: return null
    return atReminderHour(deadline, zone)
}

fun nextTriggerMillis(task: TaskItem, zone: ZoneId, nowMillis: Long): Long? {
    if (task.completed) return null
    val base = triggerAtMillis(task, zone) ?: return null
    if (base > nowMillis) return base
    val repeat = task.repeat ?: return nudgeAtMillis(nowMillis, zone)
    var occurrence = task.occurrenceIndex
    var candidate = base
    var steps = 0
    while (candidate <= nowMillis && steps < MAX_ADVANCE_STEPS) {
        candidate = nextOccurrence(repeat, candidate, zone)
        occurrence += 1
        steps += 1
        if (isSeriesFinished(repeat, occurrence, candidate)) return null
    }
    return candidate.takeIf { it > nowMillis }
}

fun nextOccurrence(repeat: TaskRepeat, fromMillis: Long, zone: ZoneId): Long {
    val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
    val every = repeat.every.coerceAtLeast(1)
    val date = from.toLocalDate()
    val next = when (repeat.unit) {
        RepeatUnit.DAY -> date.plusDays(every.toLong())
        RepeatUnit.WEEK -> nextWeekDate(date, repeat.weekdays.filter { it in 1..7 }.toSet(), every)
        RepeatUnit.MONTH -> date.plusMonths(every.toLong())
        RepeatUnit.YEAR -> date.plusYears(every.toLong())
    }
    return next.atTime(from.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
}

fun isSeriesFinished(repeat: TaskRepeat, occurrenceIndex: Int, atMillis: Long): Boolean {
    val end = repeat.endAtMillis
    if (end != null && atMillis > end) return true
    val count = repeat.endAfterCount
    return count != null && occurrenceIndex >= count
}

fun nudgeAtMillis(nowMillis: Long, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val atHour = atStartOfHour(today, zone)
    return if (atHour > nowMillis) atHour else atStartOfHour(today.plusDays(1), zone)
}

fun sortTasks(tasks: List<TaskItem>, sort: TaskSort): List<TaskItem> = when (sort) {
    TaskSort.MY_ORDER -> tasks.sortedWith(compareBy({ it.position }, { it.createdAtMillis }))
    TaskSort.DATE -> tasks.sortedWith(
        compareBy(
            { it.scheduleAtMillis == null },
            { it.scheduleAtMillis ?: Long.MAX_VALUE },
            { it.position }
        )
    )
    TaskSort.STARRED -> tasks.sortedWith(
        compareBy(
            { !it.starred },
            { -(it.starredAtMillis ?: 0L) },
            { it.position }
        )
    )
}

private fun nextWeekDate(date: LocalDate, weekdays: Set<Int>, every: Int): LocalDate {
    if (weekdays.isEmpty()) return date.plusWeeks(every.toLong())
    val weekStart = date.with(DayOfWeek.MONDAY)
    var candidate = date.plusDays(1)
    while (candidate.isBefore(weekStart.plusWeeks(1))) {
        if (candidate.dayOfWeek.value in weekdays) return candidate
        candidate = candidate.plusDays(1)
    }
    return weekStart.plusWeeks(every.toLong()).with(DayOfWeek.of(weekdays.min()))
}

private fun atReminderHour(atMillis: Long, zone: ZoneId): Long =
    atStartOfHour(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate(), zone)

private fun atStartOfHour(date: LocalDate, zone: ZoneId): Long =
    date.atTime(LocalTime.of(REMINDER_HOUR, 0)).atZone(zone).toInstant().toEpochMilli()
