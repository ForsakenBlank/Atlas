package com.forsakenblank.atlas.ui.countdowns

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import com.forsakenblank.atlas.ui.common.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.milestone
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun CountdownKind.comesRoundYearly(): Boolean = this == CountdownKind.BIRTHDAY || this == CountdownKind.ANNIVERSARY

class CountdownEditorViewModel(
    private val id: Long,
    presetKind: CountdownKind,
    presetCountUp: Boolean,
    firstDay: Long,
    private val repo: AtlasRepository,
) : ViewModel() {

    var loaded by mutableStateOf(id == 0L)
        private set
    private var original: Countdown? = null

    var title by mutableStateOf("")
    var date by mutableStateOf(if (firstDay > 0) LocalDate.ofEpochDay(firstDay) else LocalDate.now())
    var kind by mutableStateOf(presetKind)
        private set
    var yearly by mutableStateOf(presetKind.comesRoundYearly() && !presetCountUp)
        private set
    var yearKnown by mutableStateOf(true)
    var countUp by mutableStateOf(presetCountUp)
        private set
    var color by mutableStateOf<Int?>(null)
    var emoji by mutableStateOf("")
    var note by mutableStateOf("")
    var pinned by mutableStateOf(false)
    var showOnCalendar by mutableStateOf(true)
    var remind by mutableStateOf(true)
    var dirty by mutableStateOf(false)

    init {
        if (id != 0L) {
            viewModelScope.launch {
                repo.countdown(id)?.let { c ->
                    original = c
                    title = c.title
                    date = LocalDate.ofEpochDay(c.date)
                    kind = c.kind
                    yearly = c.yearly
                    yearKnown = c.yearKnown
                    countUp = c.countUp
                    color = c.color
                    emoji = c.emoji.orEmpty()
                    note = c.note.orEmpty()
                    pinned = c.pinned
                    showOnCalendar = c.showOnCalendar
                    remind = c.remind
                }
                loaded = true
            }
        }
    }

    // the year only matters for how old someone turns or which anniversary it is
    val asksForYear: Boolean
        get() = yearly && kind.comesRoundYearly()

    fun pickKind(value: CountdownKind) {
        kind = value
        if (value.comesRoundYearly()) {
            yearly = true
            countUp = false
        }
        dirty = true
    }

    // a count up has no next time, so these two switch each other off
    fun changeYearly(value: Boolean) {
        yearly = value
        if (value) countUp = false
        dirty = true
    }

    fun changeCountUp(value: Boolean) {
        countUp = value
        if (value) yearly = false
        dirty = true
    }

    fun draft(): Countdown = (original ?: Countdown(title = "", date = 0, created = System.currentTimeMillis())).copy(
        title = title.trim(),
        date = date.toEpochDay(),
        kind = kind,
        yearly = yearly,
        yearKnown = yearKnown || !asksForYear,
        countUp = countUp,
        color = color,
        emoji = emoji.trim().ifEmpty { null },
        note = note.trim().ifEmpty { null },
        pinned = pinned,
        showOnCalendar = showOnCalendar,
        remind = remind,
    )

    suspend fun save() {
        repo.saveCountdown(draft())
    }

    suspend fun delete() {
        if (id != 0L) repo.deleteCountdown(id)
    }
}

private val fullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val dayAndMonth = DateTimeFormatter.ofPattern("d MMMM")

private fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.pickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

// keeps the first few characters, counting an emoji built from several code points as one
private fun String.takeCharacters(count: Int): String {
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(this)
    var end = 0
    repeat(count) {
        val next = breaks.next()
        if (next == BreakIterator.DONE) return this
        end = next
    }
    return substring(0, end)
}

