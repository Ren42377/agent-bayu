package dev.agentbayu.app.ui.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.RepeatUnit
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskRepeat
import dev.agentbayu.app.domain.tasks.TaskSort
import dev.agentbayu.app.domain.tasks.scheduleAtMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun taskScheduleLabel(task: TaskItem): String? {
    val due = task.dueAtMillis
    if (due != null) {
        val day = dayLabel(due)
        return if (task.hasTime) {
            stringResource(R.string.tasks_due_with_time, day, timeLabel(due))
        } else {
            day
        }
    }
    val deadline = task.deadlineAtMillis ?: return null
    return stringResource(R.string.tasks_deadline_label, dayLabel(deadline))
}

@Composable
internal fun dayLabel(atMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> stringResource(R.string.tasks_due_today)
        today.plusDays(1) -> stringResource(R.string.tasks_due_tomorrow)
        today.minusDays(1) -> stringResource(R.string.tasks_due_yesterday)
        else -> {
            val pattern = if (date.year == today.year) DAY_PATTERN else DAY_YEAR_PATTERN
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }
    }
}

@Composable
internal fun dateFieldLabel(atMillis: Long?): String =
    if (atMillis == null) stringResource(R.string.tasks_date_none) else dayLabel(atMillis)

internal fun timeLabel(atMillis: Long): String = Instant.ofEpochMilli(atMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(TIME_PATTERN, Locale.getDefault()))

@Composable
internal fun repeatUnitLabel(unit: RepeatUnit): String = stringResource(
    when (unit) {
        RepeatUnit.DAY -> R.string.tasks_unit_day
        RepeatUnit.WEEK -> R.string.tasks_unit_week
        RepeatUnit.MONTH -> R.string.tasks_unit_month
        RepeatUnit.YEAR -> R.string.tasks_unit_year
    }
)

@Composable
internal fun repeatLabel(repeat: TaskRepeat?): String {
    if (repeat == null) return stringResource(R.string.tasks_detail_repeat_none)
    return stringResource(
        R.string.tasks_detail_repeat_every,
        repeat.every,
        repeatUnitLabel(repeat.unit)
    )
}

@Composable
internal fun sortLabel(sort: TaskSort): String = stringResource(
    when (sort) {
        TaskSort.MY_ORDER -> R.string.tasks_sort_my_order
        TaskSort.DATE -> R.string.tasks_sort_date
        TaskSort.STARRED -> R.string.tasks_sort_starred
    }
)

internal fun TaskItem.overdue(): Boolean {
    val at = scheduleAtMillis ?: return false
    val zone = ZoneId.systemDefault()
    if (hasTime && dueAtMillis != null) return at < System.currentTimeMillis()
    val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    return date.isBefore(LocalDate.now(zone))
}

private const val DAY_PATTERN = "d MMM"
private const val DAY_YEAR_PATTERN = "d MMM yyyy"
private const val TIME_PATTERN = "HH:mm"
