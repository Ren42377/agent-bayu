package dev.agentbayu.app.ui.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.ui.theme.GlassTileShape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskItem
import dev.agentbayu.app.ui.components.pressScaleFeedback
import dev.agentbayu.app.ui.theme.AgentBayuMotion

@Composable
internal fun TaskRowItem(
    task: TaskItem,
    subtask: Boolean,
    onOpen: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleStarred: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val overdue = !task.completed && task.overdue()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassTileShape)
            .combinedClickable(onLongClick = onMenu, onClick = onOpen)
            .pressScaleFeedback()
            .padding(
                start = if (subtask) 44.dp else 16.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        CompleteCircle(completed = task.completed, onClick = onToggleCompleted)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = task.completed,
                transitionSpec = {
                    fadeIn(AgentBayuMotion.quickFade) togetherWith
                        fadeOut(AgentBayuMotion.quickFade)
                },
                label = "taskTitle"
            ) { completed ->
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (completed) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val details = task.details.trim()
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TaskRowMeta(task = task, overdue = overdue)
        }
        StarButton(starred = task.starred, onClick = onToggleStarred)
    }
}

@Composable
private fun TaskRowMeta(task: TaskItem, overdue: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val schedule = taskScheduleLabel(task)
    val repeats = task.repeat != null
    if (schedule == null && !repeats) return
    val color = when {
        overdue -> scheme.error
        schedule != null -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (schedule != null) {
            Icon(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = schedule,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        if (repeats) {
            Icon(
                painter = painterResource(R.drawable.ic_repeat),
                contentDescription = stringResource(R.string.tasks_repeat_badge),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun CompleteCircle(completed: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val backgroundColor by animateColorAsState(
        targetValue = if (completed) scheme.primary else Color.Transparent,
        label = "completeCircleBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (completed) scheme.primary else scheme.outline,
        label = "completeCircleBorder"
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(color = backgroundColor, shape = CircleShape)
            .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = completed,
            enter = scaleIn(
                initialScale = CHECK_ENTER_SCALE,
                animationSpec = AgentBayuMotion.snappySpring
            ) + fadeIn(AgentBayuMotion.quickFade),
            exit = scaleOut(
                targetScale = CHECK_ENTER_SCALE,
                animationSpec = AgentBayuMotion.snappySpring
            ) + fadeOut(AgentBayuMotion.quickFade)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun StarButton(starred: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val starTint by animateColorAsState(
        targetValue = if (starred) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.6f),
        label = "starTint"
    )
    AnimatedContent(
        targetState = starred,
        transitionSpec = {
            (fadeIn(AgentBayuMotion.quickFade) + scaleIn(initialScale = STAR_ENTER_SCALE)) togetherWith
                (fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = STAR_ENTER_SCALE))
        },
        label = "starIcon"
    ) { starredNow ->
        Icon(
            painter = painterResource(
                if (starredNow) R.drawable.ic_star else R.drawable.ic_star_outline
            ),
            contentDescription = stringResource(
                if (starredNow) R.string.tasks_unstar else R.string.tasks_star
            ),
            tint = starTint,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(6.dp)
        )
    }
}

private const val CHECK_ENTER_SCALE = 0.5f
private const val STAR_ENTER_SCALE = 0.85f
