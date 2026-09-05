package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.domain.tasks.TaskList
import dev.agentbayu.app.domain.tasks.TaskRow
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun TasksScreen(
    lists: List<TaskList>,
    activeList: TaskList?,
    starredOpen: Boolean,
    rows: List<TaskRow>,
    completed: List<TaskItem>,
    notificationsAllowed: Boolean,
    exactAlarmsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    onSelectStarred: () -> Unit,
    onSelectList: (String) -> Unit,
    onNewList: () -> Unit,
    onListMenu: () -> Unit,
    onSortMenu: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    onToggleCompleted: (TaskItem) -> Unit,
    onToggleStarred: (TaskItem) -> Unit,
    onRowMenu: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    var completedOpen by rememberSaveable { mutableStateOf(false) }
    val cardTitle = if (starredOpen) {
        stringResource(R.string.tasks_tab_starred)
    } else {
        activeList?.title ?: stringResource(R.string.tasks_list_default)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding())
        ) {
            Text(
                text = stringResource(R.string.tasks_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
            )
            TaskListTabs(
                lists = lists,
                activeListId = activeList?.id,
                starredOpen = starredOpen,
                onSelectStarred = onSelectStarred,
                onSelectList = onSelectList,
                onNewList = onNewList,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = 96.dp + insets.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                item(key = "card") {
                    TaskCard(
                        title = cardTitle,
                        onSort = if (starredOpen) null else onSortMenu,
                        onMenu = if (starredOpen) null else onListMenu
                    ) {
                        if (rows.isEmpty() && completed.isEmpty()) {
                            Text(
                                text = stringResource(R.string.tasks_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                            )
                        }
                        rows.forEach { row ->
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
                            CompletedHeader(
                                count = completed.size,
                                expanded = completedOpen,
                                onToggle = { completedOpen = !completedOpen }
                            )
                            if (completedOpen) {
                                completed.forEach { task ->
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
                }
            }
        }
        GlassButton(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = 20.dp + insets.calculateBottomPadding()
                )
                .size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
            shape = GlassCardShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.tasks_add),
                modifier = Modifier.size(24.dp)
            )
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
        GlassButton(
            onClick = onAction,
            modifier = Modifier.padding(start = 10.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(text = action, style = MaterialTheme.typography.labelMedium)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
