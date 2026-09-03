package dev.agentbayu.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassOverlay

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
    var hourText by remember(visible, initialHour) {
        mutableStateOf(twoDigits(initialHour))
    }
    var minuteText by remember(visible, initialMinute) {
        mutableStateOf(twoDigits(initialMinute))
    }
    GlassOverlay(visible = visible, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeField(
                    value = hourText,
                    onValueChange = { hourText = digits(it) }
                )
                Text(
                    text = TIME_SEPARATOR,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                TimeField(
                    value = minuteText,
                    onValueChange = { minuteText = digits(it) }
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
                        onSelect(
                            clamp(hourText, MAX_HOUR),
                            clamp(minuteText, MAX_MINUTE)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tasks_detail_save),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                text = stringResource(R.string.tasks_clear_value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onClear()
                    }
                    .padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TimeField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(88.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

private fun digits(value: String): String = value.filter { it.isDigit() }.take(TIME_DIGITS)

private fun clamp(value: String, max: Int): Int =
    (value.toIntOrNull() ?: 0).coerceIn(0, max)

private fun twoDigits(value: Int): String = value.toString().padStart(TIME_DIGITS, '0')

private const val TIME_DIGITS = 2
private const val MAX_HOUR = 23
private const val MAX_MINUTE = 59
private const val TIME_SEPARATOR = ":"
