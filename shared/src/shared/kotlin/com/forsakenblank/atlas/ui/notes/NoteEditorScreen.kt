package com.forsakenblank.atlas.ui.notes

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
import androidx.compose.material3.Icon
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

    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            title = repo.item(id)?.name.orEmpty()
            text = repo.noteText(id)
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
    val bodyStyle = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * settings.editorTextSize.scale, lineHeight = it.lineHeight * settings.editorTextSize.scale) }
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
                TextField(
                    value = vm.text,
                    onValueChange = vm::onTextChange,
                    placeholder = { Text("Start writing. Use #tags to group notes.") },
                    textStyle = bodyStyle,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
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
