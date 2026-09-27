package dev.agentbayu.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
fun StatusCard(
    title: String,
    body: String,
    done: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val activeColor = AppleGreenLight
    val badgeTint by animateColorAsState(
        targetValue = if (done) activeColor else Color.Transparent,
        label = "statusBadgeTint"
    )
    val statusColor by animateColorAsState(
        targetValue = if (done) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "statusLabelColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .glassSurface(shape = CircleShape, tint = badgeTint, elevation = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = done,
                    transitionSpec = {
                        (fadeIn(AgentBayuMotion.quickFade) + scaleIn(initialScale = STATUS_ICON_ENTER_SCALE)) togetherWith
                            (fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = STATUS_ICON_ENTER_SCALE))
                    },
                    label = "statusIcon"
                ) { ready ->
                    Icon(
                        painter = painterResource(
                            if (ready) R.drawable.ic_check else R.drawable.ic_pending
                        ),
                        contentDescription = stringResource(
                            if (ready) R.string.cd_status_ready else R.string.cd_status_pending
                        ),
                        tint = if (ready) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (done) R.string.status_ready else R.string.status_pending
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hint != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassButton(
                        onClick = onAction,
                        tint = MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            if (checked != null && onCheckedChange != null) {
                Spacer(modifier = Modifier.width(8.dp))
                GlassToggle(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }
}

private const val STATUS_ICON_ENTER_SCALE = 0.85f
