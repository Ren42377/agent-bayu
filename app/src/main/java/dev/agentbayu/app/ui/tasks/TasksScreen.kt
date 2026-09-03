package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskList
import dev.agentbayu.app.domain.tasks.TaskRow
import dev.agentbayu.app.domain.tasks.TaskSort
import dev.agentbayu.app.ui.ai.AiDropdown
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassSegmentedSelector
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun TasksScreen(
    lists: List<TaskList>,
    activeList: TaskList?,
    rows: List<TaskRow>,
    completed: List<TaskItem>,
    sort: TaskSort,
    notificationsAllowed: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onSelectList: (String) -> Unit,
    onListMenu: () -> Unit,
    onSortChange: (TaskSort) -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    onToggleCompleted: (TaskItem) -> Unit,
    onToggleStarred: (TaskItem) -> Unit,
    onRowMenu: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    var completedOpen by rememberSaveable { mutableStateOf(false) }
    val sorts = remember { TaskSort.entries }
    val sortLabels = sorts.map { sortLabel(it) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tasks_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                onClick = onListMenu,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pending),
                    contentDescription = stringResource(R.string.tasks_list_menu),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AiDropdown(
                selectedLabel = activeList?.title
                    ?: stringResource(R.string.tasks_list_default),
                options = lists.map { it.id to it.title },
                onSelect = onSelectList
            )
            GlassSegmentedSelector(
                labels = sortLabels,
                selectedIndex = sorts.indexOf(sort).coerceAtLeast(0),
                onSelect = { index -> onSortChange(sorts[index]) }
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!notificationsAllowed) {
                item(key = "notice-notifications") {
                    TaskNotice(
                        title = stringResource(R.string.tasks_permission_card_title),
                        body = stringResource(R.string.tasks_permission_card_body),
                        action = stringResource(R.string.tasks_permission_card_action),
                        onAction = onRequestNotifications
                    )
                }
            }
            if (notificationsAllowed && !exactAlarmsAllowed) {
                item(key = "notice-exact") {
                    TaskNotice(
                        title = stringResource(R.string.tasks_exact_card_title),
                        body = stringResource(R.string.tasks_exact_card_body),
                        action = stringResource(R.string.tasks_exact_card_action),
                        onAction = onRequestExactAlarms
                    )
                }
            }
            if (rows.isEmpty() && completed.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp)
                    )
                }
            }
            items(items = rows, key = { it.task.id }) { row ->
                TaskRowItem(
                    task = row.task,
                    subtask = row.subtask,
                    onOpen = { onOpenTask(row.task) },
                    onToggleCompleted = { onToggleCompleted(row.task) },
                    onToggleStarred = { onToggleStarred(row.task) },
                    onMenu = { onRowMenu(row.task) }
                )
            }
            if (completed.isNotEmpty()) {
                item(key = "completed-header") {
                    CompletedHeader(
                        count = completed.size,
                        expanded = completedOpen,
                        onToggle = { completedOpen = !completedOpen }
                    )
                }
                if (completedOpen) {
                    items(items = completed, key = { it.id }) { task ->
                        TaskRowItem(
                            task = task,
                            subtask = task.parentId != null,
                            onOpen = { onOpenTask(task) },
                            onToggleCompleted = { onToggleCompleted(task) },
                            onToggleStarred = { onToggleStarred(task) },
                            onMenu = { onRowMenu(task) }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 12.dp + insets.calculateBottomPadding()
                )
        ) {
            GlassButton(
                onClick = onAddTask,
                modifier = Modifier.fillMaxWidth(),
                tint = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tasks_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TaskNotice(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        GlassButton(
            onClick = onAction,
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun CompletedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.tasks_completed_section, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .size(16.dp)
                .rotate(if (expanded) 270f else 90f)
        )
    }
}
