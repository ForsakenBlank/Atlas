package com.forsakenblank.atlas.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.SheetRow
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.TextInputDialog
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.Sheet
import com.forsakenblank.atlas.util.formatDay
import com.forsakenblank.atlas.util.isFormula
import com.forsakenblank.atlas.util.parseCellRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// what the list needs from each sheet, worked out once per change
class SheetSummary(val item: Item, val rows: Int, val columns: Int, val preview: String, private val content: String) {

    fun matches(query: String): Boolean =
        item.name.contains(query, ignoreCase = true) || content.contains(query, ignoreCase = true)

    companion object {
        fun from(row: SheetRow): SheetSummary {
            val sheet = row.json?.let { Sheet.fromJson(it) } ?: Sheet()
            // the first row with something typed in it usually holds the headings
            val firstRow = sheet.cells.keys.mapNotNull { parseCellRef(it)?.row }.minOrNull()
            val preview = if (firstRow == null) "" else (0 until sheet.columns)
                .map { sheet.raw(firstRow, it) }
                .filter { it.isNotBlank() && !isFormula(it) }
                .joinToString(", ")
            return SheetSummary(row.item, sheet.rows, sheet.columns, preview, sheet.cells.values.joinToString(" "))
        }
    }
}

class SheetsViewModel(private val repo: AtlasRepository) : ViewModel() {

    val query = MutableStateFlow("")

    val sheets = combine(repo.sheets(), query) { rows, q ->
        val all = rows.map { SheetSummary.from(it) }
        if (q.isBlank()) all else all.filter { it.matches(q.trim()) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // new sheets go at the top level, the same as new notes
    suspend fun newSheet(name: String): Long = repo.createSheet(parentId = null, name = name)

    fun rename(item: Item, name: String) {
        viewModelScope.launch { repo.rename(item, name) }
    }

    fun trash(item: Item) {
        viewModelScope.launch { repo.moveToTrash(item) }
    }
}

private sealed interface SheetsDialog {
    data object New : SheetsDialog
    data class Rename(val item: Item) : SheetsDialog
    data class Trash(val item: Item) : SheetsDialog
}

@Composable
fun SheetsScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { SheetsViewModel(it.repository) }
    val sheets by vm.sheets.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<SheetsDialog?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search sheets") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (sheets.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.TableChart,
                        title = if (query.isBlank()) "No sheets yet" else "Nothing matches",
                        body = "Tap New sheet to start one. Cells take numbers, text or formulas like =SUM(A1:A5).",
                    )
                }
            }
            items(sheets, key = { it.item.id }) { sheet ->
                SheetCard(
                    sheet = sheet,
                    onOpen = { navigator.openSheet(sheet.item.id) },
                    onRename = { dialog = SheetsDialog.Rename(sheet.item) },
                    onTrash = { if (settings.confirmTrash) dialog = SheetsDialog.Trash(sheet.item) else vm.trash(sheet.item) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { dialog = SheetsDialog.New },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New sheet") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    when (val d = dialog) {
        null -> Unit
        SheetsDialog.New -> TextInputDialog(
            title = "New sheet",
            confirm = "Create",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                scope.launch { navigator.openSheet(vm.newSheet(name)) }
            },
        )
        is SheetsDialog.Rename -> TextInputDialog(
            title = "Rename",
            initial = d.item.name,
            onDismiss = { dialog = null },
            onConfirm = {
                vm.rename(d.item, it)
                dialog = null
            },
        )
        is SheetsDialog.Trash -> ConfirmDialog(
            title = "Move to trash?",
            body = "${d.item.name.ifBlank { "This sheet" }} goes to the trash.",
            button = "Move to trash",
            onDismiss = { dialog = null },
        ) { vm.trash(d.item) }
    }
}

@Composable
private fun SheetCard(sheet: SheetSummary, onOpen: () -> Unit, onRename: () -> Unit, onTrash: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        AtlasCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen, onLongClick = { menu = true }) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.TableChart,
                    contentDescription = null,
                    tint = sheet.item.color.toItemColor(MaterialTheme.colorScheme.primary),
                )
                Column(
                    Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        sheet.item.name.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sheet.preview.isNotEmpty()) {
                        Text(
                            sheet.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${sheet.rows} rows by ${sheet.columns} columns, edited ${formatDay(sheet.item.updated).lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Move to trash") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    menu = false
                    onTrash()
                },
            )
        }
    }
}
