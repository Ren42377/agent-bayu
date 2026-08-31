package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.LogLevel
import dev.agentbayu.app.ai.LogStore
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleOrangeLight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.GlassBadgeShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface

data class LogRowState(
    val id: Long,
    val level: LogLevel,
    val time: String,
    val source: String,
    val message: String,
    val detail: String?
)

@Composable
fun LogsScreen(
    rows: List<LogRowState>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(title = stringResource(R.string.logs_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = NOTE_KEY) {
                Text(
                    text = stringResource(R.string.logs_note, LogStore.MAX_ENTRIES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (rows.isEmpty()) {
                item(key = EMPTY_KEY) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(shape = GlassCardShape)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.logs_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(items = rows, key = { row -> row.id }) { row -> LogCard(row = row) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp + insets.calculateBottomPadding()
                )
        ) {
            GlassButton(
                onClick = onClear,
                enabled = rows.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.logs_clear),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogCard(row: LogRowState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassTileShape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val accent = levelColor(row.level)
                Box(
                    modifier = Modifier
                        .background(
                            color = accent.copy(alpha = BADGE_FILL_ALPHA),
                            shape = GlassBadgeShape
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(levelLabel(row.level)),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Text(
                    text = stringResource(R.string.logs_meta, row.time, row.source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = row.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.level == LogLevel.ERROR) {
                    AppleRedLight
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            row.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> AppleGreenLight
    LogLevel.WARNING -> AppleOrangeLight
    LogLevel.ERROR -> AppleRedLight
}

private fun levelLabel(level: LogLevel): Int = when (level) {
    LogLevel.INFO -> R.string.logs_level_info
    LogLevel.WARNING -> R.string.logs_level_warning
    LogLevel.ERROR -> R.string.logs_level_error
}

private const val BADGE_FILL_ALPHA = 0.16f
private const val NOTE_KEY = "logs-note"
private const val EMPTY_KEY = "logs-empty"
