package dev.agentbayu.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.ui.theme.AgentBubbleShape
import dev.agentbayu.app.ui.theme.AppleBlueDark
import dev.agentbayu.app.ui.theme.AppleBlueLight
import dev.agentbayu.app.ui.theme.UserBubbleShape
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onShowDetail: ((ChatMessage) -> Unit)? = null
) {
    val fromUser = message.author == MessageAuthor.USER
    val isDark = isSystemInDarkTheme()

    val userTint = if (isDark) AppleBlueDark else AppleBlueLight

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start
    ) {
        if (fromUser) {
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
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .glassSurface(shape = AgentBubbleShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (message.detail != null && onShowDetail != null) {
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { onShowDetail(message) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.route_show),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_chevron),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
