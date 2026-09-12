package dev.agentbayu.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.CapsuleShape

data class ChatSuggestion(val label: String, @DrawableRes val icon: Int)

@Composable
fun defaultSuggestions(): List<ChatSuggestion> = listOf(
    ChatSuggestion(stringResource(R.string.suggestion_summarize), R.drawable.ic_note),
    ChatSuggestion(stringResource(R.string.suggestion_write), R.drawable.ic_edit),
    ChatSuggestion(stringResource(R.string.suggestion_search), R.drawable.ic_globe),
    ChatSuggestion(stringResource(R.string.suggestion_tasks), R.drawable.ic_task)
)

@Composable
fun SuggestionRows(
    suggestions: List<ChatSuggestion>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CapsuleShape)
                    .clickable { onSelect(suggestion.label) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    painter = painterResource(suggestion.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = suggestion.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
