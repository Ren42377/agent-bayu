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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.domain.MessageSegment
import dev.agentbayu.app.ui.theme.AppleBlueDark
import dev.agentbayu.app.ui.theme.AppleBlueLight
import dev.agentbayu.app.ui.theme.AppleGreenDark
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.GlassBadgeShape
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.UserBubbleShape
import dev.agentbayu.app.ui.theme.glassSurface
import kotlinx.coroutines.delay

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onShowDetail: ((ChatMessage) -> Unit)? = null
) {
    val isDark = LocalDarkTheme.current
    if (message.author == MessageAuthor.USER) {
        UserMessage(message = message, modifier = modifier, isDark = isDark)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        message.displaySegments.forEach { segment ->
            when (segment) {
                is MessageSegment.Thinking -> ThinkingRow(segment = segment)

                is MessageSegment.Prose -> if (segment.text.isNotBlank()) {
                    MarkdownMessage(
                        content = segment.text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is MessageSegment.Tool -> ToolRow(segment = segment, isDark = isDark)
            }
        }
        if (message.detail != null && onShowDetail != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onShowDetail(message) },
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.route_show),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage, isDark: Boolean, modifier: Modifier = Modifier) {
    val userTint = if (isDark) AppleBlueDark else AppleBlueLight
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
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
    }
}

@Composable
private fun ThinkingRow(segment: MessageSegment.Thinking) {
    var expanded by remember { mutableStateOf(false) }
    var liveMillis by remember { mutableLongStateOf(0L) }
    val running = !segment.done

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val startedAt = System.nanoTime()
        while (true) {
            liveMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
            delay(TICK_MILLIS)
        }
    }

    val millis = if (running) liveMillis else segment.millis
    val seconds = (millis / MILLIS_PER_SECOND).toInt()
    val label = if (seconds <= 0) {
        stringResource(R.string.chat_thought)
    } else {
        stringResource(R.string.chat_thought_seconds, seconds)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(GlassBadgeShape)
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHEVRON_ALPHA),
                modifier = Modifier
                    .size(12.dp)
                    .rotate(if (expanded) 270f else 90f)
            )
        }
        if (expanded && segment.text.isNotBlank()) {
            val body = segment.text.trim()
            val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
            val rendered = remember(body, codeColor) {
                buildAnnotatedString { appendInlineMarkdown(body, codeColor) }
            }
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 8.dp)
            )
        }
    }
}

@Composable
private fun ToolRow(segment: MessageSegment.Tool, isDark: Boolean) {
    val mutating = segment.name !in READ_ONLY_TOOLS
    val argument = argumentOf(segment.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (segment.running) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = toolDisplayName(segment.name),
            style = MaterialTheme.typography.labelMedium,
            color = if (mutating) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
        if (argument.isNotEmpty()) {
            Text(
                text = argument,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (mutating && !segment.running) {
            Icon(
                painter = painterResource(if (segment.ok) R.drawable.ic_check else R.drawable.ic_close),
                contentDescription = stringResource(
                    if (segment.ok) R.string.tool_run_done else R.string.tool_run_failed
                ),
                tint = if (segment.ok) {
                    if (isDark) AppleGreenDark else AppleGreenLight
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private val READ_ONLY_TOOLS = setOf(
    "list_files",
    "read_file",
    "search_files",
    "view_image",
    "list_tasks",
    "web_search"
)

private val ARGUMENT_PATTERN = Regex("\"(path|from|title|query|time)\"\\s*:\\s*\"([^\"]*)\"")

private fun argumentOf(label: String): String {
    val value = ARGUMENT_PATTERN.find(label)?.groupValues?.get(2) ?: return ""
    return if (value.startsWith("/")) value.substringAfterLast('/') else value
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val TICK_MILLIS = 250L
private const val CHEVRON_ALPHA = 0.6f
