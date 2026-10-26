package com.forsakenblank.atlas.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.money.Earned
import com.forsakenblank.atlas.ui.money.Spent
import com.forsakenblank.atlas.ui.tasks.TaskRow
import com.forsakenblank.atlas.ui.track.dayValue
import com.forsakenblank.atlas.ui.track.formatNumber
import com.forsakenblank.atlas.util.classesOn
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.pounds
import com.forsakenblank.atlas.util.signedPounds
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val fullDay = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

// what a tracker did on one day in a few words, goals included
private fun trackerLine(t: TrackerWithItem, logs: List<LogEntry>): String {
    val unit = t.tracker.unit?.let { " $it" }.orEmpty()
    val goal = t.tracker.dailyGoal
    return when (t.tracker.kind) {
        TrackerKind.COUNTER -> "${logs.size}$unit" + (goal?.let { " of $it" } ?: "")
        TrackerKind.YES_NO -> "Done"
        TrackerKind.TIMER -> {
            val seconds = logs.sumOf { it.durationSeconds ?: 0L }
            formatDuration(seconds) + (goal?.let { " of $it min" } ?: "")
        }
        TrackerKind.NUMBER -> {
            val value = dayValue(t.tracker, logs) ?: 0.0
            formatNumber(value) + unit + (goal?.let { " of $it" } ?: "") + if (logs.size > 1) " from ${logs.size} entries" else ""
        }
        TrackerKind.RATING -> "${formatNumber(dayValue(t.tracker, logs) ?: 0.0)} of 5 stars"
    }
}

// everything that happened on a day in one place, including trackers that are kept off the calendar cells
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsSheet(
    day: LocalDate,
    data: CalendarData,
    settings: AppSettings,
    navigator: AtlasNavigator,
    vm: CalendarViewModel,
    onEditTask: (com.forsakenblank.atlas.data.Task) -> Unit,
    onDismiss: () -> Unit,
) {
    val saved = data.noteOn(day).orEmpty()
    var note by remember(day) { mutableStateOf(saved) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun finish() {
        if (note.trim() != saved) vm.saveNote(day, note)
        onDismiss()
    }

    val events = data.eventsOn(day)
    val countdowns = data.countdownsOn(day)
    val classes = classesOn(day, data.slots, data.subjects, data.terms, settings)
    val tasks = data.tasksOn(day)
    val repeats = data.repeatsOn(day)
    val logsByTracker = data.logsOn(day).groupBy { it.trackerId }
    val trackers = data.trackers.filter { logsByTracker.containsKey(it.item.id) }
    val unlogged = data.trackers.size - trackers.size
    val money = data.moneyOn(day)
    val accountNames = data.accounts.associate { it.id to it.name }
    val real = money.filter { it.transferId == null }
    val earned = real.filter { it.amountPence > 0 }.sumOf { it.amountPence }
    val spent = -real.filter { it.amountPence < 0 }.sumOf { it.amountPence }

    ModalBottomSheet(onDismissRequest = ::finish, sheetState = sheet) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(day.format(fullDay), style = MaterialTheme.typography.titleLarge)
                Text(
                    "Everything from this day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(2000) },
                    label = { Text("Note for this day") },
                    leadingIcon = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Saved when you close this",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }

            if (events.isNotEmpty() || countdowns.isNotEmpty() || classes.isNotEmpty()) {
                item { SectionTitle("Schedule", Modifier.padding(top = 12.dp)) }
                items(countdowns, key = { "c-${it.id}" }) { c -> CountdownRow(c, day) { navigator.openCountdown(c.id) } }
                items(events, key = { "e-${it.id}" }) { e -> EventRow(e, settings) { navigator.openEvent(e.id, day.toEpochDay()) } }
                items(classes, key = { "k-${it.slot.id}" }) { c ->
                    ClassRow(c, settings) { navigator.openSection(Section.TIMETABLE, Section.TIMETABLE in settings.tabs) }
                }
            }

            if (tasks.isNotEmpty() || repeats.isNotEmpty()) {
                item { SectionTitle("Tasks", Modifier.padding(top = 12.dp)) }
                items(tasks, key = { "t-${it.id}" }) { task ->
                    TaskRow(task = task, subject = data.subject(task.subjectId), onToggle = { vm.setTaskDone(task, it) }, onClick = { onEditTask(task) })
                }
                items(repeats, key = { "r-${it.id}" }) { task -> RepeatPreviewRow(task) { onEditTask(task) } }
            }

            item { SectionTitle("Trackers", Modifier.padding(top = 12.dp)) }
            if (trackers.isEmpty()) {
                item {
                    Text("Nothing tracked this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(trackers, key = { "tr-${it.item.id}" }) { t ->
                val logs = logsByTracker[t.item.id].orEmpty()
                val notes = logs.mapNotNull { it.note?.takeIf { n -> n.isNotBlank() } }
                Row(
                    Modifier.fillMaxWidth().clickable { navigator.openTracker(t.item.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Insights, contentDescription = null, tint = t.item.color.toItemColor(MaterialTheme.colorScheme.tertiary), modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.item.name, style = MaterialTheme.typography.bodyLarge)
                        if (notes.isNotEmpty()) {
                            Text(notes.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!t.tracker.showOnCalendar) {
                            Text("Hidden from the calendar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Text(trackerLine(t, logs), fontWeight = FontWeight.Medium)
                }
                HorizontalDivider()
            }
            if (unlogged > 0 && trackers.isNotEmpty()) {
                item {
                    Text(
                        "$unlogged other tracker${if (unlogged == 1) "" else "s"} not logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (money.isNotEmpty()) {
                item { SectionTitle("Money", Modifier.padding(top = 12.dp)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("Earned ${pounds(earned)}", color = Earned, fontWeight = FontWeight.Medium)
                        Text("Spent ${pounds(spent)}", color = Spent, fontWeight = FontWeight.Medium)
                    }
                }
                items(money, key = { "m-${it.id}" }) { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(m.category?.takeIf { it.isNotBlank() } ?: "Uncategorised")
                            val sub = listOfNotNull(accountNames[m.accountId], m.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            signedPounds(m.amountPence),
                            color = when {
                                m.transferId != null -> MaterialTheme.colorScheme.onSurfaceVariant
                                m.amountPence > 0 -> Earned
                                else -> Spent
                            },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            item {
                TextButton(onClick = ::finish, modifier = Modifier.padding(top = 8.dp)) { Text("Done") }
            }
        }
    }
}
