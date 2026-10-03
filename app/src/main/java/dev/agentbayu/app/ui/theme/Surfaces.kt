package dev.agentbayu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

@Immutable
data class AppSurfaces(
    val container: Color,
    val control: Color,
    val composer: Color,
    val userBubble: Color,
    val drawer: Color,
    val lift: Dp
)

val lightSurfaces = AppSurfaces(
    container = GlassSurfaceLight,
    control = ControlLight,
    composer = ComposerLight,
    userBubble = UserBubbleLight,
    drawer = DrawerLight,
    lift = 5.dp
)

val darkSurfaces = AppSurfaces(
    container = GlassSurfaceDark,
    control = ControlDark,
    composer = ComposerDark,
    userBubble = UserBubbleDark,
    drawer = DrawerDark,
    lift = 0.dp
)

val LocalAppSurfaces = compositionLocalOf { lightSurfaces }

internal fun lerpSurfaces(start: AppSurfaces, stop: AppSurfaces, fraction: Float): AppSurfaces {
    return AppSurfaces(
        container = lerp(start.container, stop.container, fraction),
        control = lerp(start.control, stop.control, fraction),
        composer = lerp(start.composer, stop.composer, fraction),
        userBubble = lerp(start.userBubble, stop.userBubble, fraction),
        drawer = lerp(start.drawer, stop.drawer, fraction),
        lift = lerp(start.lift, stop.lift, fraction)
    )
}

@Composable
fun Modifier.liftShadow(shape: Shape): Modifier {
    val lift = LocalAppSurfaces.current.lift
    return if (lift.value <= 0.05f) {
        this
    } else {
        this.shadow(
            elevation = lift,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.16f),
            spotColor = Color.Black.copy(alpha = 0.2f)
        )
    }
}
