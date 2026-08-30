package dev.agentbayu.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassBadge
import dev.agentbayu.app.ui.components.GlassCard
import dev.agentbayu.app.ui.theme.AppleBlueLight
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleIndigoLight
import dev.agentbayu.app.ui.theme.ApplePurpleLight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.liquidGlass

@Composable
fun SettingsScreen(
    versionName: String,
    useScreenContext: Boolean,
    onScreenContextChange: (Boolean) -> Unit,
    onClearConversation: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenUsage: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        SectionGroup(title = stringResource(R.string.settings_ai)) {
            NavigationSettingRow(
                icon = painterResource(R.drawable.ic_spark),
                iconColor = AppleBlueLight,
                title = stringResource(R.string.settings_providers_title),
                subtitle = stringResource(R.string.settings_providers_body),
                onClick = onOpenProviders
            )
            SettingDivider()
            NavigationSettingRow(
                icon = painterResource(R.drawable.ic_check),
                iconColor = AppleGreenLight,
                title = stringResource(R.string.settings_usage_title),
                subtitle = stringResource(R.string.settings_usage_body),
                onClick = onOpenUsage
            )
        }

        SectionGroup(title = stringResource(R.string.settings_privacy)) {
            ToggleSettingRow(
                icon = painterResource(R.drawable.ic_settings),
                iconColor = AppleIndigoLight,
                title = stringResource(R.string.setup_context_title),
                subtitle = stringResource(R.string.setup_context_body),
                checked = useScreenContext,
                onCheckedChange = onScreenContextChange
            )
        }

        SectionGroup(title = stringResource(R.string.settings_conversation)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassBadge(
                    icon = painterResource(R.drawable.ic_delete),
                    containerColor = AppleRedLight
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_clear_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_clear_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable(onClick = onClearConversation)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        SectionGroup(title = stringResource(R.string.settings_about)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SectionGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = GlassCardShape)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun NavigationSettingRow(
    icon: Painter,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassBadge(icon = icon, containerColor = iconColor)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ToggleSettingRow(
    icon: Painter,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassBadge(icon = icon, containerColor = iconColor)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}
