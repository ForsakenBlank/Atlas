package com.forsakenblank.atlas.ui.sheets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.TextInputDialog
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.CellFormat
import com.forsakenblank.atlas.util.CellValue
import com.forsakenblank.atlas.util.ColumnStyle
import com.forsakenblank.atlas.util.DEFAULT_COLUMN_WIDTH
import com.forsakenblank.atlas.util.Sheet
import com.forsakenblank.atlas.util.SheetValues
import com.forsakenblank.atlas.util.cellName
import com.forsakenblank.atlas.util.cellText
import com.forsakenblank.atlas.util.columnName
import com.forsakenblank.atlas.util.toCsv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.forsakenblank.atlas.platform.platform

data class CellPos(val row: Int, val col: Int)

class SheetEditorViewModel(
    private val id: Long,
    private val repo: AtlasRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val item = repo.observeItem(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var sheet by mutableStateOf(Sheet())
        private set
    var values by mutableStateOf(SheetValues.EMPTY)
        private set
    var loaded by mutableStateOf(false)
        private set
    var selected by mutableStateOf(CellPos(0, 0))
        private set

    // what is in the formula bar, it only goes into the cell on enter or when you move away
    var draft by mutableStateOf(TextFieldValue(""))
        private set

    // earlier versions of the sheet, newest last
    private val history = mutableStateListOf<Sheet>()

    private var dirty = false
    private var trashed = false
    private var saveJob: Job? = null
    private var calcJob: Job? = null

    private val draftChanged: Boolean
        get() = loaded && draft.text != sheet.raw(selected.row, selected.col)

    val canUndo: Boolean
        get() = history.isNotEmpty() || draftChanged

    init {
        viewModelScope.launch {
            sheet = repo.sheet(id) ?: Sheet()
            recalc()
            loadDraft()
            loaded = true
        }
    }

    private fun loadDraft() {
        val raw = sheet.raw(selected.row, selected.col)
        draft = TextFieldValue(raw, TextRange(raw.length))
    }

    fun onDraftChange(value: TextFieldValue) {
        draft = value
    }

    // while a formula is being typed and the cursor sits after an operator, tapping a cell adds its name
    fun wantsRef(): Boolean {
        if (!draft.text.startsWith("=")) return false
        val before = draft.text.substring(0, draft.selection.min).trimEnd()
        return before.lastOrNull()?.let { it in "=(+-*/^&,;:<>" } == true
    }

    fun insertRef(row: Int, col: Int) {
        val name = cellName(row, col)
        val text = draft.text
        val start = draft.selection.min
        draft = TextFieldValue(text.substring(0, start) + name + text.substring(draft.selection.max), TextRange(start + name.length))
    }

    fun select(row: Int, col: Int) {
        commitDraft()
        selected = CellPos(row.coerceIn(0, sheet.rows - 1), col.coerceIn(0, sheet.columns - 1))
        loadDraft()
    }

    fun enter() {
        select(selected.row + 1, selected.col)
    }

    private fun commitDraft() {
        if (draftChanged) change(sheet.withCell(selected.row, selected.col, draft.text))
    }

    private fun change(next: Sheet) {
        if (next == sheet) return
        history.add(sheet)
        if (history.size > 100) history.removeAt(0)
        sheet = next
        recalc()
        scheduleSave()
    }

    // a typed but unsaved cell is undone first, then whole edits one at a time
    fun undo() {
        if (draftChanged) {
            loadDraft()
            return
        }
        sheet = history.removeLastOrNull() ?: return
        selected = CellPos(selected.row.coerceAtMost(sheet.rows - 1), selected.col.coerceAtMost(sheet.columns - 1))
        loadDraft()
        recalc()
        scheduleSave()
    }

    fun addRow() = reshape { it.insertRows(it.rows) }

    fun addColumn() = reshape { it.insertColumns(it.columns) }

    fun insertRow(at: Int) = reshape { it.insertRows(at) }

    fun deleteRow(row: Int) = reshape { it.deleteRows(row) }

    fun insertColumn(at: Int) = reshape { it.insertColumns(at) }

    fun deleteColumn(col: Int) = reshape { it.deleteColumns(col) }

    private fun reshape(transform: (Sheet) -> Sheet) {
        commitDraft()
        change(transform(sheet))
        selected = CellPos(selected.row.coerceAtMost(sheet.rows - 1), selected.col.coerceAtMost(sheet.columns - 1))
        loadDraft()
    }

    fun setStyle(col: Int, style: ColumnStyle) = change(sheet.withStyle(col, style))

    // small sheets update straight away, big ones are worked out off the main thread
    private fun recalc() {
        calcJob?.cancel()
        val snapshot = sheet
        if (snapshot.cells.size <= 2000) {
            values = snapshot.evaluate()
        } else {
            calcJob = viewModelScope.launch {
                values = withContext(Dispatchers.Default) { snapshot.evaluate() }
            }
        }
    }

    // saves a moment after the last change, like the note editor
    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repo.saveSheet(id, sheet)
            dirty = false
        }
    }

    // keeps typing that was not entered yet and saves now, for when the app goes to the background
    fun flush() {
        if (!loaded || trashed) return
        commitDraft()
        if (dirty) {
            saveJob?.cancel()
            dirty = false
            val snapshot = sheet
            appScope.launch { repo.saveSheet(id, snapshot) }
        }
    }

    fun csv(currencySymbol: String): String {
        commitDraft()
        return sheet.toCsv(sheet.evaluate(), currencySymbol)
    }

    fun rename(name: String) {
        val current = item.value ?: return
        viewModelScope.launch { repo.rename(current, name) }
    }

    fun moveToTrash(done: () -> Unit) {
        val current = item.value ?: return
        saveJob?.cancel()
        dirty = false
        trashed = true
        viewModelScope.launch {
            repo.moveToTrash(current)
            done()
        }
    }

    override fun onCleared() {
        if (!loaded || trashed) return
        // the view model scope is gone by now, so finish up on the app scope
        val final = if (draftChanged) sheet.withCell(selected.row, selected.col, draft.text) else sheet
        if (dirty || final != sheet) appScope.launch { repo.saveSheet(id, final) }
    }
}

