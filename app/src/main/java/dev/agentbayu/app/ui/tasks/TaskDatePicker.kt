package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassOverlay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
internal fun TaskDatePickerDialog(
    visible: Boolean,
    title: String,
    initialDate: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val selected = initialDate ?: today
    var month by remember(visible, selected) { mutableStateOf(YearMonth.from(selected)) }
    var mode by remember(visible) { mutableStateOf(DatePickerMode.DAYS) }
    GlassOverlay(visible = visible, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            MonthHeader(
                month = month,
                mode = mode,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onMonthClick = {
                    mode = if (mode == DatePickerMode.MONTHS) {
                        DatePickerMode.DAYS
                    } else {
                        DatePickerMode.MONTHS
                    }
                },
                onYearClick = {
                    mode = if (mode == DatePickerMode.YEARS) {
                        DatePickerMode.DAYS
                    } else {
                        DatePickerMode.YEARS
                    }
                }
            )
            when (mode) {
                DatePickerMode.DAYS -> {
                    WeekdayHeader()
                    MonthGrid(
                        month = month,
                        selected = initialDate,
                        today = today,
                        onSelect = { date ->
                            onDismiss()
                            onSelect(date)
                        }
                    )
                }
                DatePickerMode.MONTHS -> MonthPicker(
                    month = month,
                    onSelect = { value ->
                        month = month.withMonth(value.value)
                        mode = DatePickerMode.DAYS
                    }
                )
                DatePickerMode.YEARS -> YearPicker(
                    month = month,
                    today = today,
                    onSelect = { year ->
                        month = month.withYear(year)
                        mode = DatePickerMode.DAYS
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassButton(
                    onClick = {
                        onDismiss()
                        onClear()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_clear_value),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    mode: DatePickerMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMonthClick: () -> Unit,
    onYearClick: () -> Unit
) {
    val locale = currentLocale()
    val monthLabel = remember(month, locale) {
        month.atDay(1).format(DateTimeFormatter.ofPattern(MONTH_NAME_PATTERN, locale))
    }
    val yearLabel = remember(month, locale) {
        month.atDay(1).format(DateTimeFormatter.ofPattern(YEAR_PATTERN, locale))
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mode == DatePickerMode.DAYS) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .rotate(180f)
                    .clickable(onClick = onPrevious)
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderLabel(
                label = monthLabel,
                active = mode == DatePickerMode.MONTHS,
                onClick = onMonthClick
            )
            HeaderLabel(
                label = yearLabel,
                active = mode == DatePickerMode.YEARS,
                onClick = onYearClick
            )
        }
        if (mode == DatePickerMode.DAYS) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onNext)
            )
        }
    }
}

@Composable
private fun HeaderLabel(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun WeekdayHeader() {
    val locale = currentLocale()
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEK_DAYS.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val cells = remember(month) { monthCells(month) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                selected = date == selected,
                                today = date == today,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (selected) scheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                selected -> Color.White
                today -> scheme.primary
                else -> scheme.onSurface
            }
        )
    }
}

@Composable
private fun MonthPicker(
    month: YearMonth,
    onSelect: (Month) -> Unit
) {
    val locale = currentLocale()
    val labels = remember(locale) {
        Month.values().map { value -> value to value.getDisplayName(TextStyle.FULL, locale) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.chunked(MONTH_COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (value, label) ->
                    PickerCell(
                        label = label,
                        selected = value == month.month,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun YearPicker(
    month: YearMonth,
    today: LocalDate,
    onSelect: (Int) -> Unit
) {
    val rows = remember(today) {
        (today.year - YEARS_BACK..today.year + YEARS_FORWARD).toList().chunked(YEAR_COLUMNS)
    }
    val state = rememberLazyListState()
    LaunchedEffect(rows, month.year) {
        val index = rows.indexOfFirst { row -> row.contains(month.year) }
        if (index >= 0) {
            state.scrollToItem(index)
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = YEAR_LIST_MAX_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items = rows, key = { row -> row.first() }) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { year ->
                    PickerCell(
                        label = year.toString(),
                        selected = year == month.year,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(year) }
                    )
                }
                repeat(YEAR_COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color = if (selected) scheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val blanks = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List(blanks) { null } + days
    val tail = (DAYS_IN_WEEK - cells.size % DAYS_IN_WEEK) % DAYS_IN_WEEK
    return cells + List(tail) { null }
}

internal val WEEK_DAYS = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

private enum class DatePickerMode { DAYS, MONTHS, YEARS }

private const val DAYS_IN_WEEK = 7
private const val MONTH_COLUMNS = 3
private const val YEAR_COLUMNS = 4
private const val YEARS_BACK = 20
private const val YEARS_FORWARD = 20
private val YEAR_LIST_MAX_HEIGHT = 260.dp
private const val MONTH_NAME_PATTERN = "MMMM"
private const val YEAR_PATTERN = "yyyy"
