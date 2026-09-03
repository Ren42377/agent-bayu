package dev.agentbayu.app.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember(context) { AppGraph.sessions(context) }
    val sessions by manager.sessions.collectAsState()
    val activeId by manager.activeSessionId.collectAsState()
    val deletedMessage = stringResource(R.string.history_deleted)

    HistoryScreen(
        sessions = sessions,
        activeSessionId = activeId,
        onBack = onBack,
        onOpen = { sessionId ->
            manager.openSession(sessionId)
            onBack()
        },
        onNew = {
            manager.newSession()
            onBack()
        },
        onDelete = { sessionId ->
            manager.deleteSession(sessionId)
            onMessage(deletedMessage)
        },
        modifier = modifier
    )
}
