package dev.agentbayu.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.MarkdownMessage
import dev.agentbayu.app.ui.tasks.TaskTextField
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets

@Composable
fun NoteEditorScreen(
    isNew: Boolean,
    draft: NoteDraft,
    onDraftChange: (NoteDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    var preview by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(
            title = stringResource(
                if (isNew) R.string.notes_editor_new else R.string.notes_editor_title
            ),
            onBack = onBack
        ) {
            GlassIconButton(
                onClick = { onDraftChange(draft.copy(pinned = !draft.pinned)) },
                size = 38.dp
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = stringResource(
                        if (draft.pinned) R.string.notes_unpin else R.string.notes_pin
                    ),
                    tint = if (draft.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
            GlassIconButton(
                onClick = { preview = !preview },
                size = 38.dp
            ) {
                Icon(
                    painter = painterResource(
                        if (preview) R.drawable.ic_edit else R.drawable.ic_visibility
                    ),
                    contentDescription = stringResource(
                        if (preview) R.string.notes_editor_write
                        else R.string.notes_editor_preview
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TaskTextField(
                value = draft.title,
                hint = stringResource(R.string.notes_editor_title_hint),
                onValueChange = { onDraftChange(draft.copy(title = it)) }
            )
            if (preview) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                ) {
                    MarkdownMessage(
                        content = draft.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { onDraftChange(draft.copy(content = it)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.notes_editor_content_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
            if (!isNew) {
                Text(
                    text = stringResource(R.string.notes_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CapsuleShape)
                        .clickable(onClick = onDelete)
                        .padding(vertical = 12.dp)
                )
            }
        }
        EditorActionBar(
            insets = insets.calculateBottomPadding(),
            onCancel = onBack,
            onSave = onSave
        )
    }
}

@Composable
private fun EditorActionBar(
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
