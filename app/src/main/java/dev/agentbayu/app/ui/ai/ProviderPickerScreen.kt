package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassOverlay
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.glassSurface

data class ProviderOption(
    val connectionId: String,
    val label: String,
    val providerLabel: String,
    val model: String,
    val models: List<String>,
    val authKind: AuthKind,
    val isActive: Boolean,
    val ready: Boolean
)

@Composable
fun ProviderPickerDialog(
    options: List<ProviderOption>,
    onSelect: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassOverlay(onDismiss = onDismiss) {
        PickerContent(
            options = options,
            onSelect = onSelect,
            onSelectModel = onSelectModel,
            onManage = onManage,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun PickerContent(
    options: List<ProviderOption>,
    onSelect: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = MAX_PICKER_HEIGHT)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        options.forEach { option ->
            OptionRow(
                option = option,
                onSelect = {
                    onSelect(option.connectionId)
                    onDismiss()
                }
            )
            if (option.isActive) {
                ModelList(
                    option = option,
                    onSelectModel = { modelId ->
                        onSelectModel(option.connectionId, modelId)
                        onDismiss()
                    }
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassButton(
                onClick = onManage,
                modifier = Modifier.weight(1f),
                tint = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.picker_manage),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            GlassButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.picker_close),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionRow(option: ProviderOption, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = option.ready, onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (option.ready) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(
                    R.string.picker_subtitle,
                    option.providerLabel,
                    option.model
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (option.ready) {
                    authKindLabel(option.authKind)
                } else {
                    stringResource(R.string.picker_not_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (option.ready) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    AppleRedLight
                }
            )
        }
        if (option.isActive) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .glassSurface(
                        shape = CircleShape,
                        tint = AppleGreenLight,
                        elevation = 2.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.providers_active),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelList(option: ProviderOption, onSelectModel: (String) -> Unit) {
    if (option.models.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.picker_model_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        option.models.forEach { modelId ->
            val selected = modelId == option.model
            GlassButton(
                onClick = { onSelectModel(modelId) },
                modifier = Modifier.fillMaxWidth(),
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = modelId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.picker_model_selected),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private val MAX_PICKER_HEIGHT = 480.dp
