package dev.agentbayu.app.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.firstImageReference
import dev.agentbayu.app.ui.components.imageModelFor
import dev.agentbayu.app.ui.components.pressScaleFeedback
import dev.agentbayu.app.ui.components.stripImageReferences
import dev.agentbayu.app.ui.tasks.dayLabel
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface
import dev.agentbayu.app.ui.components.GlassFab
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.domain.notes.NoteGroup

@Composable
fun NotesScreen(
    groups: List<NoteGroup>,
    activeGroup: NoteGroup?,
    pinnedOpen: Boolean,
    notes: List<NoteItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAddNote: () -> Unit,
    onOpenNote: (NoteItem) -> Unit,
    onNoteMenu: (NoteItem) -> Unit,
    onSelectPinned: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
    onGroupMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    val pageIndex = if (pinnedOpen) {
        0
    } else {
        groups.indexOfFirst { it.id == activeGroup?.id }.coerceAtLeast(0) + 1
    }
    val page = NotePage(index = pageIndex, notes = notes)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.notes_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                GlassIconButton(onClick = onGroupMenu, size = 42.dp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            NoteGroupTabs(
                groups = groups,
                activeGroupId = activeGroup?.id,
                pinnedOpen = pinnedOpen,
                onSelectPinned = onSelectPinned,
                onSelectGroup = onSelectGroup,
                onNewGroup = onNewGroup,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.notes_search_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn(AgentBayuMotion.quickFade) +
                            scaleIn(initialScale = CLEAR_ICON_ENTER_SCALE),
                        exit = fadeOut(AgentBayuMotion.quickFade) +
                            scaleOut(targetScale = CLEAR_ICON_ENTER_SCALE)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(GlassTileShape)
                                .clickable { onQueryChange("") }
                                .padding(4.dp)
                        )
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            AnimatedContent(
                targetState = page,
                contentKey = { state -> state.index },
                transitionSpec = {
                    val direction = if (targetState.index > initialState.index) 1 else -1
                    (
                        slideInHorizontally(AgentBayuMotion.navSlideSpec) { width ->
                            direction * width
                        } + fadeIn(AgentBayuMotion.navFadeSpec)
                    ) togetherWith (
                        slideOutHorizontally(AgentBayuMotion.navSlideSpec) { width ->
                            -direction * width
                        } + fadeOut(AgentBayuMotion.navFadeSpec)
                    )
                },
                label = "notePage",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { current ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 96.dp + insets.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (current.notes.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(
                                    if (query.isEmpty()) R.string.notes_empty
                                    else R.string.notes_search_empty
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                            )
                        }
                    }
                    items(current.notes, key = { it.id }) { note ->
                        NoteRowItem(
                            note = note,
                            onOpen = { onOpenNote(note) },
                            onMenu = { onNoteMenu(note) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        GlassFab(
            onClick = onAddNote,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = 20.dp + insets.calculateBottomPadding()
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.notes_add),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun NoteRowItem(
    note: NoteItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageReference = remember(note.content) { firstImageReference(note.content) }
    val snippet = remember(note.content) { stripImageReferences(note.content) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
            .clip(GlassCardShape)
            .clickable(onClick = onOpen)
            .pressScaleFeedback()
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.pinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(R.string.notes_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = note.title.ifEmpty { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (note.pinned) 6.dp else 0.dp)
                )
            }
            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = dayLabel(note.updatedAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (imageReference != null) {
            NoteThumbnail(reference = imageReference)
        }
        GlassIconButton(onClick = onMenu, size = 34.dp) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NoteThumbnail(reference: String) {
    val model = remember(reference) { imageModelFor(reference) }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(start = 8.dp, end = 4.dp)
            .size(NOTE_THUMBNAIL_SIZE)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    )
}

private val NOTE_THUMBNAIL_SIZE = 56.dp
private const val CLEAR_ICON_ENTER_SCALE = 0.85f

private data class NotePage(
    val index: Int,
    val notes: List<NoteItem>
)
