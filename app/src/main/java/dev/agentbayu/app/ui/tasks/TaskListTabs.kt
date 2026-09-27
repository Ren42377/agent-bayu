package dev.agentbayu.app.ui.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.tasks.TaskList
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassTileShape

@Composable
internal fun TaskListTabs(
    lists: List<TaskList>,
    activeListId: String?,
    starredOpen: Boolean,
    onSelectStarred: () -> Unit,
    onSelectList: (String) -> Unit,
    onNewList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tabBounds = remember { mutableStateMapOf<String, Rect>() }
    val selectedKey = if (starredOpen) STARRED_KEY else activeListId.orEmpty()
    val targetBounds = tabBounds[selectedKey]
    val indicatorX by animateDpAsState(
        targetValue = with(density) { (targetBounds?.left ?: 0f).toDp() } + INDICATOR_INSET,
        animationSpec = indicatorSpring,
        label = "tabIndicatorX"
    )
    val indicatorY by animateDpAsState(
        targetValue = with(density) { (targetBounds?.bottom ?: 0f).toDp() } - INDICATOR_HEIGHT,
        animationSpec = indicatorSpring,
        label = "tabIndicatorY"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = with(density) { (targetBounds?.width ?: 0f).toDp() } - INDICATOR_INSET * 2,
        animationSpec = indicatorSpring,
        label = "tabIndicatorWidth"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (targetBounds == null) 0f else 1f,
        animationSpec = AgentBayuMotion.quickFade,
        label = "tabIndicatorAlpha"
    )
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskTab(
                    selected = starredOpen,
                    onClick = onSelectStarred,
                    onBounds = { bounds -> tabBounds[STARRED_KEY] = bounds }
                ) {
                    AnimatedContent(
                        targetState = starredOpen,
                        transitionSpec = {
                            (fadeIn(AgentBayuMotion.quickFade) + scaleIn(initialScale = TAB_ICON_ENTER_SCALE)) togetherWith
                                (fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = TAB_ICON_ENTER_SCALE))
                        },
                        label = "starredIcon"
                    ) { starred ->
                        Icon(
                            painter = painterResource(
                                if (starred) R.drawable.ic_star else R.drawable.ic_star_outline
                            ),
                            contentDescription = stringResource(R.string.tasks_tab_starred),
                            tint = tabColor(starred),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                lists.forEach { list ->
                    val selected = !starredOpen && list.id == activeListId
                    TaskTab(
                        selected = selected,
                        onClick = { onSelectList(list.id) },
                        onBounds = { bounds -> tabBounds[list.id] = bounds }
                    ) {
                        Text(
                            text = list.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = tabColor(selected),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = indicatorX, y = indicatorY)
                    .width(indicatorWidth)
                    .height(INDICATOR_HEIGHT)
                    .alpha(indicatorAlpha)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CapsuleShape)
            )
        }
        Row(
            modifier = Modifier
                .clip(CapsuleShape)
                .clickable(onClick = onNewList)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.tasks_tab_new_list),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TaskTab(
    selected: Boolean,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onGloballyPositioned { coordinates -> onBounds(coordinates.boundsInParent()) }
            .clip(GlassTileShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
        Spacer(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(INDICATOR_HEIGHT)
        )
    }
}

@Composable
private fun tabColor(selected: Boolean): Color = animateColorAsState(
    targetValue = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
    label = "tabColor"
).value

private val INDICATOR_HEIGHT = 2.dp
private val INDICATOR_INSET = 8.dp
private val indicatorSpring: AnimationSpec<Dp> = spring(
    dampingRatio = 0.75f,
    stiffness = Spring.StiffnessMedium
)
private const val STARRED_KEY = "starred"
private const val TAB_ICON_ENTER_SCALE = 0.85f
