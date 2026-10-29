package com.forsakenblank.atlas.ui.explorer

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.ExplorerSort
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.ColorPickerDialog
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.TextInputDialog
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.icon
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.track.NewTrackerDialog
import com.forsakenblank.atlas.util.formatDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerViewModel(private val repo: AtlasRepository) : ViewModel() {

    val folderId = MutableStateFlow<Long?>(null)

    val children = folderId.flatMapLatest { repo.children(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val path = folderId.mapLatest { repo.pathTo(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = repo.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(id: Long?) {
        folderId.value = id
    }

    fun up() {
        folderId.value = path.value.dropLast(1).lastOrNull()?.id
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repo.createFolder(name, folderId.value) }
    }

    suspend fun createNote(): Long = repo.createNote(folderId.value)

    suspend fun createSheet(name: String): Long = repo.createSheet(folderId.value, name)

    fun rename(item: Item, name: String) {
        viewModelScope.launch { repo.rename(item, name) }
    }

    fun setColor(item: Item, color: Int?) {
        viewModelScope.launch { repo.setColor(item, color) }
    }

    fun move(item: Item, to: Long?) {
        viewModelScope.launch { repo.move(item, to) }
    }

    fun trash(item: Item) {
        viewModelScope.launch { repo.moveToTrash(item) }
    }
}

private fun sortItems(items: List<Item>, sort: ExplorerSort, foldersFirst: Boolean): List<Item> {
    val byChoice: Comparator<Item> = when (sort) {
        ExplorerSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        ExplorerSort.EDITED -> compareByDescending { it.updated }
        ExplorerSort.CREATED -> compareByDescending { it.created }
        ExplorerSort.TYPE -> compareBy<Item> { it.type.ordinal }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val order = if (foldersFirst) compareBy<Item> { it.type != ItemType.FOLDER }.then(byChoice) else byChoice
    return items.sortedWith(order)
}

private sealed interface ExplorerDialog {
    data object NewFolder : ExplorerDialog
    data object NewTracker : ExplorerDialog
    data object NewSheet : ExplorerDialog
    data class Rename(val item: Item) : ExplorerDialog
    data class Colour(val item: Item) : ExplorerDialog
    data class Move(val item: Item) : ExplorerDialog
    data class Trash(val item: Item) : ExplorerDialog
}

@Composable
fun ExplorerScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { ExplorerViewModel(it.repository) }
    val folderId by vm.folderId.collectAsStateWithLifecycle()
    val unsorted by vm.children.collectAsStateWithLifecycle()
    val path by vm.path.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val children = remember(unsorted, settings.explorerSort, settings.foldersFirst) {
        sortItems(unsorted, settings.explorerSort, settings.foldersFirst)
    }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ExplorerDialog?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = folderId != null) { vm.up() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = if (settings.explorerGrid) GridCells.Adaptive(120.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Breadcrumbs(path, onOpen = vm::open) }
            if (children.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = "This folder is empty",
                        body = "Use the + button to add a folder, note, tracker or sheet here.",
                    )
                }
            }
            items(children, key = { it.id }) { item ->
                ExplorerRow(
                    item = item,
                    tile = settings.explorerGrid,
                    onOpen = {
                        when (item.type) {
                            ItemType.FOLDER -> vm.open(item.id)
                            ItemType.NOTE -> navigator.openNote(item.id)
                            ItemType.TRACKER -> navigator.openTracker(item.id)
                            ItemType.SHEET -> navigator.openSheet(item.id)
                        }
                    },
                    onRename = { dialog = ExplorerDialog.Rename(item) },
                    onColour = { dialog = ExplorerDialog.Colour(item) },
                    onMove = { dialog = ExplorerDialog.Move(item) },
                    onTrash = { if (settings.confirmTrash) dialog = ExplorerDialog.Trash(item) else vm.trash(item) },
                )
            }
            if (folderId == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListItem(
                        headlineContent = { Text("Trash") },
                        supportingContent = {
                            Text(if (settings.trashDays <= 0) "Deleted items stay until you empty it" else "Deleted items are kept for ${settings.trashDays} days")
                        },
                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        modifier = Modifier.clickable { navigator.openTrash() },
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { addMenu = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add")
            }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Folder") },
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        dialog = ExplorerDialog.NewFolder
                    },
                )
                DropdownMenuItem(
                    text = { Text("Note") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        scope.launch { navigator.openNote(vm.createNote()) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tracker") },
                    leadingIcon = { Icon(Icons.Outlined.Insights, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        dialog = ExplorerDialog.NewTracker
                    },
                )
                DropdownMenuItem(
                    text = { Text("Sheet") },
                    leadingIcon = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        dialog = ExplorerDialog.NewSheet
                    },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        ExplorerDialog.NewFolder -> TextInputDialog(
            title = "New folder",
            confirm = "Create",
            onDismiss = { dialog = null },
            onConfirm = {
                vm.createFolder(it)
                dialog = null
            },
        )
        ExplorerDialog.NewTracker -> NewTrackerDialog(
            parentId = folderId,
            onDismiss = { dialog = null },
            onCreated = { dialog = null },
        )
        ExplorerDialog.NewSheet -> TextInputDialog(
            title = "New sheet",
            confirm = "Create",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                scope.launch { navigator.openSheet(vm.createSheet(name)) }
            },
        )
        is ExplorerDialog.Rename -> TextInputDialog(
            title = "Rename",
            initial = d.item.name,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.rename(d.item, it)
                dialog = null
            },
        )
        is ExplorerDialog.Colour -> ColorPickerDialog(
            initial = d.item.color,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.setColor(d.item, it)
                dialog = null
            },
        )
        is ExplorerDialog.Trash -> ConfirmDialog(
            title = "Move to trash?",
            body = if (d.item.type == ItemType.FOLDER) "${d.item.name} and everything in it goes to the trash." else "${d.item.name.ifBlank { "This item" }} goes to the trash.",
            button = "Move to trash",
            onDismiss = { dialog = null },
        ) { vm.trash(d.item) }
        is ExplorerDialog.Move -> MoveDialog(
            item = d.item,
            folders = vm.folders.collectAsStateWithLifecycle().value,
            onDismiss = { dialog = null },
            onMove = {
                vm.move(d.item, it)
                dialog = null
            },
        )
    }
}