private fun placeholderFor(kind: CountdownKind, countUp: Boolean): String = when {
    countUp -> "Moved into the new flat"
    kind == CountdownKind.BIRTHDAY -> "Mum's birthday"
    kind == CountdownKind.ANNIVERSARY -> "Our anniversary"
    kind == CountdownKind.HOLIDAY -> "Summer in Spain"
    kind == CountdownKind.EVENT -> "Gig night"
    else -> "Something big"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountdownEditorScreen(id: Long, kind: String, countUp: Boolean, day: Long, navigator: AtlasNavigator) {
    val preset = CountdownKind.entries.firstOrNull { it.name == kind } ?: CountdownKind.EVENT
    val vm = atlasViewModel { CountdownEditorViewModel(id, preset, countUp, day, it.repository) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    fun leave() {
        if (vm.dirty) confirmLeave = true else navigator.back()
    }

    fun save() {
        if (vm.title.isBlank()) {
            scope.launch { snackbar.showSnackbar("Give it a name first") }
            return
        }
        scope.launch {
            vm.save()
            navigator.back()
        }
    }

    BackHandler(enabled = vm.dirty) { confirmLeave = true }

    val pageTitle = when {
        id != 0L -> "Edit countdown"
        countUp -> "New days since"
        preset == CountdownKind.BIRTHDAY -> "New birthday"
        preset == CountdownKind.ANNIVERSARY -> "New anniversary"
        else -> "New countdown"
    }

    PageScaffold(
        title = pageTitle,
        onBack = ::leave,
        actions = {
            if (id != 0L) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            }
            IconButton(onClick = ::save) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
        },
    ) { padding ->
        if (!vm.loaded) return@PageScaffold
        val draft = vm.draft()
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
                placeholder = { Text(placeholderFor(vm.kind, vm.countUp)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountdownKind.entries.forEach { option ->
                    FilterChip(
                        selected = vm.kind == option,
                        onClick = { vm.pickKind(option) },
                        label = { Text(option.title()) },
                        leadingIcon = { Icon(option.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (vm.countUp) "Since" else "Date", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(draft.label(today), draft.milestone(today)).joinToString(", ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { pickingDate = true }) {
                    Text(vm.date.format(if (vm.asksForYear && !vm.yearKnown) dayAndMonth else fullDate))
                }
            }

            HorizontalDivider()
            SwitchRow(title = "Repeats every year", checked = vm.yearly, onChange = vm::changeYearly)
            if (vm.asksForYear) {
                SwitchRow(
                    title = "I know the year",
                    detail = if (vm.kind == CountdownKind.BIRTHDAY) "Shows how old they turn" else "Shows which anniversary it is",
                    checked = vm.yearKnown,
                    onChange = {
                        vm.yearKnown = it
                        vm.dirty = true
                    },
                )
            }
            SwitchRow(
                title = "Count up",
                detail = "Show the days since instead of the days to go",
                checked = vm.countUp,
                onChange = vm::changeCountUp,
            )

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CountdownBadge(draft, size = 56.dp)
                OutlinedTextField(
                    value = vm.emoji,
                    onValueChange = {
                        vm.emoji = it.takeCharacters(2)
                        vm.dirty = true
                    },
                    label = { Text("Emoji") },
                    supportingText = { Text("Shows instead of the icon") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            ColorRow(selected = vm.color, onSelect = {
                vm.color = it
                vm.dirty = true
            })
            OutlinedTextField(
                value = vm.note,
                onValueChange = {
                    vm.note = it
                    vm.dirty = true
                },
                label = { Text("Note") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SwitchRow(
                title = "Pin to Home",
                detail = "Pinned ones stay at the front on Home",
                checked = vm.pinned,
                onChange = {
                    vm.pinned = it
                    vm.dirty = true
                },
            )
            SwitchRow(
                title = "Show on calendar",
                checked = vm.showOnCalendar,
                onChange = {
                    vm.showOnCalendar = it
                    vm.dirty = true
                },
            )
            // a count up has no day coming to be reminded about
            if (!vm.countUp) {
                SwitchRow(
                    title = "Remind me",
                    detail = "A notification on the morning of the day",
                    checked = vm.remind,
                    onChange = {
                        vm.remind = it
                        vm.dirty = true
                    },
                )
            }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save") }
        }
    }

    if (pickingDate) {
        DateDialog(vm.date, onDismiss = { pickingDate = false }) {
            vm.date = it
            vm.dirty = true
            pickingDate = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this countdown?",
            body = "This cannot be undone.",
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
            body = "Your changes to this countdown are not saved.",
            button = "Discard",
            onDismiss = { confirmLeave = false },
        ) { navigator.back() }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
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
