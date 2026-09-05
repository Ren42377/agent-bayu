package dev.agentbayu.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatSessionMeta
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.InteractiveHighlight
import dev.agentbayu.app.ui.components.inspectDragGestures
import dev.agentbayu.app.ui.theme.GlassOverlayDimDark
import dev.agentbayu.app.ui.theme.GlassOverlayDimLight
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.PanelStartShape
import dev.agentbayu.app.ui.theme.glassSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class HistoryDrawerState internal constructor(private val scope: CoroutineScope) {

    private val animation = Animatable(0f)

    internal var mounted by mutableStateOf(false)
        private set

    internal var isOpen by mutableStateOf(false)
        private set

    internal var panelWidth = 0f

    internal fun value(): Float = animation.value

    fun open() {
        mounted = true
        isOpen = true
        scope.launch { animation.animateTo(1f, PANEL_SPRING) }
    }

    fun close() {
        isOpen = false
        scope.launch {
            animation.animateTo(0f, PANEL_SPRING)
            mounted = false
        }
    }

    internal fun drag(delta: Float) {
        mounted = true
        val width = panelWidth
        if (width <= 0f) return
        val target = (animation.targetValue + delta / width).coerceIn(0f, 1f)
        scope.launch { animation.animateTo(target, DRAG_SPRING) }
    }

    internal fun settle() {
        if (animation.targetValue >= SETTLE_FRACTION) open() else close()
    }

    private companion object {
        val PANEL_SPRING = spring<Float>(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f
        )
        val DRAG_SPRING = spring<Float>(
            dampingRatio = 1f,
            stiffness = 1_400f,
            visibilityThreshold = 0.001f
        )
        const val SETTLE_FRACTION = 0.4f
    }
}

@Composable
fun rememberHistoryDrawerState(): HistoryDrawerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { HistoryDrawerState(scope) }
}

fun Modifier.historyDrawerEdge(state: HistoryDrawerState): Modifier = this.pointerInput(state) {
    val edgePx = EDGE_WIDTH.toPx()
    var armed = false
    inspectDragGestures(
        onDragStart = { down -> armed = down.position.x <= edgePx || state.isOpen },
        onDragEnd = { if (armed) state.settle() },
        onDragCancel = { if (armed) state.settle() }
    ) { _, dragAmount ->
        if (armed) state.drag(dragAmount.x)
    }
}

@Composable
fun HistoryDrawer(
    state: HistoryDrawerState,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.mounted) return
    val context = LocalContext.current
    val manager = remember(context) { AppGraph.sessions(context) }
    val sessions by manager.sessions.collectAsState()
    val activeId by manager.activeSessionId.collectAsState()
    val deletedMessage = stringResource(R.string.history_deleted)
    val darkTheme = LocalDarkTheme.current
    val scrim = if (darkTheme) GlassOverlayDimDark else GlassOverlayDimLight
    var deleteMode by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChatSessionMeta?>(null) }
    val systemInsets = WindowInsets.systemBars.asPaddingValues()

    BackHandler(enabled = state.isOpen, onBack = state::close)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelWidth = maxWidth * PANEL_FRACTION
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(color = scrim, alpha = state.value()) }
                .then(
                    if (state.isOpen) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { state.close() }
                        }
                    } else {
                        Modifier
                    }
                )
        )
        Column(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .onSizeChanged { size -> state.panelWidth = size.width.toFloat() }
                .graphicsLayer { translationX = -size.width * (1f - state.value()) }
                .drawWithContent { if (state.value() > 0f) drawContent() }
                .glassSurface(shape = PanelStartShape)
                .padding(top = systemInsets.calculateTopPadding())
        ) {
            HistoryDrawerContent(
                sessions = sessions,
                activeSessionId = activeId,
                deleteMode = deleteMode,
                onToggleDeleteMode = { deleteMode = !deleteMode },
                onOpen = { sessionId ->
                    manager.openSession(sessionId)
                    state.close()
                },
                onNew = {
                    manager.newSession()
                    state.close()
                },
                onDelete = { session -> pendingDelete = session }
            )
        }
    }

    val pending = pendingDelete
    GlassDialog(
        visible = pending != null,
        title = stringResource(R.string.history_delete),
        body = stringResource(R.string.history_delete_confirm_body),
        confirmLabel = stringResource(R.string.history_delete),
        onConfirm = {
            pendingDelete = null
            pending?.let { session ->
                manager.deleteSession(session.id)
                onMessage(deletedMessage)
            }
        },
        dismissLabel = stringResource(R.string.dialog_cancel),
        onDismiss = { pendingDelete = null }
    )
}

private const val PANEL_FRACTION = 0.84f
private val EDGE_WIDTH = 28.dp

@Composable
private fun ColumnScope.HistoryDrawerContent(
    sessions: List<ChatSessionMeta>,
    activeSessionId: String?,
    deleteMode: Boolean,
    onToggleDeleteMode: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (ChatSessionMeta) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        GlassIconButton(onClick = onToggleDeleteMode) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.history_delete_mode),
                tint = if (deleteMode) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp)
            )
        }
        sessions.forEach { session ->
            SessionRow(
                session = session,
                isActive = session.id == activeSessionId,
                showDelete = deleteMode,
                onOpen = { onOpen(session.id) },
                onDelete = { onDelete(session) }
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = 12.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
            )
    ) {
        GlassButton(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = stringResource(R.string.history_new),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSessionMeta,
    isActive: Boolean,
    showDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(animationScope = scope, claimDrag = false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassTileShape)
            .clickable(interactionSource = null, indication = null, onClick = onOpen)
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
            .padding(start = 10.dp, end = if (showDelete) 2.dp else 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chat),
            contentDescription = null,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = session.title.ifBlank { stringResource(R.string.history_untitled) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showDelete) {
            GlassIconButton(onClick = onDelete, size = 32.dp) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.history_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
