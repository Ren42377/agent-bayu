package dev.agentbayu.app.ui.settings

import dev.agentbayu.app.ui.history.HistoryDrawerButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tools.ToolApprovalMode
import dev.agentbayu.app.platform.ThemeMode
import dev.agentbayu.app.ui.components.GlassSegmentedSelector
import dev.agentbayu.app.ui.components.GlassToggle
import dev.agentbayu.app.ui.components.pressScaleFeedback
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.LocalThemeScrub
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun SettingsScreen(
    versionName: String,
    useScreenContext: Boolean,
    themeMode: ThemeMode,
    toolApprovalMode: ToolApprovalMode,
    storageGranted: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToolApprovalModeChange: (ToolApprovalMode) -> Unit,
    onScreenContextChange: (Boolean) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenCustomPrompt: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + insets.calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryDrawerButton()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        SectionGroup(title = stringResource(R.string.settings_appearance)) {
            ThemeSettingRow(mode = themeMode, onModeChange = onThemeModeChange)
        }

        SectionGroup(title = stringResource(R.string.settings_ai)) {
            NavigationSettingRow(
                icon = painterResource(R.drawable.ic_package),
                title = stringResource(R.string.settings_providers_title),
                shape = groupShape(index = 0, count = 3),
                onClick = onOpenProviders
            )
            NavigationSettingRow(
                icon = painterResource(R.drawable.ic_edit),
                title = stringResource(R.string.settings_custom_prompt_title),
                shape = groupShape(index = 1, count = 3),
                onClick = onOpenCustomPrompt
            )
            NavigationSettingRow(
                icon = painterResource(R.drawable.ic_pending),
                title = stringResource(R.string.settings_logs_title),
                shape = groupShape(index = 2, count = 3),
                onClick = onOpenLogs
            )
        }

        SectionGroup(title = stringResource(R.string.settings_tools)) {
            ToggleSettingRow(
                icon = painterResource(R.drawable.ic_check),
                title = stringResource(R.string.settings_tool_approval_title),
                shape = groupShape(index = 0, count = 2),
                checked = toolApprovalMode == ToolApprovalMode.BYPASS,
                onCheckedChange = { enabled ->
                    onToolApprovalModeChange(
                        if (enabled) ToolApprovalMode.BYPASS else ToolApprovalMode.ASK
                    )
                }
            )
            NavigationSettingRow(
                icon = painterResource(R.drawable.ic_open_in_app),
                title = stringResource(R.string.settings_storage_title),
                value = if (storageGranted) stringResource(R.string.status_ready) else null,
                shape = groupShape(index = 1, count = 2),
                onClick = onOpenStorageSettings
            )
        }

        SectionGroup(title = stringResource(R.string.settings_privacy)) {
            ToggleSettingRow(
                icon = painterResource(R.drawable.ic_settings),
                title = stringResource(R.string.setup_context_title),
                shape = groupShape(index = 0, count = 1),
                checked = useScreenContext,
                onCheckedChange = onScreenContextChange
            )
        }

        SectionGroup(title = stringResource(R.string.settings_about)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(shape = groupShape(index = 0, count = 1), bordered = false)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private val GroupOuterRadius = 28.dp
private val GroupInnerRadius = 6.dp
private val GroupRowGap = 3.dp

private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) GroupOuterRadius else GroupInnerRadius
    val bottom = if (index == count - 1) GroupOuterRadius else GroupInnerRadius
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom
    )
}

@Composable
private fun SectionGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(GroupRowGap)) {
            content()
        }
    }
}

@Composable
private fun ThemeSettingRow(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    val options = ThemeMode.entries
    val labels = options.map { option ->
        when (option) {
            ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
            ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
            ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
        }
    }
    val icons = options.map { option ->
        when (option) {
            ThemeMode.SYSTEM -> R.drawable.ic_theme_system
            ThemeMode.LIGHT -> R.drawable.ic_theme_light
            ThemeMode.DARK -> R.drawable.ic_theme_dark
        }
    }
    val scrub = LocalThemeScrub.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = groupShape(index = 0, count = 1), bordered = false)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_theme),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SettingIconSize)
            )
            Spacer(modifier = Modifier.width(SettingIconGap))
            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        GlassSegmentedSelector(
            labels = labels,
            icons = icons.map { painterResource(it) },
            selectedIndex = options.indexOf(mode).coerceAtLeast(0),
            onSelect = { index -> onModeChange(options[index]) },
            onScrub = { reader ->
                if (reader != null) {
                    scrub.bind(reader)
                } else {
                    scrub.unbind()
                }
            }
        )
    }
}

@Composable
private fun NavigationSettingRow(
    icon: Painter,
    title: String,
    shape: Shape,
    onClick: () -> Unit,
    value: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = shape, bordered = false)
            .clickable(onClick = onClick)
            .pressScaleFeedback()
            .heightIn(min = SettingRowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SettingIconSize)
        )
        Spacer(modifier = Modifier.width(SettingIconGap))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ToggleSettingRow(
    icon: Painter,
    title: String,
    shape: Shape,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = shape, bordered = false)
            .heightIn(min = SettingRowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SettingIconSize)
        )
        Spacer(modifier = Modifier.width(SettingIconGap))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        GlassToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private val SettingIconSize = 22.dp
private val SettingIconGap = 16.dp
private val SettingRowMinHeight = 56.dp
