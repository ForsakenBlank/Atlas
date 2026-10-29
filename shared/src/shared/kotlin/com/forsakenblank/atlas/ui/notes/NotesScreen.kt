package com.forsakenblank.atlas.ui.notes

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import com.forsakenblank.atlas.ui.common.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.NoteLayout
import com.forsakenblank.atlas.data.NoteRow
import com.forsakenblank.atlas.data.NoteSort
import com.forsakenblank.atlas.data.Tag
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.SelectionBar
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toggle
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val repo: AtlasRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val selectedTag = MutableStateFlow<Tag?>(null)

    val tags = repo.usedTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes = combine(
        selectedTag.flatMapLatest { tag -> if (tag == null) repo.notes() else repo.notesWithTag(tag.id) },
        query,
    ) { notes, q ->
        if (q.isBlank()) notes
        else notes.filter { it.item.name.contains(q, ignoreCase = true) || it.text.orEmpty().contains(q, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleTag(tag: Tag) {
        selectedTag.value = if (selectedTag.value?.id == tag.id) null else tag
    }

    suspend fun newNote(): Long = repo.createNote(parentId = null)

    fun trash(ids: Set<Long>) {
        viewModelScope.launch { repo.moveToTrash(ids) }
    }

    fun restore(ids: Set<Long>) {
        viewModelScope.launch { repo.restoreAll(ids) }
    }

    fun setPinned(notes: List<NoteRow>, pinned: Boolean) {
        viewModelScope.launch { notes.forEach { repo.setPinned(it.item, pinned) } }
    }
}

private fun sortNotes(notes: List<NoteRow>, sort: NoteSort, pinnedFirst: Boolean): List<NoteRow> {
    val byChoice: Comparator<NoteRow> = when (sort) {
        NoteSort.EDITED -> compareByDescending { it.item.updated }
        NoteSort.CREATED -> compareByDescending { it.item.created }
        NoteSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.item.name.ifBlank { "untitled" } }
        NoteSort.COLOUR -> compareBy<NoteRow> { it.item.color == null }.thenBy { it.item.color ?: 0 }.thenByDescending { it.item.updated }
    }
    val order = if (pinnedFirst) compareByDescending<NoteRow> { it.item.pinned }.then(byChoice) else byChoice
    return notes.sortedWith(order)
}

@Composable
fun NotesScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { NotesViewModel(it.repository) }
    val notes by vm.notes.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val selectedTag by vm.selectedTag.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val sorted = remember(notes, settings.noteSort, settings.pinnedFirst) { sortNotes(notes, settings.noteSort, settings.pinnedFirst) }
    val grid = settings.noteLayout == NoteLayout.GRID
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmTrash by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val selecting = selected.isNotEmpty()

    LaunchedEffect(sorted) {
        val ids = sorted.map { it.item.id }.toSet()
        if (!ids.containsAll(selected)) selected = selected intersect ids
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(if (settings.noteLayout == NoteLayout.COMPACT) 6.dp else 10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (tags.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags, key = { it.id }) { tag ->
                            FilterChip(
                                selected = selectedTag?.id == tag.id,
                                onClick = { vm.toggleTag(tag) },
                                label = { Text("#${tag.name}") },
                            )
                        }
                    }
                }
            }
            if (sorted.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = if (query.isBlank() && selectedTag == null) "No notes yet" else "Nothing matches",
                        body = "Tap New note to start writing. Add #tags anywhere in the text to group notes.",
                    )
                }
            }
            gridItems(sorted, key = { it.item.id }) { note ->
                NoteCard(
                    note = note,
                    previewLines = if (settings.noteLayout == NoteLayout.COMPACT) 0 else settings.notePreviewLines,
                    showDate = settings.showNoteDates,
                    compact = settings.noteLayout == NoteLayout.COMPACT,
                    selected = note.item.id in selected,
                    onLongClick = { selected = selected.toggle(note.item.id) },
                ) {
                    if (selecting) selected = selected.toggle(note.item.id) else navigator.openNote(note.item.id)
                }
            }
        }

        if (selecting) {
            val chosen = sorted.filter { it.item.id in selected }
            val allPinned = chosen.all { it.item.pinned }
            SelectionBar(
                count = selected.size,
                onClear = { selected = emptySet() },
                onSelectAll = if (selected.size < sorted.size) ({ selected = sorted.map { it.item.id }.toSet() }) else null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) {
                IconButton(onClick = {
                    vm.setPinned(chosen, !allPinned)
                    selected = emptySet()
                }) {
                    Icon(if (allPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, contentDescription = if (allPinned) "Unpin" else "Pin")
                }
                IconButton(onClick = { confirmTrash = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Move to trash") }
            }
        } else {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { navigator.openNote(vm.newNote()) } },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New note") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (confirmTrash) {
        val count = selected.size
        val kept = if (settings.trashDays <= 0) "until you empty the trash" else "for ${settings.trashDays} days"
        ConfirmDialog(
            title = if (count == 1) "Move this note to the trash?" else "Move $count notes to the trash?",
            body = "You can restore ${if (count == 1) "it" else "them"} from the trash $kept.",
            button = "Move to trash",
            onDismiss = { confirmTrash = false },
        ) {
            val ids = selected
            selected = emptySet()
            vm.trash(ids)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val message = if (ids.size == 1) "Moved 1 note to the trash" else "Moved ${ids.size} notes to the trash"
                if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.restore(ids)
            }
        }
    }
}

@Composable
fun NoteCard(
    note: NoteRow,
    previewLines: Int = 2,
    showDate: Boolean = true,
    compact: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val outline = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape) else Modifier
    AtlasCard(modifier = Modifier.fillMaxWidth().then(outline), onClick = onClick, onLongClick = onLongClick) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // colour stripe down the side of the card
            note.item.color?.let { c ->
                Box(
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(Color(c))
                )
            }
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        note.item.name.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (compact && showDate) {
                        Text(formatDay(note.item.updated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (note.item.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val preview = note.text.orEmpty().trim()
                if (preview.isNotEmpty() && previewLines > 0) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = previewLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!compact && showDate) {
                    Spacer(Modifier.height(2.dp))
                    Text(formatDay(note.item.updated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
