package dev.agentbayu.app.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.ui.components.GlassBottomTab
import dev.agentbayu.app.ui.components.GlassBottomTabs
import dev.agentbayu.app.ui.components.GlassTabsProgress

@Composable
fun AgentBayuBottomBar(
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    progress: GlassTabsProgress,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    val haptics = LocalHapticFeedback.current
    val destinations = AgentBayuDestination.entries

    val select = { index: Int ->
        if (index != selectedIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(index)
        }
    }

    GlassBottomTabs(
        selectedTabIndex = selectedIndex,
        onTabSelected = { index -> select(index) },
        tabsCount = destinations.size,
        progress = progress,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        destinations.forEachIndexed { index, destination ->
            GlassBottomTab(onClick = { select(index) }) {
                Icon(
                    painter = painterResource(destination.iconRes),
                    contentDescription = stringResource(destination.labelRes),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(destination.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
