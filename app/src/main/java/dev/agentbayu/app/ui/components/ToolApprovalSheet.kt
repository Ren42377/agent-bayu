package dev.agentbayu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tools.DiffKind
import dev.agentbayu.app.domain.tools.DiffLine
import dev.agentbayu.app.domain.tools.ToolApprovalDecision
import dev.agentbayu.app.domain.tools.ToolApprovalKind
import dev.agentbayu.app.domain.tools.ToolApprovalRequest
import dev.agentbayu.app.ui.theme.AppleGreenDark
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleRedDark
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.glassSurface

@Composable
internal fun ToolApprovalSheet(
    request: ToolApprovalRequest,
    onDecision: (ToolApprovalDecision) -> Unit
) {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val maxSheetHeight = with(LocalDensity.current) {
        (containerHeight * MAX_SHEET_HEIGHT_RATIO).toDp()
    }
    GlassOverlay(onDismiss = { onDecision(ToolApprovalDecision.DENY) }) {
        Column(
            modifier = Modifier.heightIn(max = maxSheetHeight),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = titleFor(request.kind),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.tool_approval_body,
                        toolDisplayName(request.toolName)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PathLine(request.path)
                request.destination?.let { destination ->
                    PathLine(stringResource(R.string.tool_approval_destination, destination))
                }
                if (request.preview.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tool_approval_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.tool_approval_counts,
                            request.added,
                            request.removed
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DiffPreview(request.preview)
                }
            }
            DecisionButtons(onDecision)
        }
    }
}

@Composable
private fun DecisionButtons(onDecision: (ToolApprovalDecision) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassButton(
            onClick = { onDecision(ToolApprovalDecision.DENY) },
            modifier = Modifier.weight(1f),
            contentPadding = BUTTON_PADDING
        ) {
            Text(
                text = stringResource(R.string.tool_approval_deny),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        GlassButton(
            onClick = { onDecision(ToolApprovalDecision.ALLOW_ONCE) },
            modifier = Modifier.weight(1f),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = BUTTON_PADDING
        ) {
            Text(
                text = stringResource(R.string.tool_approval_allow_once),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
    GlassButton(
        onClick = { onDecision(ToolApprovalDecision.ALLOW_SESSION) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = BUTTON_PADDING
    ) {
        Text(
            text = stringResource(R.string.tool_approval_allow_session),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PathLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun DiffPreview(preview: List<DiffLine>) {
    val darkTheme = LocalDarkTheme.current
    val addBackground = (if (darkTheme) AppleGreenDark else AppleGreenLight).copy(alpha = ROW_ALPHA)
    val removeBackground = (if (darkTheme) AppleRedDark else AppleRedLight).copy(alpha = ROW_ALPHA)
    val shown = preview.take(MAX_PREVIEW_ROWS)
    val hidden = preview.size - shown.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassTileShape)
            .padding(vertical = 8.dp)
    ) {
        shown.forEach { line ->
            Text(
                text = markerFor(line.kind) + line.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (line.kind == DiffKind.KEEP) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when (line.kind) {
                            DiffKind.ADD -> addBackground
                            DiffKind.REMOVE -> removeBackground
                            DiffKind.KEEP -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 1.dp)
            )
        }
        if (hidden > 0) {
            Text(
                text = stringResource(R.string.tool_approval_more, hidden),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun titleFor(kind: ToolApprovalKind): String = when (kind) {
    ToolApprovalKind.CREATE -> stringResource(R.string.tool_approval_create)
    ToolApprovalKind.WRITE -> stringResource(R.string.tool_approval_write)
    ToolApprovalKind.EDIT -> stringResource(R.string.tool_approval_edit)
    ToolApprovalKind.DELETE -> stringResource(R.string.tool_approval_delete)
    ToolApprovalKind.MOVE -> stringResource(R.string.tool_approval_move)
}

private fun markerFor(kind: DiffKind): String = when (kind) {
    DiffKind.ADD -> "+ "
    DiffKind.REMOVE -> "- "
    DiffKind.KEEP -> "  "
}

private const val MAX_SHEET_HEIGHT_RATIO = 0.8f
private const val MAX_PREVIEW_ROWS = 120
private const val ROW_ALPHA = 0.16f
private val BUTTON_PADDING = PaddingValues(vertical = 12.dp)
