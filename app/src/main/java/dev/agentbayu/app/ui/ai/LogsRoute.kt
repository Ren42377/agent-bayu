package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiLogsRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.logs(context) }
    val entries by store.entries.collectAsState()
    val formatter = remember { SimpleDateFormat(TIME_PATTERN, Locale.getDefault()) }
    val clearedMessage = stringResource(R.string.logs_cleared)

    val rows = remember(entries, formatter) {
        entries.asReversed().map { entry ->
            LogRowState(
                id = entry.id,
                level = entry.level,
                time = formatter.format(Date(entry.atMillis)),
                source = entry.source,
                message = entry.message,
                detail = entry.detail
            )
        }
    }

    LogsScreen(
        rows = rows,
        onBack = onBack,
        onClear = {
            store.clear()
            onMessage(clearedMessage)
        },
        modifier = modifier
    )
}

private const val TIME_PATTERN = "HH:mm:ss"
