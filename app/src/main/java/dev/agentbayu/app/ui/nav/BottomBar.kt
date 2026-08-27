package dev.agentbayu.app.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

@Composable
fun AgentBayuBottomBar(
    currentRoute: String,
    onSelect: (AgentBayuDestination) -> Unit,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    NavigationBar(windowInsets = windowInsets) {
        AgentBayuDestination.entries.forEach { destination ->
            val selected = destination.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) }
            )
        }
    }
}
