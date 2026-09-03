package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.RepeatUnit
import dev.agentbayu.app.domain.tasks.TaskRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun TaskRepeatSection(
    repeat: TaskRepeat?,
    allowed: Boolean,
    onRepeatChange: (TaskRepeat?) -> Unit
) {
    var unitSheet by remember { mutableStateOf(false) }
    var endsSheet by remember { mutableStateOf(false) }
    var endPicker by remember { mutableStateOf(false) }
    TaskSection(title = stringResource(R.string.tasks_detail_repeat)) {
        TaskFieldRow(
            label = stringResource(R.string.tasks_detail_repeat),
            value = repeatLabel(repeat),
            onClick = if (allowed) {
                { unitSheet = true }
            } else {
                null
            }
        )
        if (!allowed) {
            Text(
                text = stringResource(R.string.tasks_detail_repeat_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (allowed && repeat != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tasks_detail_repeat_interval),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TaskNumberField(
                    value = repeat.every,
                    max = MAX_INTERVAL,
                    onValueChange = { onRepeatChange(repeat.copy(every = it)) }
                )
                Text(
                    text = repeatUnitLabel(repeat.unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            if (repeat.unit == RepeatUnit.WEEK) {
                Text(
                    text = stringResource(R.string.tasks_detail_repeat_weekdays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WeekdayPicker(
                    selected = repeat.weekdays,
                    onToggle = { day ->
                        val next = if (day in repeat.weekdays) {
                            repeat.weekdays - day
                        } else {
                            repeat.weekdays + day
                        }
                        onRepeatChange(repeat.copy(weekdays = next.sorted()))
                    }
                )
            }
            TaskFieldRow(
                label = stringResource(R.string.tasks_detail_repeat_ends),
                value = endsLabel(repeat),
                onClick = { endsSheet = true }
            )
            if (repeat.endAfterCount != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_repeat_count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TaskNumberField(
                        value = repeat.endAfterCount,
                        max = MAX_COUNT,
                        onValueChange = { onRepeatChange(repeat.copy(endAfterCount = it)) }
                    )
                }
            }
        }
    }

    TaskActionSheet(
        visible = unitSheet,
        title = stringResource(R.string.tasks_detail_repeat),
        actions = unitActions(repeat, onRepeatChange),
        onDismiss = { unitSheet = false }
    )

    TaskActionSheet(
        visible = endsSheet && repeat != null,
        title = stringResource(R.string.tasks_detail_repeat_ends),
        actions = listOf(
            TaskAction(label = stringResource(R.string.tasks_detail_repeat_ends_never)) {
                repeat?.let {
                    onRepeatChange(it.copy(endAtMillis = null, endAfterCount = null))
                }
            },
            TaskAction(label = stringResource(R.string.tasks_detail_repeat_ends_on)) {
                endPicker = true
            },
            TaskAction(label = stringResource(R.string.tasks_detail_repeat_ends_after_option)) {
                repeat?.let {
                    onRepeatChange(
                        it.copy(endAtMillis = null, endAfterCount = it.endAfterCount ?: 1)
                    )
                }
            }
        ),
        onDismiss = { endsSheet = false }
    )

    TaskDatePickerDialog(
        visible = endPicker && repeat != null,
        title = stringResource(R.string.tasks_detail_repeat_ends),
        initialDate = repeat?.endAtMillis?.let { localDateOf(it) },
        onSelect = { date ->
            repeat?.let {
                onRepeatChange(
                    it.copy(endAtMillis = endOfDayMillis(date), endAfterCount = null)
                )
            }
        },
        onClear = {
            repeat?.let { onRepeatChange(it.copy(endAtMillis = null)) }
        },
        onDismiss = { endPicker = false }
    )
}

@Composable
private fun unitActions(
    repeat: TaskRepeat?,
    onRepeatChange: (TaskRepeat?) -> Unit
): List<TaskAction> {
    val actions = mutableListOf(
        TaskAction(label = stringResource(R.string.tasks_detail_repeat_none)) {
            onRepeatChange(null)
        }
    )
    RepeatUnit.entries.forEach { unit ->
        val label = stringResource(
            R.string.tasks_detail_repeat_every,
            repeat?.every?.takeIf { repeat.unit == unit } ?: 1,
            repeatUnitLabel(unit)
        )
        actions += TaskAction(label = label) {
            onRepeatChange(
                repeat?.copy(unit = unit) ?: TaskRepeat(every = 1, unit = unit)
            )
        }
    }
    return actions
}

@Composable
private fun endsLabel(repeat: TaskRepeat): String {
    val end = repeat.endAtMillis
    if (end != null) return dayLabel(end)
    val count = repeat.endAfterCount
    if (count != null) return stringResource(R.string.tasks_detail_repeat_ends_after, count)
    return stringResource(R.string.tasks_detail_repeat_ends_never)
}

@Composable
private fun WeekdayPicker(selected: List<Int>, onToggle: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WEEK_DAYS.forEach { day ->
            val active = day.value in selected
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = CircleShape
                    )
                    .clickable { onToggle(day.value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun localDateOf(atMillis: Long): LocalDate = Instant.ofEpochMilli(atMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()

private fun endOfDayMillis(date: LocalDate): Long = date
    .atTime(LocalTime.MAX)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private const val MAX_INTERVAL = 99
private const val MAX_COUNT = 99
