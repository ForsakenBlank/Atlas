package com.forsakenblank.atlas.ui.timetable

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.theme.ItemColors
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubjectRow(val subject: Subject, val classes: Int, val openTasks: Int)

class SubjectsViewModel(private val repo: AtlasRepository) : ViewModel() {

    val rows: StateFlow<List<SubjectRow>?> = combine(repo.subjects(), repo.slots(), repo.tasks()) { subjects, slots, tasks ->
        subjects.map { subject ->
            SubjectRow(
                subject = subject,
                classes = slots.count { it.subjectId == subject.id },
                openTasks = tasks.count { it.subjectId == subject.id && !it.done },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(subject: Subject, makeFolder: Boolean) {
        viewModelScope.launch {
            repo.saveSubject(subject, makeFolder)
            // keeps the explorer folder named and coloured like its subject
            val folder = subject.folderId?.let { repo.item(it) } ?: return@launch
            if (folder.name != subject.name) repo.rename(folder, subject.name)
            if (folder.color != subject.color) repo.item(folder.id)?.let { repo.setColor(it, subject.color) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteSubject(id) }
    }
}

@Composable
fun SubjectsScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { SubjectsViewModel(it.repository) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val buzz = rememberHaptic()
    var editing by remember { mutableStateOf<Subject?>(null) }
    var deleting by remember { mutableStateOf<SubjectRow?>(null) }

    PageScaffold(
        title = "Subjects",
        onBack = navigator::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // start each new subject on a colour nobody else has yet
                    val used = rows.orEmpty().map { it.subject.color }
                    editing = Subject(name = "", color = ItemColors.firstOrNull { it !in used })
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add subject") },
            )
        },
    ) { padding ->
        val list = rows
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.School,
                        title = "No subjects yet",
                        body = "Add the subjects you study, each with a colour, teacher and room. " +
                            "Your classes and homework tasks are grouped under them.",
                    )
                }
            }
            items(list.orEmpty(), key = { it.subject.id }) { row ->
                SubjectCard(row = row, twoWeek = settings.twoWeekTimetable, onClick = { editing = row.subject })
            }
        }
    }

    val current = editing
    if (current != null) {
        SubjectDialog(
            initial = current,
            makeFolder = settings.makeSubjectFolders,
            onDismiss = { editing = null },
            onSave = { subject ->
                vm.save(subject, settings.makeSubjectFolders)
                buzz()
                editing = null
            },
            onDelete = {
                deleting = rows.orEmpty().firstOrNull { it.subject.id == current.id } ?: SubjectRow(current, 0, 0)
                editing = null
            },
        )
    }

    val doomed = deleting
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${doomed.subject.name}?") },
            text = { Text(deleteMessage(doomed)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(doomed.subject.id)
                        deleting = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun countsLine(row: SubjectRow, twoWeek: Boolean): String {
    val word = if (row.classes == 1) "class" else "classes"
    val classes = when {
        row.classes == 0 -> "No classes yet"
        twoWeek -> "${row.classes} $word across weeks A and B"
        else -> "${row.classes} $word a week"
    }
    val tasks = when (row.openTasks) {
        0 -> null
        1 -> "1 open task"
        else -> "${row.openTasks} open tasks"
    }
    return listOfNotNull(classes, tasks).joinToString(" · ")
}

private fun deleteMessage(row: SubjectRow): String {
    val classes = when (row.classes) {
        0 -> "It has no classes in your timetable."
        1 -> "Its class is removed from your timetable too."
        else -> "Its ${row.classes} classes are removed from your timetable too."
    }
    val kept = if (row.subject.folderId != null) "Its tasks and Explorer folder are kept." else "Its tasks are kept."
    return "$classes $kept"
}

@Composable
private fun SubjectCard(row: SubjectRow, twoWeek: Boolean, onClick: () -> Unit) {
    val subject = row.subject
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AtlasCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(subject.color.toItemColor(MaterialTheme.colorScheme.primary)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                val people = listOfNotNull(subject.teacher, subject.room).filter { it.isNotBlank() }
                if (people.isNotEmpty()) {
                    Text(people.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                Text(countsLine(row, twoWeek), style = MaterialTheme.typography.bodySmall, color = muted)
                if (subject.folderId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = muted, modifier = Modifier.size(14.dp))
                        Text("Has a folder in Explorer", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectDialog(
    initial: Subject,
    makeFolder: Boolean,
    onDismiss: () -> Unit,
    onSave: (Subject) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    var teacher by remember { mutableStateOf(initial.teacher.orEmpty()) }
    var room by remember { mutableStateOf(initial.room.orEmpty()) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    val isNew = initial.id == 0L
    val folderNote = when {
        initial.folderId != null -> "Has a folder in Explorer"
        makeFolder -> "A folder for this subject will be made in Explorer"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New subject" else "Edit subject") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                ColorRow(selected = color, onSelect = { color = it })
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Teacher (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folderNote != null) {
                    Text(folderNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            color = color,
                            teacher = teacher.trim().takeIf { it.isNotEmpty() },
                            room = room.trim().takeIf { it.isNotEmpty() },
                            notes = notes.trim().takeIf { it.isNotEmpty() },
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
