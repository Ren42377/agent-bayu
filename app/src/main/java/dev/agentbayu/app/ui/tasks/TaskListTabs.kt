package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskList
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassTileShape

@Composable
internal fun TaskListTabs(
    lists: List<TaskList>,
    activeListId: String?,
    starredOpen: Boolean,
    onSelectStarred: () -> Unit,
    onSelectList: (String) -> Unit,
    onNewList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskTab(selected = starredOpen, onClick = onSelectStarred) {
            Icon(
                painter = painterResource(
                    if (starredOpen) R.drawable.ic_star else R.drawable.ic_star_outline
                ),
                contentDescription = stringResource(R.string.tasks_tab_starred),
                tint = tabColor(starredOpen),
                modifier = Modifier.size(18.dp)
            )
        }
        lists.forEach { list ->
            val selected = !starredOpen && list.id == activeListId
            TaskTab(selected = selected, onClick = { onSelectList(list.id) }) {
                Text(
                    text = list.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = tabColor(selected),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(CapsuleShape)
                .clickable(onClick = onNewList)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.tasks_tab_new_list),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TaskTab(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(GlassTileShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
        Spacer(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(INDICATOR_HEIGHT)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    shape = CapsuleShape
                )
        )
    }
}

@Composable
private fun tabColor(selected: Boolean) = if (selected) {
    MaterialTheme.colorScheme.primary
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

private val INDICATOR_HEIGHT = 2.dp
