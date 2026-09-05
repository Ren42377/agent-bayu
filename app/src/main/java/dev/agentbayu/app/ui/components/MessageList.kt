package dev.agentbayu.app.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.domain.ChatMessage

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isResponding: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowDetail: ((ChatMessage) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val visible = remember(messages) {
        messages.filterNot { message -> message.streaming && message.text.isEmpty() }
    }
    val showTyping = isResponding && visible.size < messages.size
    val itemCount = visible.size + if (showTyping) 1 else 0
    val scrolledCount = remember { mutableIntStateOf(0) }

    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        if (scrolledCount.intValue == 0) {
            scrolledCount.intValue = itemCount
            listState.scrollToItem(itemCount - 1)
            return@LaunchedEffect
        }
        if (!listState.isNearBottom()) return@LaunchedEffect
        if (itemCount != scrolledCount.intValue) {
            scrolledCount.intValue = itemCount
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    LaunchedEffect(isResponding) {
        if (!isResponding) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (listState.isScrollInProgress) continue
            if (!listState.isNearBottom()) continue
            val overflow = listState.bottomOverflow()
            if (overflow > 0f) {
                listState.scrollBy(overflow)
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = visible, key = { message -> message.id }) { message ->
            MessageBubble(
                message = message,
                modifier = Modifier.animateItem(placementSpec = null),
                onShowDetail = onShowDetail
            )
        }
        if (showTyping) {
            item(key = TYPING_KEY) {
                TypingIndicator(
                    modifier = Modifier
                        .animateItem(placementSpec = null)
                        .padding(start = 8.dp, top = 2.dp)
                )
            }
        }
    }
}

private fun LazyListState.isNearBottom(): Boolean {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return true
    if (last.index < layout.totalItemsCount - 1) return false
    val contentEnd = layout.viewportEndOffset - layout.afterContentPadding
    return last.offset + last.size - contentEnd <= BOTTOM_TOLERANCE_PIXELS
}

private fun LazyListState.bottomOverflow(): Float {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index < layout.totalItemsCount - 1) return 0f
    val contentEnd = layout.viewportEndOffset - layout.afterContentPadding
    return (last.offset + last.size - contentEnd).toFloat().coerceAtLeast(0f)
}

private const val TYPING_KEY = "typing"
private const val BOTTOM_TOLERANCE_PIXELS = 24
