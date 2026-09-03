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
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.ui.theme.AppleBlueDark
import dev.agentbayu.app.ui.theme.AppleBlueLight
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
