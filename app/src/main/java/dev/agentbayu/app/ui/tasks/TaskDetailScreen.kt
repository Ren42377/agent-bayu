package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskList
import dev.agentbayu.app.domain.tasks.TaskRepeat
import dev.agentbayu.app.ui.ai.AiDropdown
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassToggle
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TaskDetailScreen(
    isNew: Boolean,
    draft: TaskDraft,
    lists: List<TaskList>,
    subtasks: List<TaskItem>,
    repeatAllowed: Boolean,
    showSubtasks: Boolean,
    onDraftChange: (TaskDraft) -> Unit,
    onSubtaskAdd: (String) -> Unit,
    onSubtaskToggle: (TaskItem) -> Unit,
    onSubtaskDelete: (TaskItem) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    var duePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var deadlinePicker by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(
            title = stringResource(
                if (isNew) R.string.tasks_detail_new else R.string.tasks_detail_title
            ),
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TaskSection(title = stringResource(R.string.tasks_detail_title)) {
                TaskTextField(
                    value = draft.title,
                    hint = stringResource(R.string.tasks_detail_title_hint),
                    onValueChange = { onDraftChange(draft.copy(title = it)) }
                )
                TaskTextField(
                    value = draft.details,
                    hint = stringResource(R.string.tasks_detail_details_hint),
                    onValueChange = { onDraftChange(draft.copy(details = it)) },
                    singleLine = false,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            TaskSection(title = stringResource(R.string.tasks_detail_schedule)) {
                TaskFieldRow(
                    label = stringResource(R.string.tasks_detail_due),
                    value = dateFieldLabel(draft.dueAtMillis),
                    onClick = { duePicker = true }
                )
                TaskFieldRow(
                    label = stringResource(R.string.tasks_detail_time),
                    value = if (draft.hasTime && draft.dueAtMillis != null) {
                        timeLabel(draft.dueAtMillis)
                    } else {
                        stringResource(R.string.tasks_date_none)
                    },
                    onClick = { timePicker = true }
                )
                TaskFieldRow(
                    label = stringResource(R.string.tasks_detail_deadline),
                    value = dateFieldLabel(draft.deadlineAtMillis),
                    onClick = { deadlinePicker = true }
                )
            }

            TaskRepeatSection(
                repeat = draft.repeat,
                allowed = repeatAllowed,
                onRepeatChange = { onDraftChange(draft.copy(repeat = it)) }
            )

            TaskSection(title = stringResource(R.string.tasks_detail_organize)) {
                AiDropdown(
                    selectedLabel = lists.firstOrNull { it.id == draft.listId }?.title
                        ?: stringResource(R.string.tasks_list_default),
                    options = lists.map { it.id to it.title },
                    onSelect = { onDraftChange(draft.copy(listId = it)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_starred),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    GlassToggle(
                        checked = draft.starred,
                        onCheckedChange = { onDraftChange(draft.copy(starred = it)) }
                    )
                }
            }

            if (showSubtasks) {
                TaskSection(title = stringResource(R.string.tasks_detail_subtasks)) {
                    subtasks.forEach { subtask ->
                        SubtaskRow(
                            task = subtask,
                            onToggle = { onSubtaskToggle(subtask) },
                            onDelete = { onSubtaskDelete(subtask) }
                        )
                    }
                    SubtaskField(onAdd = onSubtaskAdd)
                }
            }

            if (!isNew) {
                Text(
                    text = stringResource(R.string.tasks_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDelete)
                        .padding(vertical = 12.dp)
                )
            }
        }
        ActionBar(
            insets = insets.calculateBottomPadding(),
            onCancel = onBack,
            onSave = onSave
        )
    }

    TaskDatePickerDialog(
        visible = duePicker,
        title = stringResource(R.string.tasks_detail_due),
        initialDate = draft.dueAtMillis?.let { localDateOf(it) },
        onSelect = { date ->
            onDraftChange(
                draft.copy(dueAtMillis = mergeDate(date, draft.dueAtMillis, draft.hasTime))
            )
        },
        onClear = { onDraftChange(draft.copy(dueAtMillis = null, hasTime = false)) },
        onDismiss = { duePicker = false }
    )

    TaskTimePickerDialog(
        visible = timePicker,
        title = stringResource(R.string.tasks_detail_time),
        initialHour = draft.dueAtMillis?.let { localTimeOf(it).hour } ?: DEFAULT_HOUR,
        initialMinute = draft.dueAtMillis?.let { localTimeOf(it).minute } ?: 0,
        onSelect = { hour, minute ->
            onDraftChange(
                draft.copy(
                    dueAtMillis = mergeTime(draft.dueAtMillis, hour, minute),
                    hasTime = true
                )
            )
        },
        onClear = {
            val date = draft.dueAtMillis?.let { localDateOf(it) }
            onDraftChange(
                draft.copy(
                    dueAtMillis = date?.let { startOfDay(it) },
                    hasTime = false
                )
            )
        },
        onDismiss = { timePicker = false }
    )

    TaskDatePickerDialog(
        visible = deadlinePicker,
        title = stringResource(R.string.tasks_detail_deadline),
        initialDate = draft.deadlineAtMillis?.let { localDateOf(it) },
        onSelect = { date -> onDraftChange(draft.copy(deadlineAtMillis = startOfDay(date))) },
        onClear = { onDraftChange(draft.copy(deadlineAtMillis = null)) },
        onDismiss = { deadlinePicker = false }
    )
}

data class TaskDraft(
    val title: String = "",
    val details: String = "",
    val dueAtMillis: Long? = null,
    val hasTime: Boolean = false,
    val deadlineAtMillis: Long? = null,
    val repeat: TaskRepeat? = null,
    val starred: Boolean = false,
    val listId: String = ""
)

@Composable
private fun SubtaskRow(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (task.completed) scheme.primary else Color.Transparent,
                    shape = CircleShape
                )
                .border(
                    width = 1.5.dp,
                    color = if (task.completed) scheme.primary else scheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (task.completed) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (task.completed) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.tasks_delete),
            tint = scheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDelete)
        )
    }
}

@Composable
private fun SubtaskField(onAdd: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TaskTextField(
                value = value,
                hint = stringResource(R.string.tasks_detail_subtask_add),
                onValueChange = { value = it },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        GlassButton(
            onClick = {
                val title = value.trim()
                if (title.isNotEmpty()) {
                    value = ""
                    onAdd(title)
                }
            },
            contentPadding = PaddingValues(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.tasks_detail_subtask_add),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ActionBar(
    insets: Dp,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = insets + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.tasks_detail_cancel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GlassButton(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.tasks_detail_save),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

private fun localDateOf(atMillis: Long): LocalDate = Instant.ofEpochMilli(atMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()

private fun localTimeOf(atMillis: Long): LocalTime = Instant.ofEpochMilli(atMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()

private fun startOfDay(date: LocalDate): Long = date
    .atStartOfDay(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private fun atTimeMillis(date: LocalDate, hour: Int, minute: Int): Long = date
    .atTime(hour, minute)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private fun mergeDate(date: LocalDate, previous: Long?, hasTime: Boolean): Long {
    if (!hasTime || previous == null) return startOfDay(date)
    val time = localTimeOf(previous)
    return atTimeMillis(date, time.hour, time.minute)
}

private fun mergeTime(previous: Long?, hour: Int, minute: Int): Long {
    val date = previous?.let { localDateOf(it) } ?: LocalDate.now(ZoneId.systemDefault())
    return atTimeMillis(date, hour, minute)
}

private const val DEFAULT_HOUR = 9


