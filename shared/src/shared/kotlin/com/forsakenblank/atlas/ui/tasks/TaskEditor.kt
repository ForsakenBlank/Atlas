@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.forsakenblank.atlas.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatMinuteOfDay
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val longDay = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val longDayWithYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

private fun longDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(if (date.year == today.year) longDay else longDayWithYear)
}

// the date picker works in utc millis while tasks keep local days
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun TaskEditorDialog(
    task: Task?,
    initialDue: Long? = null,
    initialSubjectId: Long? = null,
    onDismiss: () -> Unit,
) {
    val app = atlasApp()
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val subjects by remember(app) { app.repository.subjects() }.collectAsState(initial = emptyList())
    val today = LocalDate.now()

    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var notes by remember(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var due by remember(task?.id) { mutableStateOf(if (task != null) task.due?.toLocalDate() else initialDue?.toLocalDate()) }
    var priority by remember(task?.id) { mutableIntStateOf(task?.priority?.coerceIn(0, 3) ?: 0) }
    var repeat by remember(task?.id) { mutableStateOf(task?.repeatRule ?: Repeat.NONE) }
    var subjectId by remember(task?.id) { mutableStateOf(if (task != null) task.subjectId else initialSubjectId) }
    var remind by remember(task?.id) { mutableStateOf(task?.remind ?: true) }
    var picking by remember { mutableStateOf(false) }

    // saving runs on the app scope so closing the dialog straight away cannot cut it short
    fun save() {
        val saved = (task ?: Task(title = "")).copy(
            title = title.trim(),
            notes = notes.trim().ifEmpty { null },
            due = due?.startMillis(),
            priority = priority,
            repeatRule = if (due == null) Repeat.NONE else repeat,
            subjectId = subjectId,
            remind = remind,
        )
        app.appScope.launch { app.repository.saveTask(saved) }
        onDismiss()
    }

    fun delete(existing: Task) {
        val offerUndo = settings.showUndo
        app.appScope.launch {
            app.repository.deleteTask(existing.id)
            if (offerUndo) {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar("Task deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) app.repository.saveTask(existing)
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // a little wider than usual so the priority buttons fit on small phones
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text(if (task == null) "New task" else "Edit task") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Due date")
                Text(
                    due?.let { longDayLabel(it, today) } ?: "No due date",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = due == today, onClick = { due = today }, label = { Text("Today") })
                    FilterChip(
                        selected = due == today.plusDays(1),
                        onClick = { due = today.plusDays(1) },
                        label = { Text("Tomorrow") },
                    )
                    FilterChip(
                        selected = due == today.plusWeeks(1),
                        onClick = { due = today.plusWeeks(1) },
                        label = { Text("Next week") },
                    )
                    AssistChip(
                        onClick = { picking = true },
                        label = { Text("Pick date") },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = {
                            due = null
                            repeat = Repeat.NONE
                        },
                        enabled = due != null,
                        label = { Text("Clear") },
                        leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                if (due != null) {
                    val remindersOff = !settings.remindersOn || !settings.remindTasks
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Remind me", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (remindersOff) "Task reminders are off in Settings"
                                else "On the morning it is due, at ${formatMinuteOfDay(settings.reminderMinute, settings.use24Hour)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                }

                FieldLabel("Priority")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    priorityNames.forEachIndexed { index, name ->
                        SegmentedButton(
                            selected = priority == index,
                            onClick = { priority = index },
                            shape = SegmentedButtonDefaults.itemShape(index, priorityNames.size),
                            // no tick, it leaves too little room for "Medium"
                            icon = {},
                        ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }

                FieldLabel("Repeat")
                ChoiceButton(
                    selected = repeat,
                    options = Repeat.entries,
                    label = { it.label() },
                    onSelect = { repeat = it },
                    enabled = due != null,
                )
                if (due == null) {
                    Text("Set a due date to make it repeat.", style = MaterialTheme.typography.bodySmall)
                } else if (repeat != Repeat.NONE) {
                    Text(
                        "The next one is added when you tick this one off. For birthdays, a countdown or an event works better.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (subjects.isNotEmpty()) {
                    val fallback = MaterialTheme.colorScheme.outline
                    FieldLabel("Subject")
                    ChoiceButton(
                        selected = subjects.firstOrNull { it.id == subjectId },
                        options = listOf<Subject?>(null) + subjects,
                        label = { it?.name ?: "None" },
                        onSelect = { subjectId = it?.id },
                        dot = { subject -> subject?.let { it.color.toItemColor(fallback) } },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (task != null) {
                    TextButton(
                        onClick = { delete(task) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = (due ?: today).toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { due = pickerMillisToDate(it) }
                        picking = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
}

// a button showing the current choice that opens a menu of the others
@Composable
private fun <T> ChoiceButton(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    dot: (T) -> Color? = { null },
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            val selectedDot = dot(selected)
            if (selectedDot != null) {
                ColourDot(selectedDot, size = 10.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(label(selected), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                val optionDot = dot(option)
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                    leadingIcon = if (optionDot != null) {
                        { ColourDot(optionDot, size = 12.dp) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
