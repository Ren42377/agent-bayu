package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.GlassOverlay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class QuickTask(
    val title: String,
    val details: String,
    val dueAtMillis: Long?,
    val hasTime: Boolean,
    val starred: Boolean
)

@Composable
internal fun TaskQuickAddSheet(
    visible: Boolean,
    onSave: (QuickTask) -> Unit,
    onOpenDetails: (QuickTask) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(visible) { mutableStateOf("") }
    var details by remember(visible) { mutableStateOf("") }
    var detailsOpen by remember(visible) { mutableStateOf(false) }
    var dueAtMillis by remember(visible) { mutableStateOf<Long?>(null) }
    var hasTime by remember(visible) { mutableStateOf(false) }
    var starred by remember(visible) { mutableStateOf(false) }
    var step by remember(visible) { mutableStateOf(QuickStep.FORM) }

    fun draft(): QuickTask = QuickTask(
        title = title.trim(),
        details = details.trim(),
        dueAtMillis = dueAtMillis,
        hasTime = hasTime,
        starred = starred
    )

    GlassOverlay(visible = visible && step == QuickStep.FORM, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TaskTextField(
                value = title,
                hint = stringResource(R.string.tasks_quick_title_hint),
                onValueChange = { title = it }
            )
            if (detailsOpen) {
                TaskTextField(
                    value = details,
                    hint = stringResource(R.string.tasks_detail_details_hint),
                    onValueChange = { details = it },
                    singleLine = false,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (dueAtMillis != null) {
                Text(
                    text = quickDueLabel(dueAtMillis, hasTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GlassIconButton(onClick = { detailsOpen = !detailsOpen }, size = 36.dp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_note),
                        contentDescription = stringResource(R.string.tasks_detail_details_hint),
                        tint = if (detailsOpen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
                GlassIconButton(
                    onClick = {
                        step = if (dueAtMillis == null) QuickStep.DATE else QuickStep.TIME
                    },
                    size = 36.dp
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = stringResource(R.string.tasks_detail_due),
                        tint = if (dueAtMillis != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
                GlassIconButton(onClick = { starred = !starred }, size = 36.dp) {
                    Icon(
                        painter = painterResource(
                            if (starred) R.drawable.ic_star else R.drawable.ic_star_outline
                        ),
                        contentDescription = stringResource(R.string.tasks_star),
                        tint = if (starred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                GlassButton(
                    onClick = { onOpenDetails(draft()) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_quick_more),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                GlassButton(
                    onClick = { onSave(draft()) },
                    enabled = title.isNotBlank(),
                    tint = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_save),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    TaskDatePickerDialog(
        visible = visible && step == QuickStep.DATE,
        title = stringResource(R.string.tasks_detail_due),
        initialDate = dueAtMillis?.let { localDate(it) },
        onSelect = { date ->
            val time = dueAtMillis?.takeIf { hasTime }?.let { localTime(it) }
            dueAtMillis = if (time == null) {
                startOfDay(date)
            } else {
                atTime(date, time.hour, time.minute)
            }
        },
        onClear = {
            dueAtMillis = null
            hasTime = false
        },
        onDismiss = { step = QuickStep.FORM }
    )

    TaskTimePickerDialog(
        visible = visible && step == QuickStep.TIME,
        title = stringResource(R.string.tasks_detail_time),
        initialHour = dueAtMillis?.let { localTime(it).hour } ?: DEFAULT_HOUR,
        initialMinute = dueAtMillis?.let { localTime(it).minute } ?: 0,
        onSelect = { hour, minute ->
            val date = dueAtMillis?.let { localDate(it) } ?: LocalDate.now(ZoneId.systemDefault())
            dueAtMillis = atTime(date, hour, minute)
            hasTime = true
        },
        onClear = { hasTime = false },
        onDismiss = { step = QuickStep.FORM }
    )
}

private enum class QuickStep { FORM, DATE, TIME }

@Composable
private fun quickDueLabel(atMillis: Long?, hasTime: Boolean): String {
    if (atMillis == null) return stringResource(R.string.tasks_date_none)
    val day = dayLabel(atMillis)
    return if (hasTime) stringResource(R.string.tasks_due_with_time, day, timeLabel(atMillis)) else day
}

private fun localDate(atMillis: Long): LocalDate = Instant.ofEpochMilli(atMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()

private fun localTime(atMillis: Long): LocalTime = Instant.ofEpochMilli(atMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalTime()

private fun startOfDay(date: LocalDate): Long = date
    .atStartOfDay(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private fun atTime(date: LocalDate, hour: Int, minute: Int): Long = date
    .atTime(hour, minute)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private const val DEFAULT_HOUR = 9
