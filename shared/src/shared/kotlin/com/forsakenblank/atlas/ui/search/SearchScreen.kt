package com.forsakenblank.atlas.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.data.NoteRow
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.icon
import com.forsakenblank.atlas.ui.tasks.TaskEditorDialog
import com.forsakenblank.atlas.util.formatDay
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SearchResults(
    val query: String = "",
    val notes: List<NoteRow> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val events: List<Event> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    val items: List<Item> = emptyList(),
) {
    val isEmpty: Boolean get() = notes.isEmpty() && tasks.isEmpty() && events.isEmpty() && countdowns.isEmpty() && items.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(repo: AtlasRepository) : ViewModel() {

    val query = MutableStateFlow("")

    val results = query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isEmpty()) {
                flowOf(SearchResults())
            } else {
                combine(
                    repo.notes(),
                    repo.tasks(),
                    repo.eventsBetween(0, Long.MAX_VALUE),
                    repo.countdowns(),
                    repo.searchItems(q),
                ) { notes, tasks, events, countdowns, items ->
                    fun String?.has() = this != null && contains(q, ignoreCase = true)
                    SearchResults(
                        query = q,
                        notes = notes.filter { it.item.name.has() || it.text.has() }.sortedByDescending { it.item.updated },
                        tasks = tasks.filter { it.title.has() || it.notes.has() }.sortedWith(compareBy({ it.done }, { it.due ?: Long.MAX_VALUE })),
                        events = events.filter { it.title.has() || it.location.has() || it.notes.has() }.sortedByDescending { it.startsAt },
                        countdowns = countdowns.filter { it.title.has() || it.note.has() },
                        // notes already have their own group
                        items = items.filter { it.type != ItemType.NOTE },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
}

// a few words either side of the first match, so you can see why a note came up
private fun snippet(text: String, query: String): String? {
    val at = text.indexOf(query, ignoreCase = true)
    if (at < 0) return null
    val start = text.lastIndexOf(' ', (at - 30).coerceAtLeast(0)).let { if (it < 0 || at - it > 40) (at - 30).coerceAtLeast(0) else it + 1 }
    val end = (at + query.length + 60).coerceAtMost(text.length)
    val clip = text.substring(start, end).replace('\n', ' ').trim()
    return (if (start > 0) "...$clip" else clip) + if (end < text.length) "..." else ""
}

@Composable
fun SearchScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { SearchViewModel(it.repository) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var editingTask by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    PageScaffold(title = "Search", onBack = navigator::back) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Notes, tasks, events, trackers and more") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(bottom = 8.dp),
                )
            }

            if (results.query.isNotEmpty() && results.isEmpty) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Nothing found",
                        body = "Nothing has \"${results.query}\" in its name or text.",
                    )
                }
            }

            group("Notes", results.notes, key = { "note-${it.item.id}" }) { note ->
                ResultRow(
                    icon = Icons.Outlined.Description,
                    title = note.item.name.ifBlank { "Untitled" },
                    detail = snippet(note.text.orEmpty(), results.query) ?: formatDay(note.item.updated),
                ) { navigator.openNote(note.item.id) }
            }
            group("Tasks", results.tasks, key = { "task-${it.id}" }) { task ->
                ResultRow(
                    icon = Icons.Outlined.Checklist,
                    title = task.title,
                    detail = when {
                        task.done -> "Done"
                        task.due != null -> "Due ${formatDay(task.due)}"
                        else -> "No due date"
                    },
                ) { editingTask = task }
            }
            group("Events", results.events, key = { "event-${it.id}" }) { event ->
                ResultRow(
                    icon = Icons.Outlined.Event,
                    title = event.title,
                    detail = listOfNotNull(formatDay(event.startsAt), event.location?.takeIf { it.isNotBlank() }).joinToString(", "),
                    tint = event.color?.let { Color(it) },
                ) { navigator.openEvent(event.id, event.startsAt.toLocalDate().toEpochDay()) }
            }
            group("Countdowns", results.countdowns, key = { "countdown-${it.id}" }) { countdown ->
                ResultRow(
                    icon = Icons.Outlined.Cake,
                    title = listOfNotNull(countdown.emoji?.takeIf { it.isNotBlank() }, countdown.title).joinToString(" "),
                    detail = countdown.label(),
                    tint = countdown.color?.let { Color(it) },
                ) { navigator.openCountdown(countdown.id) }
            }
            group("Trackers, sheets and folders", results.items, key = { "item-${it.id}" }) { item ->
                ResultRow(
                    icon = item.type.icon(),
                    title = item.name.ifBlank { "Untitled" },
                    detail = when (item.type) {
                        ItemType.FOLDER -> "Folder"
                        ItemType.TRACKER -> "Tracker"
                        ItemType.SHEET -> "Sheet, edited ${formatDay(item.updated).lowercase()}"
                        ItemType.NOTE -> "Note"
                    },
                    tint = item.color?.let { Color(it) },
                ) {
                    when (item.type) {
                        ItemType.TRACKER -> navigator.openTracker(item.id)
                        ItemType.SHEET -> navigator.openSheet(item.id)
                        ItemType.NOTE -> navigator.openNote(item.id)
                        ItemType.FOLDER -> navigator.openSection(Section.EXPLORER, isTab = false)
                    }
                }
            }
        }
    }

    editingTask?.let { task ->
        TaskEditorDialog(task = task, onDismiss = { editingTask = null })
    }
}

private fun <T> LazyListScope.group(title: String, rows: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item(key = "title-$title") { SectionTitle("$title (${rows.size})", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
    items(rows.take(30), key = key) { row(it) }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, detail: String, tint: Color? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