private val RowHeaderWidth = 44.dp
private val HeaderHeight = 32.dp
private val CellHeight = 40.dp
private val WidthChoices = listOf("Narrow" to 64, "Normal" to DEFAULT_COLUMN_WIDTH, "Wide" to 144, "Extra wide" to 200)

@Composable
fun SheetEditorScreen(id: Long, navigator: AtlasNavigator) {
    val vm = atlasViewModel { SheetEditorViewModel(id, it.repository, it.appScope) }
    val item by vm.item.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val platform = platform()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = LocalSnackbar.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val barFocus = remember { FocusRequester() }
    var barFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var styling by remember { mutableStateOf<Int?>(null) }
    val sheet = vm.sheet
    val name = item?.name?.ifBlank { null } ?: "Sheet"

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun copyCsv() {
        val csv = vm.csv(settings.currencySymbol)
        if (csv.isEmpty()) return message("This sheet is empty")
        clipboard.setText(AnnotatedString(csv))
        message("Copied as CSV")
    }

    fun shareCsv() {
        val csv = vm.csv(settings.currencySymbol)
        if (csv.isEmpty()) return message("This sheet is empty")
        platform.share("$name.csv", csv)?.let(::message)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    // keeps the selected cell on screen when enter moves it down past the edge
    LaunchedEffect(vm.selected) {
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo
            .filter { it.index < sheet.rows && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
            .map { it.index }
        val row = vm.selected.row
        if (shown.isNotEmpty()) {
            if (row < shown.first()) listState.animateScrollToItem(row)
            if (row > shown.last()) listState.animateScrollToItem((row - shown.size + 1).coerceAtLeast(0))
        }
        val col = vm.selected.col
        val left = with(density) { (0 until col).sumOf { sheet.style(it).width }.dp.roundToPx() }
        val right = left + with(density) { sheet.style(col).width.dp.roundToPx() }
        val viewport = hScroll.viewportSize
        if (viewport > 0) {
            if (left < hScroll.value) hScroll.animateScrollTo(left)
            if (right > hScroll.value + viewport) hScroll.animateScrollTo(right - viewport)
        }
    }

    PageScaffold(
        title = name,
        onBack = navigator::back,
        actions = {
            IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Add row") },
                    leadingIcon = { Icon(Icons.Outlined.TableRows, contentDescription = null) },
                    enabled = sheet.rows < Sheet.MAX_ROWS,
                    onClick = {
                        menuOpen = false
                        vm.addRow()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add column") },
                    leadingIcon = { Icon(Icons.Outlined.ViewColumn, contentDescription = null) },
                    enabled = sheet.columns < Sheet.MAX_COLUMNS,
                    onClick = {
                        menuOpen = false
                        vm.addColumn()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy as CSV") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        copyCsv()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Share as CSV") },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        shareCsv()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        renaming = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Move to trash") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        vm.moveToTrash { navigator.back() }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize(),
        ) {
            if (vm.loaded) {
                FormulaBar(
                    cell = cellName(vm.selected.row, vm.selected.col),
                    value = vm.draft,
                    onChange = vm::onDraftChange,
                    onEnter = vm::enter,
                    modifier = Modifier
                        .focusRequester(barFocus)
                        .onFocusChanged { barFocused = it.isFocused },
                )
                HorizontalDivider()
                SheetGrid(
                    sheet = sheet,
                    values = vm.values,
                    selected = vm.selected,
                    currencySymbol = settings.currencySymbol,
                    listState = listState,
                    hScroll = hScroll,
                    onCell = { row, col ->
                        val same = row == vm.selected.row && col == vm.selected.col
                        when {
                            barFocused && !same && vm.wantsRef() -> vm.insertRef(row, col)
                            same -> {
                                barFocus.requestFocus()
                                keyboard?.show()
                            }
                            else -> vm.select(row, col)
                        }
                    },
                    rowMenu = { row, close ->
                        DropdownMenuItem(
                            text = { Text("Insert row above") },
                            enabled = sheet.rows < Sheet.MAX_ROWS,
                            onClick = {
                                close()
                                vm.insertRow(row)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Insert row below") },
                            enabled = sheet.rows < Sheet.MAX_ROWS,
                            onClick = {
                                close()
                                vm.insertRow(row + 1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete row") },
                            enabled = sheet.rows > 1,
                            onClick = {
                                close()
                                vm.deleteRow(row)
                            },
                        )
                    },
                    columnMenu = { col, close ->
                        DropdownMenuItem(
                            text = { Text("Insert column left") },
                            enabled = sheet.columns < Sheet.MAX_COLUMNS,
                            onClick = {
                                close()
                                vm.insertColumn(col)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Insert column right") },
                            enabled = sheet.columns < Sheet.MAX_COLUMNS,
                            onClick = {
                                close()
                                vm.insertColumn(col + 1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Format and width") },
                            onClick = {
                                close()
                                styling = col
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete column") },
                            enabled = sheet.columns > 1,
                            onClick = {
                                close()
                                vm.deleteColumn(col)
                            },
                        )
                    },
                    onAddRow = vm::addRow,
                    onAddColumn = vm::addColumn,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Rename",
            initial = item?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                vm.rename(it)
                renaming = false
            },
        )
    }

    styling?.let { col ->
        ColumnStyleDialog(
            col = col,
            initial = sheet.style(col),
            currencySymbol = settings.currencySymbol,
            onDismiss = { styling = null },
            onSave = {
                vm.setStyle(col, it)
                styling = null
            },
        )
    }
}

@Composable
private fun FormulaBar(
    cell: String,
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            cell,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("Value or =formula") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEnter() }),
            trailingIcon = if (value.text.isEmpty()) null else {
                {
                    IconButton(onClick = { onChange(TextFieldValue("")) }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = modifier.weight(1f),
        )
        FilledIconButton(onClick = onEnter) {
            Icon(Icons.Outlined.Check, contentDescription = "Enter")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetGrid(
    sheet: Sheet,
    values: SheetValues,
    selected: CellPos,
    currencySymbol: String,
    listState: LazyListState,
    hScroll: ScrollState,
    onCell: (Int, Int) -> Unit,
    rowMenu: @Composable ColumnScope.(row: Int, close: () -> Unit) -> Unit,
    columnMenu: @Composable ColumnScope.(col: Int, close: () -> Unit) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = MaterialTheme.colorScheme.outlineVariant
    var rowMenuFor by remember { mutableStateOf<Int?>(null) }
    var columnMenuFor by remember { mutableStateOf<Int?>(null) }

    // every row scrolls sideways on its own, a stretch at the edge would pull just one row out of line
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(modifier) {
            // column letters stay put while the rows scroll under them
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(
                    Modifier
                        .size(RowHeaderWidth, HeaderHeight)
                        .gridLines(lines),
                )
                Row(Modifier.horizontalScroll(hScroll)) {
                    for (col in 0 until sheet.columns) {
                        HeaderCell(
                            label = columnName(col),
                            width = sheet.style(col).width.dp,
                            height = HeaderHeight,
                            highlighted = col == selected.col,
                            lines = lines,
                            onOpen = { columnMenuFor = col },
                        ) {
                            DropdownMenu(expanded = columnMenuFor == col, onDismissRequest = { columnMenuFor = null }) {
                                columnMenu(col) { columnMenuFor = null }
                            }
                        }
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(sheet.rows, key = { it }) { row ->
                    Row {
                        HeaderCell(
                            label = "${row + 1}",
                            width = RowHeaderWidth,
                            height = CellHeight,
                            highlighted = row == selected.row,
                            lines = lines,
                            onOpen = { rowMenuFor = row },
                        ) {
                            DropdownMenu(expanded = rowMenuFor == row, onDismissRequest = { rowMenuFor = null }) {
                                rowMenu(row) { rowMenuFor = null }
                            }
                        }
                        GridRow(
                            row = row,
                            sheet = sheet,
                            values = values,
                            selectedCol = if (row == selected.row) selected.col else -1,
                            currencySymbol = currencySymbol,
                            lines = lines,
                            hScroll = hScroll,
                            onCell = onCell,
                        )
                    }
                }
                item {
                    Row(Modifier.padding(start = RowHeaderWidth, top = 4.dp, bottom = 16.dp)) {
                        TextButton(onClick = onAddRow, enabled = sheet.rows < Sheet.MAX_ROWS) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add row")
                        }
                        TextButton(onClick = onAddColumn, enabled = sheet.columns < Sheet.MAX_COLUMNS) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add column")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridRow(
    row: Int,
    sheet: Sheet,
    values: SheetValues,
    selectedCol: Int,
    currencySymbol: String,
    lines: Color,
    hScroll: ScrollState,
    onCell: (Int, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.horizontalScroll(hScroll)) {
        for (col in 0 until sheet.columns) {
            val raw = sheet.raw(row, col)
            val value = values[row, col]
            val style = sheet.style(col)
            val align = when (value) {
                is CellValue.Number -> TextAlign.End
                is CellValue.Error, is CellValue.Bool -> TextAlign.Center
                else -> TextAlign.Start
            }
            Box(
                Modifier
                    .size(style.width.dp, CellHeight)
                    .gridLines(lines)
                    .then(if (col == selectedCol) Modifier.border(2.dp, colors.primary) else Modifier)
                    .clickable { onCell(row, col) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    cellText(raw, value, style.format, currencySymbol),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value is CellValue.Error) colors.error else colors.onSurface,
                    textAlign = align,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// a column letter or row number, tap or long press it for insert, delete and format
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderCell(
    label: String,
    width: Dp,
    height: Dp,
    highlighted: Boolean,
    lines: Color,
    onOpen: () -> Unit,
    menu: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(width, height)
            .background(if (highlighted) colors.primaryContainer else colors.surfaceContainerHigh)
            .gridLines(lines)
            .combinedClickable(onClick = onOpen, onLongClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
        menu()
    }
}

// thin lines on the right and bottom edges, so neighbouring cells share one line
private fun Modifier.gridLines(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
    drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnStyleDialog(
    col: Int,
    initial: ColumnStyle,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (ColumnStyle) -> Unit,
) {
    var format by remember { mutableStateOf(initial.format) }
    var width by remember { mutableStateOf(initial.width) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Column ${columnName(col)}") },
        text = {
            Column {
                SectionTitle("Numbers")
                CellFormat.entries.forEach { choice ->
                    val sample = if (choice == CellFormat.PERCENT) 0.25 else 1234.5
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = format == choice, onClick = { format = choice }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = format == choice, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(choice.label)
                            Text(
                                cellText("=$sample", CellValue.Number(sample), choice, currencySymbol),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SectionTitle("Width", Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidthChoices.forEach { (label, dp) ->
                        FilterChip(selected = width == dp, onClick = { width = dp }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ColumnStyle(width = width, format = format)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
