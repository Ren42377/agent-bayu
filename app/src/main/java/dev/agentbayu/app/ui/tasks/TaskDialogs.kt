package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassOverlay
import dev.agentbayu.app.ui.theme.GlassTileShape

internal data class TaskAction(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
internal fun TaskActionSheet(
    visible: Boolean,
    title: String,
    actions: List<TaskAction>,
    onDismiss: () -> Unit
) {
    GlassOverlay(visible = visible, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            actions.forEach { action ->
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (action.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassTileShape)
                        .clickable {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
internal fun TaskTextDialog(
    visible: Boolean,
    title: String,
    hint: String,
    initialValue: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(visible, initialValue) { mutableStateOf(initialValue) }
    GlassOverlay(visible = visible, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(text = hint, style = MaterialTheme.typography.bodyMedium)
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
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
                        text = dismissLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassButton(
                    onClick = { onConfirm(value.trim()) },
                    modifier = Modifier.weight(1f),
                    enabled = value.isNotBlank(),
                    tint = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = confirmLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
