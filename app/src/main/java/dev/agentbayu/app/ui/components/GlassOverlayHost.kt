package dev.agentbayu.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.GlassOverlayDimDark
import dev.agentbayu.app.ui.theme.GlassOverlayDimLight
import dev.agentbayu.app.ui.theme.GlassOverlayFillDark
import dev.agentbayu.app.ui.theme.GlassOverlayFillLight
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.ScrimBlack

@Stable
class GlassOverlayController {

    internal val entries = mutableStateListOf<GlassOverlayEntry>()

    val isVisible: Boolean
        get() = entries.isNotEmpty()
}

@Stable
internal class GlassOverlayEntry {

    var onDismiss: () -> Unit by mutableStateOf({})

    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

val LocalGlassOverlay = staticCompositionLocalOf { GlassOverlayController() }

@Composable
fun GlassOverlay(
    visible: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val controller = LocalGlassOverlay.current
    val entry = remember { GlassOverlayEntry() }
    SideEffect {
        if (visible) {
            entry.onDismiss = onDismiss
            entry.content = content
        }
    }
    DisposableEffect(controller, entry, visible) {
        if (visible) {
            controller.entries.add(entry)
        }
        onDispose { controller.entries.remove(entry) }
    }
}

@Composable
fun GlassOverlayHost(
    controller: GlassOverlayController,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    val current = controller.entries.lastOrNull()
    val rendered = remember { mutableStateOf<GlassOverlayEntry?>(null) }
    if (current != null && rendered.value !== current) {
        rendered.value = current
    }
    val progress by animateFloatAsState(
        targetValue = if (current != null) 1f else 0f,
        animationSpec = AgentBayuMotion.panelSpring,
        label = "glassOverlay",
        finishedListener = { value ->
            if (value < VISIBILITY_THRESHOLD) {
                rendered.value = null
            }
        }
    )
    val entry = rendered.value
    if (entry == null || (current == null && progress < VISIBILITY_THRESHOLD)) {
        return
    }
    val body = entry.content ?: return
    val dimColor = if (darkTheme) GlassOverlayDimDark else GlassOverlayDimLight
    val fillColor = if (darkTheme) GlassOverlayFillDark else GlassOverlayFillLight
    val panelBrightness = if (darkTheme) 0f else 0.2f
    val dimmedBackdrop = rememberBackdrop(backdrop) { drawBackdrop ->
        drawBackdrop()
        drawRect(color = dimColor)
    }
    val panelBackdrop = rememberLayerBackdrop()

    BackHandler(enabled = current != null) { entry.onDismiss() }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(dimColor)
                .pointerInput(entry) { detectTapGestures { entry.onDismiss() } }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = MAX_OVERLAY_WIDTH)
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = dimmedBackdrop,
                        shape = { OVERLAY_SHAPE },
                        effects = {
                            colorControls(brightness = panelBrightness, saturation = 1.5f)
                            vibrancy()
                            if (size.isSpecified) {
                                lens(
                                    OVERLAY_REFRACTION_HEIGHT.toPx(),
                                    OVERLAY_REFRACTION_AMOUNT.toPx(),
                                    depthEffect = true
                                )
                            }
                        },
                        highlight = { Highlight.Plain },
                        shadow = {
                            Shadow(radius = 28.dp, color = ScrimBlack.copy(alpha = 0.3f))
                        },
                        layerBlock = {
                            alpha = progress
                            val scale = OVERLAY_MIN_SCALE +
                                (1f - OVERLAY_MIN_SCALE) * progress
                            scaleX = scale
                            scaleY = scale
                        },
                        exportedBackdrop = panelBackdrop,
                        onDrawSurface = { drawRect(color = fillColor) }
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp)
            ) {
                CompositionLocalProvider(LocalGlassBackdrop provides panelBackdrop) {
                    body()
                }
            }
        }
    }
}

private val OVERLAY_SHAPE = RoundedCornerShape(36.dp)
private val MAX_OVERLAY_WIDTH = 480.dp
private val OVERLAY_REFRACTION_HEIGHT = 18.dp
private val OVERLAY_REFRACTION_AMOUNT = 36.dp
private const val OVERLAY_MIN_SCALE = 0.9f
private const val VISIBILITY_THRESHOLD = 0.01f
