package com.forsakenblank.atlas.ui.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.forsakenblank.atlas.data.NoteFont
import com.forsakenblank.atlas.data.NoteSpacing
import com.forsakenblank.atlas.data.TextSize
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.util.MdEdit
import com.forsakenblank.atlas.util.continueList
import com.forsakenblank.atlas.util.insertBlock
import com.forsakenblank.atlas.util.markdownToPlain
import com.forsakenblank.atlas.util.numberLines
import com.forsakenblank.atlas.util.setHeading
import com.forsakenblank.atlas.util.toggleLinePrefix
import com.forsakenblank.atlas.util.toggleTaskLine
import com.forsakenblank.atlas.util.wrapSelection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.forsakenblank.atlas.ui.common.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.ColorPickerDialog
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.forsakenblank.atlas.platform.platform

class NoteEditorViewModel(
    private val id: Long,
    private val repo: AtlasRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val item = repo.observeItem(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var title by mutableStateOf("")
        private set
    var text by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    var markdown by mutableStateOf(false)
        private set

    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            title = repo.item(id)?.name.orEmpty()
            text = repo.noteText(id)
            markdown = repo.noteIsMarkdown(id)
            loaded = true
        }
    }

    fun onTitleChange(value: String) {
        title = value
        scheduleSave()
    }

    fun onTextChange(value: String) {
        text = value
        scheduleSave()
    }

    // saves a moment after typing stops instead of on every key
    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repo.saveNote(id, title, text)
            dirty = false
        }
    }

    // turning markdown into plain text can also take the symbols out of the words
    fun setMarkdown(on: Boolean, newText: String = text) {
        saveJob?.cancel()
        markdown = on
        text = newText
        viewModelScope.launch {
            repo.setNoteMarkdown(id, newText, on)
            repo.saveNote(id, title, newText)
            dirty = false
        }
    }

    fun togglePin() {
        val current = item.value ?: return
        viewModelScope.launch { repo.setPinned(current, !current.pinned) }
    }

    fun setColor(color: Int?) {
        val current = item.value ?: return
        viewModelScope.launch { repo.setColor(current, color) }
    }

    fun moveToTrash(done: () -> Unit) {
        val current = item.value ?: return
        saveJob?.cancel()
        dirty = false
        viewModelScope.launch {
            repo.moveToTrash(current)
            done()
        }
    }

    override fun onCleared() {
        // the view model scope is gone by now, so finish up on the app scope
        val pending = dirty
        val finalTitle = title
        val finalText = text
        val wasLoaded = loaded
        appScope.launch {
            if (pending) repo.saveNote(id, finalTitle, finalText)
            // a note left completely empty is not worth keeping
            val current = repo.item(id)
            if (wasLoaded && current != null && current.deletedAt == null && finalTitle.isBlank() && finalText.isBlank()) {
                repo.deleteForever(current)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(id: Long, navigator: AtlasNavigator) {
    val vm = atlasViewModel { NoteEditorViewModel(id, it.repository, it.appScope) }
    val settings = LocalSettings.current
    val app = atlasApp()
    val bodyStyle = MaterialTheme.typography.bodyLarge.let {
        it.copy(
            fontSize = it.fontSize * settings.editorTextSize.scale,
            lineHeight = it.lineHeight * settings.editorTextSize.scale * settings.noteSpacing.scale,
            fontFamily = when (settings.noteFont) {
                NoteFont.SANS -> FontFamily.SansSerif
                NoteFont.SERIF -> FontFamily.Serif
                NoteFont.MONO -> FontFamily.Monospace
            },
        )
    }
    val colors = MaterialTheme.colorScheme
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var preview by remember { mutableStateOf(false) }
    var lookMenu by remember { mutableStateOf(false) }
    var askConvert by remember { mutableStateOf(false) }
    LaunchedEffect(vm.loaded, vm.text) {
        if (field.text != vm.text) field = TextFieldValue(vm.text, TextRange(vm.text.length))
    }

    fun apply(edit: MdEdit) {
        field = TextFieldValue(edit.text, TextRange(edit.start, edit.end))
        vm.onTextChange(edit.text)
    }

    fun onTyped(value: TextFieldValue) {
        val next = if (vm.markdown && value.selection.collapsed) continueList(field.text, value.text, value.selection.start) else null
        if (next != null) {
            apply(next)
        } else {
            field = value
            if (value.text != vm.text) vm.onTextChange(value.text)
        }
    }
    val item by vm.item.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }
    val platform = platform()
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val tags = remember(vm.text) { AtlasRepository.findTags(vm.text) }
    val words = remember(vm.text) { vm.text.split(Regex("\\s+")).count { it.isNotBlank() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (vm.markdown) {
                        IconButton(onClick = { preview = !preview }) {
                            if (preview) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                            } else {
                                Icon(Icons.Outlined.Visibility, contentDescription = "Preview")
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { lookMenu = true }) { Icon(Icons.Outlined.FormatSize, contentDescription = "Text look") }
                        DropdownMenu(expanded = lookMenu, onDismissRequest = { lookMenu = false }) {
                            Text("Font", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            NoteFont.entries.forEach { f ->
                                DropdownMenuItem(text = { Text((if (settings.noteFont == f) "✓ " else "") + f.label) }, onClick = { app.updateSettings { it.copy(noteFont = f) } })
                            }
                            Text("Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            TextSize.entries.forEach { s ->
                                DropdownMenuItem(text = { Text((if (settings.editorTextSize == s) "✓ " else "") + s.label) }, onClick = { app.updateSettings { it.copy(editorTextSize = s) } })
                            }
                            Text("Line spacing", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            NoteSpacing.entries.forEach { s ->
                                DropdownMenuItem(text = { Text((if (settings.noteSpacing == s) "✓ " else "") + s.label) }, onClick = { app.updateSettings { it.copy(noteSpacing = s) } })
                            }
                        }
                    }
                    IconButton(onClick = vm::togglePin) {
                        if (item?.pinned == true) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Unpin", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.PushPin, contentDescription = "Pin")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (vm.markdown) "Turn into plain text (.txt)" else "Turn into markdown (.md)") },
                            leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                if (vm.markdown) {
                                    askConvert = true
                                } else {
                                    vm.setMarkdown(true)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Colour") },
                            leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                pickingColor = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val text = listOf(vm.title, vm.text).filter { it.isNotBlank() }.joinToString("\n\n")
                                platform.share(vm.title.ifBlank { null }, text)?.let { scope.launch { snackbar.showSnackbar(it) } }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy text") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(vm.text))
                                scope.launch { snackbar.showSnackbar("Copied", duration = SnackbarDuration.Short) }
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
            )
        },
    ) { padding ->
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (vm.loaded) {
                TextField(
                    value = vm.title,
                    onValueChange = vm::onTitleChange,
                    placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.markdown && !preview) {
                    val start = minOf(field.selection.start, field.selection.end)
                    val end = maxOf(field.selection.start, field.selection.end)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                        listOf<Pair<String, () -> Unit>>(
                            "B" to { apply(wrapSelection(field.text, start, end, "**")) },
                            "I" to { apply(wrapSelection(field.text, start, end, "*")) },
                            "U" to { apply(wrapSelection(field.text, start, end, "<u>", "</u>")) },
                            "S" to { apply(wrapSelection(field.text, start, end, "~~")) },
                            "Mark" to { apply(wrapSelection(field.text, start, end, "==")) },
                            "H1" to { apply(setHeading(field.text, start, end, 1)) },
                            "H2" to { apply(setHeading(field.text, start, end, 2)) },
                            "H3" to { apply(setHeading(field.text, start, end, 3)) },
                            "•" to { apply(toggleLinePrefix(field.text, start, end, "- ")) },
                            "1." to { apply(numberLines(field.text, start, end)) },
                            "☐" to { apply(toggleLinePrefix(field.text, start, end, "- [ ] ")) },
                            "❝" to { apply(toggleLinePrefix(field.text, start, end, "> ")) },
                            "</>" to { apply(wrapSelection(field.text, start, end, "`")) },
                            "Code" to { apply(wrapSelection(field.text, start, end, "```\n", "\n```")) },
                            "Link" to { apply(wrapSelection(field.text, start, end, "[", "](https://)")) },
                            "Line" to { apply(insertBlock(field.text, end, "---")) },
                            "Table" to { apply(insertBlock(field.text, end, "| Column | Column |\n| --- | --- |\n|  |  |")) },
                        ).forEach { (label, action) ->
                            TextButton(onClick = action) { Text(label, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                if (vm.markdown && preview) {
                    MarkdownView(
                        text = vm.text,
                        style = bodyStyle.copy(color = colors.onSurface),
                        onToggleTask = { line -> vm.onTextChange(toggleTaskLine(vm.text, line)) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else {
                    TextField(
                        value = field,
                        onValueChange = ::onTyped,
                        placeholder = { Text(if (vm.markdown) "Start writing. The bar above formats what you select." else "Start writing. Use #tags to group notes.") },
                        textStyle = bodyStyle,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        visualTransformation = if (vm.markdown) remember(colors) { MarkdownEditing(colors) } else VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { tag -> AssistChip(onClick = {}, label = { Text("#$tag") }) }
                    }
                }
                if (settings.showWordCount) {
                    Text(
                        "$words words, ${vm.text.length} characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (askConvert) {
        AlertDialog(
            onDismissRequest = { askConvert = false },
            title = { Text("Turn into plain text?") },
            text = { Text("Formatting stops showing. You can take the symbols like ** and # out of the words too, or keep them in case you change back.") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        askConvert = false
                        preview = false
                        vm.setMarkdown(false, markdownToPlain(vm.text))
                    }) { Text("Take the symbols out") }
                    TextButton(onClick = {
                        askConvert = false
                        preview = false
                        vm.setMarkdown(false)
                    }) { Text("Keep the symbols") }
                    TextButton(onClick = { askConvert = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (pickingColor) {
        ColorPickerDialog(
            initial = item?.color,
            onDismiss = { pickingColor = false },
            onConfirm = {
                vm.setColor(it)
                pickingColor = false
            },
        )
    }
}
