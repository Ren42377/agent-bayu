package dev.agentbayu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.domain.ToolRun
import dev.agentbayu.app.ui.theme.AppleBlueDark
import dev.agentbayu.app.ui.theme.AppleBlueLight
import dev.agentbayu.app.ui.theme.AppleGreenDark
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.GlassBadgeShape
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.UserBubbleShape
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onShowDetail: ((ChatMessage) -> Unit)? = null
) {
    val fromUser = message.author == MessageAuthor.USER
    val isDark = LocalDarkTheme.current

    val userTint = if (isDark) AppleBlueDark else AppleBlueLight

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start
    ) {
        if (fromUser) {
            if (message.attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.attachments.forEach { attachment ->
                        AttachmentThumbnail(
                            attachment = attachment,
                            size = 96.dp,
                            shape = UserBubbleShape
                        )
                    }
                }
            }
            if (message.text.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .glassSurface(shape = UserBubbleShape, tint = userTint)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            if (message.toolRuns.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.toolRuns.forEach { run -> ToolRunRow(run = run, isDark = isDark) }
                }
            }
            MarkdownMessage(
                content = message.text,
                modifier = Modifier.fillMaxWidth()
            )
            if (message.detail != null && onShowDetail != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onShowDetail(message) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_app),
                        contentDescription = stringResource(R.string.route_show),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRunRow(run: ToolRun, isDark: Boolean) {
    val icon = when {
        run.running -> R.drawable.ic_pending
        run.ok -> R.drawable.ic_check
        else -> R.drawable.ic_close
    }
    val status = when {
        run.running -> R.string.tool_run_pending
        run.ok -> R.string.tool_run_done
        else -> R.string.tool_run_failed
    }
    val tint = when {
        run.running -> MaterialTheme.colorScheme.onSurfaceVariant
        run.ok -> if (isDark) AppleGreenDark else AppleGreenLight
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .glassSurface(shape = GlassBadgeShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(status),
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = toolDisplayName(run.name),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        val argument = argumentOf(run.label)
        if (argument.isNotEmpty()) {
            Text(
                text = argument,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val ARGUMENT_PATTERN = Regex("\"(path|from|title|query)\"\\s*:\\s*\"([^\"]*)\"")

private fun argumentOf(label: String): String {
    val value = ARGUMENT_PATTERN.find(label)?.groupValues?.get(2) ?: return ""
    return if (value.startsWith("/")) value.substringAfterLast('/') else value
}
