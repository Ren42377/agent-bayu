package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
internal fun CodeCard(
    label: String,
    icon: Int?,
    code: String,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
                size = 28.dp
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.code_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        body()
    }
}

internal data class CodeFence(val language: String?, val body: String)

internal fun fenceOf(source: String): CodeFence {
    val lines = source.trim().lines()
    if (lines.isEmpty()) return CodeFence(null, source)
    val opening = lines.first().trim()
    if (!opening.startsWith("```") && !opening.startsWith("~~~")) {
        return CodeFence(null, source.trimEnd())
    }
    val language = opening.drop(3).trim().takeIf { it.isNotEmpty() }
    val closing = lines.lastOrNull()?.trim().orEmpty()
    val end = if (lines.size > 1 && (closing == "```" || closing == "~~~")) {
        lines.lastIndex
    } else {
        lines.size
    }
    return CodeFence(language, lines.subList(1, end).joinToString("\n"))
}

internal fun isMarkdownLanguage(language: String?): Boolean {
    val name = language?.lowercase() ?: return false
    return name == "md" || name == "markdown"
}

internal fun markdownTitleOf(body: String): String? = body.lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("#") }
    ?.trimStart('#')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
