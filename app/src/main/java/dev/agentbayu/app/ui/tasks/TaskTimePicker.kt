package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.agentbayu.app.ui.theme.GlassTileShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.GlassOverlay
import dev.agentbayu.app.ui.components.GlassOverlayPresentation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskTimePickerDialog(
    visible: Boolean,
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onSelect: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = key(visible, initialHour, initialMinute) {
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
    }
    var keyboardMode by remember(visible) { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val colors = TimePickerDefaults.colors(
        containerColor = Color.Transparent,
        clockDialColor = scheme.onSurface.copy(alpha = DIAL_ALPHA),
        clockDialSelectedContentColor = scheme.onPrimary,
        clockDialUnselectedContentColor = scheme.onSurface,
        selectorColor = scheme.primary,
        timeSelectorSelectedContainerColor = scheme.primary,
        timeSelectorSelectedContentColor = scheme.onPrimary,
        timeSelectorUnselectedContainerColor = scheme.onSurface.copy(alpha = DIAL_ALPHA),
        timeSelectorUnselectedContentColor = scheme.onSurface
    )
    GlassOverlay(
        visible = visible,
        presentation = GlassOverlayPresentation.WIDE_DIALOG,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            if (keyboardMode) {
                TimeInput(state = state, colors = colors)
            } else {
                TimePicker(
                    state = state,
                    colors = colors,
                    layoutType = TimePickerLayoutType.Vertical
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassIconButton(onClick = { keyboardMode = !keyboardMode }, size = 40.dp) {
                    Icon(
                        painter = painterResource(
                            if (keyboardMode) R.drawable.ic_clock else R.drawable.ic_keyboard
                        ),
                        contentDescription = stringResource(R.string.tasks_time_input_toggle),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                GlassButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                GlassButton(
                    onClick = {
                        onDismiss()
                        onSelect(state.hour, state.minute)
                    },
                    tint = scheme.primary,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_save),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimary
                    )
                }
            }
            Text(
                text = stringResource(R.string.tasks_clear_value),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassTileShape)
                    .clickable {
                        onDismiss()
                        onClear()
                    }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

private const val DIAL_ALPHA = 0.06f
