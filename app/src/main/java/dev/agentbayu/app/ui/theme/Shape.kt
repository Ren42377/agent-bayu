package dev.agentbayu.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal val AgentBayuShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val CapsuleShape = RoundedCornerShape(percent = 50)
val GlassCardShape = RoundedCornerShape(22.dp)
val GlassTileShape = RoundedCornerShape(16.dp)
val GlassBadgeShape = RoundedCornerShape(10.dp)

val PanelShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 6.dp,
    bottomStart = 20.dp
)

val AgentBubbleShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 6.dp
)
