@file:OptIn(ExperimentalMaterial3Api::class)

package com.forsakenblank.atlas.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.rememberHaptic
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TermsViewModel(private val repo: AtlasRepository) : ViewModel() {

    val terms: StateFlow<List<Term>?> = repo.terms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(term: Term) {
        viewModelScope.launch { repo.saveTerm(term) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteTerm(id) }
    }
}

private val TermDateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

private fun termDates(startDay: Long, endDay: Long): String =
    "${LocalDate.ofEpochDay(startDay).format(TermDateFormat)} to ${LocalDate.ofEpochDay(endDay).format(TermDateFormat)}"

// material date pickers work in utc millis while terms keep epoch days
private fun dayToMillis(day: Long): Long =
    LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun millisToDay(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

@Composable
fun TermsScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { TermsViewModel(it.repository) }
    val terms by vm.terms.collectAsStateWithLifecycle()
    val buzz = rememberHaptic()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Term?>(null) }
    var deleting by remember { mutableStateOf<Term?>(null) }
    val today = LocalDate.now().toEpochDay()

    PageScaffold(
        title = "Terms",
        onBack = navigator::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add term") },
            )
        },
    ) { padding ->
        val list = terms
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TermsIntro() }
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "No terms yet",
                        body = "Your classes show every week of the year until you add one.",
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { term ->
                TermCard(term = term, isNow = today in term.startDay..term.endDay, onClick = { editing = term })
            }
        }
    }

    if (creating || editing != null) {
        TermDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { term ->
                vm.save(term)
                buzz()
                creating = false
                editing = null
            },
            onDelete = {
                deleting = editing
                editing = null
            },
        )
    }

    val doomed = deleting
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${doomed.name}?") },
            text = { Text("Only the dates are removed. Your classes stay as they are.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(doomed.id)
                        deleting = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TermsIntro() {
    AtlasCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "With no terms set, your classes show all year. Once you add terms, classes only show " +
                    "on days inside one, so the holidays stay clear.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TermCard(term: Term, isNow: Boolean, onClick: () -> Unit) {
    AtlasCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(term.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    termDates(term.startDay, term.endDay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isNow) {
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TermDialog(initial: Term?, onDismiss: () -> Unit, onSave: (Term) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var startDay by remember { mutableStateOf(initial?.startDay) }
    var endDay by remember { mutableStateOf(initial?.endDay) }
    var picking by remember { mutableStateOf(false) }
    val start = startDay
    val end = endDay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New term" else "Edit term") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Autumn term") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Dates", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (start != null && end != null) termDates(start, end) else "Pick the first and last day")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && start != null && end != null,
                onClick = {
                    if (start != null && end != null) {
                        onSave(
                            Term(
                                id = initial?.id ?: 0L,
                                name = name.trim(),
                                startDay = minOf(start, end),
                                endDay = maxOf(start, end),
                            )
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (picking) {
        TermDatesDialog(
            startDay = start,
            endDay = end,
            onDismiss = { picking = false },
            onPick = { first, last ->
                startDay = first
                endDay = last
                picking = false
            },
        )
    }
}

@Composable
private fun TermDatesDialog(startDay: Long?, endDay: Long?, onDismiss: () -> Unit, onPick: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startDay?.let { dayToMillis(it) },
        initialSelectedEndDateMillis = endDay?.let { dayToMillis(it) },
    )
    val first = state.selectedStartDateMillis
    val last = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = first != null && last != null,
                onClick = {
                    if (first != null && last != null) onPick(millisToDay(first), millisToDay(last))
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state)
    }
}
