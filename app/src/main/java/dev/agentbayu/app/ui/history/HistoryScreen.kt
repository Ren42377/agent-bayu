package dev.agentbayu.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatSessionMeta
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.InteractiveHighlight
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun HistoryScreen(
    sessions: List<ChatSessionMeta>,
    activeSessionId: String?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    var deleteMode by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatSessionMeta?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            action = {
                GlassIconButton(onClick = { deleteMode = !deleteMode }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.history_delete_mode),
                        tint = if (deleteMode) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                GlassIconButton(onClick = onNew) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.history_new),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(shape = GlassCardShape)
                        .padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            sessions.forEach { session ->
                SessionCard(
                    session = session,
                    isActive = session.id == activeSessionId,
                    showDelete = deleteMode,
                    onOpen = { onOpen(session.id) },
                    onDelete = { pendingDelete = session }
                )
            }
        }
    }

    val pending = pendingDelete
    GlassDialog(
        visible = pending != null,
        title = stringResource(R.string.history_delete),
        body = stringResource(R.string.history_delete_confirm_body),
        confirmLabel = stringResource(R.string.history_delete),
        onConfirm = {
            pendingDelete = null
            pending?.let { onDelete(it.id) }
        },
        dismissLabel = stringResource(R.string.dialog_cancel),
        onDismiss = { pendingDelete = null }
    )
}

@Composable
private fun SessionCard(
    session: ChatSessionMeta,
    isActive: Boolean,
    showDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, claimDrag = false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
            .clip(GlassCardShape)
            .clickable(interactionSource = null, indication = null, onClick = onOpen)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .padding(
                start = 16.dp,
                end = if (showDelete) 4.dp else 16.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title.ifBlank { stringResource(R.string.history_untitled) },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (session.preview.isNotBlank()) {
                Text(
                    text = session.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = DateUtils.getRelativeTimeSpanString(session.updatedAtMillis).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .glassSurface(shape = CircleShape, tint = AppleGreenLight, elevation = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (showDelete) {
            GlassIconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.history_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
