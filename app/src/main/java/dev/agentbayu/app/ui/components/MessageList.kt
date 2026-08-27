package dev.agentbayu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.domain.ChatMessage

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isResponding: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowRoute: ((ChatMessage) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val visible = messages.filterNot { message -> message.streaming && message.text.isEmpty() }
    val showTyping = isResponding && visible.size < messages.size
    val itemCount = visible.size + if (showTyping) 1 else 0
    val lastLength = visible.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(itemCount, lastLength) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = visible, key = { message -> message.id }) { message ->
            MessageBubble(message = message, onShowRoute = onShowRoute)
        }
        if (showTyping) {
            item(key = TYPING_KEY) {
                TypingIndicator(modifier = Modifier.padding(start = 8.dp, top = 2.dp))
            }
        }
    }
}

private const val TYPING_KEY = "typing"
