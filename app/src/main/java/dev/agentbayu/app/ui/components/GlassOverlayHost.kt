package dev.agentbayu.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.PanelShape
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

    var presentation: GlassOverlayPresentation by mutableStateOf(GlassOverlayPresentation.DIALOG)

    var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

enum class GlassOverlayPresentation { DIALOG, SHEET }

val LocalGlassOverlay = staticCompositionLocalOf { GlassOverlayController() }

@Composable
fun GlassOverlay(
    visible: Boolean = true,
    presentation: GlassOverlayPresentation = GlassOverlayPresentation.DIALOG,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val controller = LocalGlassOverlay.current
    val entry = remember { GlassOverlayEntry() }
    SideEffect {
        if (visible) {
            entry.onDismiss = onDismiss
            entry.presentation = presentation
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
    darkTheme: Boolean = LocalDarkTheme.current
) {
    val rendered = remember { mutableStateListOf<GlassOverlayEntry>() }
    controller.entries.forEach { entry ->
        if (!rendered.contains(entry)) {
            rendered.add(entry)
        }
    }
    if (rendered.isEmpty()) {
        return
    }
    val focused = controller.entries.lastOrNull()
    Box(modifier = modifier.fillMaxSize()) {
        rendered.toList().forEach { entry ->
            key(entry) {
                GlassOverlayPanel(
                    entry = entry,
                    visible = controller.entries.contains(entry),
                    focused = entry === focused,
                    backdrop = backdrop,
                    darkTheme = darkTheme,
                    onExit = { rendered.remove(entry) }
                )
            }
        }
    }
}

@Composable
private fun GlassOverlayPanel(
    entry: GlassOverlayEntry,
    visible: Boolean,
    focused: Boolean,
    backdrop: Backdrop,
    darkTheme: Boolean,
    onExit: () -> Unit
) {
    val body = entry.content ?: return
    val animation = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        animation.animateTo(if (visible) 1f else 0f, AgentBayuMotion.panelSpring)
        if (!visible) {
            onExit()
        }
    }
    val isSheet = entry.presentation == GlassOverlayPresentation.SHEET
    val panelShape = if (isSheet) PanelShape else OVERLAY_SHAPE
    val dimColor = if (darkTheme) GlassOverlayDimDark else GlassOverlayDimLight
    val fillColor = if (darkTheme) GlassOverlayFillDark else GlassOverlayFillLight
    val panelBrightness = if (darkTheme) 0f else 0.2f
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val panelInsets = if (isSheet) {
        PaddingValues(bottom = navigationBottom)
    } else {
        LocalScreenInsets.current
    }
    val dimmedBackdrop = rememberBackdrop(backdrop) { drawBackdrop ->
        drawBackdrop()
        drawRect(color = dimColor)
    }
    val panelBackdrop = rememberLayerBackdrop()

    BackHandler(enabled = visible && focused) { entry.onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animation.value }
                .background(dimColor)
                .pointerInput(entry) { detectTapGestures { entry.onDismiss() } }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = if (isSheet) Alignment.BottomCenter else Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (isSheet) {
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(SHEET_HEIGHT_RATIO)
                        } else {
                            Modifier
                                .padding(horizontal = 24.dp)
                                .widthIn(max = MAX_OVERLAY_WIDTH)
                                .fillMaxWidth()
                        }
                    )
                    .drawBackdrop(
                        backdrop = dimmedBackdrop,
                        shape = { panelShape },
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
                            val progress = animation.value
                            alpha = progress
                            if (isSheet) {
                                translationY = size.height * (1f - progress)
                            } else {
                                val scale = OVERLAY_MIN_SCALE +
                                    (1f - OVERLAY_MIN_SCALE) * progress
                                scaleX = scale
                                scaleY = scale
                            }
                        },
                        exportedBackdrop = panelBackdrop,
                        onDrawSurface = { drawRect(color = fillColor) }
                    )
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(if (isSheet) Modifier else Modifier.padding(20.dp))
            ) {
                CompositionLocalProvider(
                    LocalGlassBackdrop provides panelBackdrop,
                    LocalScreenInsets provides panelInsets
                ) {
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
private const val SHEET_HEIGHT_RATIO = 0.94f