@Composable
private fun Breadcrumbs(path: List<Item>, onOpen: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            AssistChip(
                onClick = { onOpen(null) },
                label = { Text("All") },
                leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            )
        }
        itemsIndexed(path, key = { _, folder -> folder.id }) { _, folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                AssistChip(onClick = { onOpen(folder.id) }, label = { Text(folder.name) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerRow(
    item: Item,
    tile: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onColour: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val tint = item.color.toItemColor(MaterialTheme.colorScheme.onSurfaceVariant)
    val kind = when (item.type) {
        ItemType.FOLDER -> "Folder"
        ItemType.NOTE -> "Note"
        ItemType.TRACKER -> "Tracker"
        ItemType.SHEET -> "Sheet"
    }
    Box {
        if (tile) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.type.icon(), contentDescription = kind, tint = tint, modifier = Modifier.size(40.dp))
                Text(
                    item.name.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            ListItem(
                headlineContent = { Text(item.name.ifBlank { "Untitled" }) },
                supportingContent = { Text("$kind, edited ${formatDay(item.updated).lowercase()}") },
                leadingContent = { Icon(item.type.icon(), contentDescription = null, tint = tint) },
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menu = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Colour") },
                leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                onClick = { menu = false; onColour() },
            )
            DropdownMenuItem(
                text = { Text("Move") },
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                onClick = { menu = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text("Move to trash") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { menu = false; onTrash() },
            )
        }
    }
}

@Composable
private fun MoveDialog(item: Item, folders: List<Item>, onDismiss: () -> Unit, onMove: (Long?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${item.name.ifBlank { "item" }} to") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("All (top level)") },
                        leadingContent = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        modifier = Modifier.clickable { onMove(null) },
                    )
                }
                listItems(folders.filter { it.id != item.id }, key = { it.id }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = {
                            Icon(ItemType.FOLDER.icon(), contentDescription = null, tint = folder.color.toItemColor(MaterialTheme.colorScheme.onSurfaceVariant))
                        },
                        modifier = Modifier.clickable { onMove(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
