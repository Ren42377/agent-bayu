package dev.agentbayu.app.ui.history

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassIconButton

val LocalHistoryDrawer = staticCompositionLocalOf<HistoryDrawerState?> { null }

@Composable
fun HistoryDrawerButton(modifier: Modifier = Modifier) {
    val drawer = LocalHistoryDrawer.current ?: return
    GlassIconButton(
        onClick = drawer::open,
        modifier = modifier,
        size = 42.dp
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = stringResource(R.string.history_open),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
