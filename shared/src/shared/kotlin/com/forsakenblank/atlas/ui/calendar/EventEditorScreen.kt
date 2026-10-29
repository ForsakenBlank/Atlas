package com.forsakenblank.atlas.ui.calendar

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.atMinute
import com.forsakenblank.atlas.util.formatMinuteOfDay
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.NO_REMINDER
import com.forsakenblank.atlas.util.ReminderMinuteOptions
import com.forsakenblank.atlas.util.minuteOfDay
import com.forsakenblank.atlas.util.reminderMinutesLabel
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class EventEditorViewModel(private val id: Long, firstDay: Long, defaultMinutes: Int, private val repo: AtlasRepository) : ViewModel() {

    var loaded by mutableStateOf(id == 0L)
        private set
    var original: Event? = null
        private set

    var title by mutableStateOf("")
    var allDay by mutableStateOf(false)
    var startDate by mutableStateOf(if (firstDay > 0) LocalDate.ofEpochDay(firstDay) else LocalDate.now())
    var startMinute by mutableStateOf(defaultStart(firstDay))
    var endDate by mutableStateOf(startDate)
    var endMinute by mutableStateOf((startMinute + defaultMinutes).coerceAtMost(23 * 60 + 59))
    var color by mutableStateOf<Int?>(null)
    var repeat by mutableStateOf(Repeat.NONE)
    var repeatUntil by mutableStateOf<LocalDate?>(null)
    var location by mutableStateOf("")
    var notes by mutableStateOf("")
    var reminderMinutes by mutableStateOf<Int?>(null) // null follows the default in settings
    var dirty by mutableStateOf(false)

    init {
        if (id != 0L) {
            viewModelScope.launch {
                repo.event(id)?.let { e ->
                    original = e
                    title = e.title
                    allDay = e.allDay
                    startDate = e.startsAt.toLocalDate()
                    startMinute = e.startsAt.minuteOfDay()
                    endDate = if (e.allDay) (e.endsAt - 1).toLocalDate() else e.endsAt.toLocalDate()
                    endMinute = e.endsAt.minuteOfDay()
                    color = e.color
                    repeat = e.repeatRule
                    repeatUntil = e.repeatUntil?.toLocalDate()
                    location = e.location.orEmpty()
                    notes = e.notes.orEmpty()
                    reminderMinutes = e.reminderMinutes
                }
                loaded = true
            }
        }
    }

    // the next whole hour today, or nine in the morning on any other day
    private fun defaultStart(day: Long): Int {
        val date = if (day > 0) LocalDate.ofEpochDay(day) else LocalDate.now()
        if (date != LocalDate.now()) return 9 * 60
        return ((LocalTime.now().hour + 1) * 60).coerceAtMost(22 * 60)
    }

    fun startsAt(): Long = if (allDay) startDate.startMillis() else startDate.atMinute(startMinute)

    fun endsAt(): Long = if (allDay) endDate.plusDays(1).startMillis() else endDate.atMinute(endMinute)

    fun valid(): Boolean = title.isNotBlank() && endsAt() > startsAt()

    fun moveStart(date: LocalDate, minute: Int) {
        // keep the same length when the start moves
        val length = endsAt() - startsAt()
        startDate = date
        startMinute = minute
        val end = startsAt() + length.coerceAtLeast(0)
        endDate = if (allDay) (end - 1).toLocalDate() else end.toLocalDate()
        endMinute = end.minuteOfDay()
        dirty = true
    }

    // far enough ahead that a few repeats still happen
    fun untilSuggestion(): LocalDate = when (repeat) {
        Repeat.YEARLY -> startDate.plusYears(5)
        Repeat.MONTHLY -> startDate.plusYears(1)
        else -> startDate.plusMonths(3)
    }

    suspend fun save() {
        val event = (original ?: Event(title = "", startsAt = 0, endsAt = 0)).copy(
            title = title.trim(),
            startsAt = startsAt(),
            endsAt = endsAt(),
            allDay = allDay,
            color = color,
            location = location.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
            repeatRule = repeat,
            repeatUntil = if (repeat == Repeat.NONE) null else repeatUntil?.startMillis(),
            reminderMinutes = reminderMinutes,
        )
        repo.saveEvent(event)
    }

    suspend fun delete() {
        if (id != 0L) repo.deleteEvent(id)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

private fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.pickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private enum class Picking { START_DATE, START_TIME, END_DATE, END_TIME, UNTIL, REPEAT, REMINDER }

private fun reminderChoiceLabel(minutes: Int?, default: Int): String =
    if (minutes == null) "Default (${reminderMinutesLabel(default).lowercase()})" else reminderMinutesLabel(minutes)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(id: Long, day: Long, navigator: AtlasNavigator) {
    val settings = LocalSettings.current
    val vm = atlasViewModel { EventEditorViewModel(id, day, settings.defaultEventMinutes, it.repository) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var picking by remember { mutableStateOf<Picking?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    fun leave() {
        if (vm.dirty) confirmLeave = true else navigator.back()
    }

    fun save() {
        if (!vm.valid()) {
            scope.launch { snackbar.showSnackbar(if (vm.title.isBlank()) "Give the event a name" else "The end needs to be after the start") }
            return
        }
        scope.launch {
            vm.save()
            navigator.back()
        }
    }

    BackHandler(enabled = vm.dirty) { confirmLeave = true }

    PageScaffold(
        title = if (id == 0L) "New event" else "Edit event",
        onBack = ::leave,
        actions = {
            if (id != 0L) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            }
            IconButton(onClick = ::save) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
        },
    ) { padding ->
        if (!vm.loaded) return@PageScaffold
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = {
                    vm.title = it
                    vm.dirty = true
                },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All day", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = vm.allDay, onCheckedChange = {
                    vm.allDay = it
                    vm.dirty = true
                })
            }
            HorizontalDivider()
            DateTimeRow(
                label = "Starts",
                date = vm.startDate,
                minute = vm.startMinute.takeUnless { vm.allDay },
                use24 = settings.use24Hour,
                onDate = { picking = Picking.START_DATE },
                onTime = { picking = Picking.START_TIME },
            )
            DateTimeRow(
                label = "Ends",
                date = vm.endDate,
                minute = vm.endMinute.takeUnless { vm.allDay },
                use24 = settings.use24Hour,
                onDate = { picking = Picking.END_DATE },
                onTime = { picking = Picking.END_TIME },
            )
            if (vm.endsAt() <= vm.startsAt()) {
                Text("The end is before the start", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Repeat") },
                supportingContent = { Text(vm.repeat.label()) },
                modifier = Modifier.clickable { picking = Picking.REPEAT },
            )
            if (vm.repeat != Repeat.NONE) {
                ListItem(
                    headlineContent = { Text("Until") },
                    supportingContent = { Text(vm.repeatUntil?.format(dateFormat) ?: "Forever") },
                    trailingContent = {
                        if (vm.repeatUntil != null) {
                            TextButton(onClick = {
                                vm.repeatUntil = null
                                vm.dirty = true
                            }) { Text("Clear") }
                        }
                    },
                    modifier = Modifier.clickable { picking = Picking.UNTIL },
                )
            }
            val remindersOff = !settings.remindersOn || !settings.remindEvents
            if (vm.allDay) {
                // all day events go off at the morning time, so there is only on or off
                ListItem(
                    headlineContent = { Text("Reminder") },
                    supportingContent = {
                        Column {
                            Text("All day events remind you at ${formatMinuteOfDay(settings.reminderMinute, settings.use24Hour)} on the day")
                            if (remindersOff) RemindersOffNote()
                        }
                    },
                    trailingContent = {
                        Switch(checked = vm.reminderMinutes != NO_REMINDER, onCheckedChange = {
                            vm.reminderMinutes = if (it) null else NO_REMINDER
                            vm.dirty = true
                        })
                    },
                )
            } else {
                ListItem(
                    headlineContent = { Text("Reminder") },
                    supportingContent = {
                        Column {
                            Text(reminderChoiceLabel(vm.reminderMinutes, settings.eventReminderMinutes))
                            if (remindersOff) RemindersOffNote()
                        }
                    },
                    modifier = Modifier.clickable { picking = Picking.REMINDER },
                )
            }
            HorizontalDivider()
            OutlinedTextField(
                value = vm.location,
                onValueChange = {
                    vm.location = it
                    vm.dirty = true
                },
                label = { Text("Location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.notes,
                onValueChange = {
                    vm.notes = it
                    vm.dirty = true
                },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            ColorRow(selected = vm.color, onSelect = {
                vm.color = it
                vm.dirty = true
            })
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save") }
        }
    }

    when (picking) {
        Picking.START_DATE -> DateDialog(vm.startDate, onDismiss = { picking = null }) {
            vm.moveStart(it, vm.startMinute)
            picking = null
        }
        Picking.END_DATE -> DateDialog(vm.endDate, onDismiss = { picking = null }) {
            vm.endDate = it
            vm.dirty = true
            picking = null
        }
        Picking.UNTIL -> DateDialog(vm.repeatUntil ?: vm.untilSuggestion(), onDismiss = { picking = null }) {
            vm.repeatUntil = it
            vm.dirty = true
            picking = null
        }
        Picking.START_TIME -> TimeDialog(vm.startMinute, settings.use24Hour, onDismiss = { picking = null }) {
            vm.moveStart(vm.startDate, it)
            picking = null
        }
        Picking.END_TIME -> TimeDialog(vm.endMinute, settings.use24Hour, onDismiss = { picking = null }) {
            vm.endMinute = it
            vm.dirty = true
            picking = null
        }
        Picking.REMINDER -> ReminderDialog(vm.reminderMinutes, settings.eventReminderMinutes, onDismiss = { picking = null }) {
            vm.reminderMinutes = it
            vm.dirty = true
            picking = null
        }
        Picking.REPEAT -> RepeatDialog(vm.repeat, onDismiss = { picking = null }) {
            vm.repeat = it
            vm.dirty = true
            picking = null
        }
        null -> Unit
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this event?",
            body = if (vm.repeat != Repeat.NONE) "This deletes every time it repeats." else "It is removed from your calendar.",
            button = "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            scope.launch {
                vm.delete()
                navigator.back()
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Discard changes?",
            body = "Your changes to this event are not saved.",
            button = "Discard",
            onDismiss = { confirmLeave = false },
        ) { navigator.back() }
    }
}

@Composable
private fun DateTimeRow(label: String, date: LocalDate, minute: Int?, use24: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onDate) { Text(date.format(dateFormat)) }
        if (minute != null) {
            TextButton(onClick = onTime) { Text(formatMinuteOfDay(minute, use24)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.pickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.pickerDate()) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, use24: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = use24)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RepeatDialog(current: Repeat, onDismiss: () -> Unit, onPick: (Repeat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column {
                Repeat.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == current, role = Role.RadioButton) { onPick(option) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(option.label(), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RemindersOffNote() {
    Text("Event reminders are off in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ReminderDialog(current: Int?, default: Int, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    val options = listOf<Int?>(null, NO_REMINDER) + ReminderMinuteOptions
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == current, role = Role.RadioButton) { onPick(option) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(reminderChoiceLabel(option, default), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
