package dev.agentbayu.app.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isResponding: Boolean,
    modifier: Modifier = Modifier,
    sessionKey: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onShowDetail: ((ChatMessage) -> Unit)? = null
) {
    key(sessionKey) {
        MessageListBody(
            messages = messages,
            isResponding = isResponding,
            modifier = modifier,
            contentPadding = contentPadding,
            onShowDetail = onShowDetail
        )
    }
}

@Composable
private fun MessageListBody(
    messages: List<ChatMessage>,
    isResponding: Boolean,
    modifier: Modifier,
    contentPadding: PaddingValues,
    onShowDetail: ((ChatMessage) -> Unit)?
) {
    val listState = rememberLazyListState()
    val visible = remember(messages) {
        messages.filterNot { message ->
            message.streaming && message.text.isEmpty() && message.segments.isEmpty()
        }
    }
    val showTyping = isResponding && visible.size < messages.size
    val itemCount = visible.size + if (showTyping) 1 else 0
    val scrolledCount = remember { mutableIntStateOf(0) }
    val follow = remember { mutableStateOf(true) }
    val followGuard = remember(follow) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) follow.value = false
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0f) follow.value = true
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        if (visible.lastOrNull()?.author == MessageAuthor.USER) follow.value = true
        if (scrolledCount.intValue == 0) {
            scrolledCount.intValue = itemCount
            listState.scrollToItem(itemCount - 1)
            return@LaunchedEffect
        }
        if (!follow.value || itemCount == scrolledCount.intValue) return@LaunchedEffect
        scrolledCount.intValue = itemCount
        withFrameNanos { }
        val overflow = listState.bottomOverflow()
        if (overflow > 0f && !listState.isScrollInProgress) {
            try {
                listState.animateScrollBy(overflow)
            } catch (cancellation: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    LaunchedEffect(isResponding) {
        if (!isResponding) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (!follow.value) continue
            if (listState.isScrollInProgress) continue
            val overflow = listState.bottomOverflow()
            if (overflow > 0f) {
                try {
                    listState.scrollBy(overflow)
                } catch (cancellation: CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.nestedScroll(followGuard),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = visible, key = { message -> message.id }) { message ->
            MessageBubble(
                message = message,
                modifier = Modifier.animateItem(fadeOutSpec = null, placementSpec = null),
                onShowDetail = onShowDetail
            )
        }
        if (showTyping) {
            item(key = TYPING_KEY) {
                TypingIndicator(
                    modifier = Modifier
                        .animateItem(fadeOutSpec = null, placementSpec = null)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

private fun LazyListState.bottomOverflow(): Float {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index < layout.totalItemsCount - 1) return 0f
    val contentEnd = layout.viewportEndOffset - layout.afterContentPadding
    return (last.offset + last.size - contentEnd).toFloat().coerceAtLeast(0f)
}

private const val TYPING_KEY = "typing"
