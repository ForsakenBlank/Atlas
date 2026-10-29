package com.forsakenblank.atlas.ui.track

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.Aggregate
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatDay
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.formatTime
import com.forsakenblank.atlas.util.streaks
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class TrackerDetailViewModel(private val id: Long, private val repo: AtlasRepository) : ViewModel() {

    val tracker = repo.observeTracker(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val logs = repo.logs(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteLog(entry: LogEntry) {
        viewModelScope.launch { repo.deleteLog(entry.id) }
    }

    fun cancelTimer() {
        viewModelScope.launch { repo.cancelTimer(id) }
    }

    fun save(name: String, color: Int?, goal: Int?, showOnHome: Boolean, unit: String?, aggregate: Aggregate?, onCalendar: Boolean) {
        val current = tracker.value ?: return
        viewModelScope.launch {
            repo.rename(current.item.copy(color = color), name)
            repo.updateTracker(current.tracker.copy(dailyGoal = goal, showOnHome = showOnHome, unit = unit, aggregate = aggregate, showOnCalendar = onCalendar))
        }
    }

    fun logValue(value: Double, note: String?) {
        viewModelScope.launch { repo.logValue(id, value, note) }
    }

    fun logPast(day: LocalDate, count: Int, value: Double, seconds: Long?, note: String?) {
        viewModelScope.launch { repo.logPast(id, day, count, value, seconds, note) }
    }

    fun tap() {
        viewModelScope.launch { repo.tap(id) }
    }

    fun moveToTrash(done: () -> Unit) {
        val current = tracker.value ?: return
        viewModelScope.launch {
            repo.moveToTrash(current.item)
            done()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(id: Long, navigator: AtlasNavigator) {
    val vm = atlasViewModel { TrackerDetailViewModel(id, it.repository) }
    val tracker by vm.tracker.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    var loggingPast by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(StatRange.WEEK) }
    // timers show minutes first, a tap on the total goes on to hours, then days, then round again
    var timeUnit by rememberSaveable { mutableStateOf(TimeUnitView.MINUTES) }
    var confirmDelete by remember { mutableStateOf<LogEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tracker?.item?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                editing = true
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
        val t = tracker ?: return@Scaffold
        val accent = t.item.color.toItemColor(MaterialTheme.colorScheme.secondary)
        val byDay = remember(logs) { logs.groupBy { it.timestamp.toLocalDate() } }
        val streak = remember(byDay) { streaks(byDay.keys) }
        val today = LocalDate.now()
        val kind = t.tracker.kind
        val unitLabel = if (kind == TrackerKind.TIMER) "min" else t.tracker.unit.orEmpty()
        // totals make sense for counts and sums, averages for ratings and things like weight
        val averages = kind == TrackerKind.RATING || (kind == TrackerKind.NUMBER && t.tracker.aggregate != null && t.tracker.aggregate != Aggregate.SUM)
        val firstDay = byDay.keys.minOrNull() ?: today
        val start = range.days?.let { today.minusDays(it - 1L) } ?: minOf(firstDay, today)
        val inRange = byDay.filterKeys { it >= start }
        val rangeValues = inRange.mapNotNull { (_, dayLogs) -> dayValue(t.tracker, dayLogs) }
        val rangeLabel = (if (averages) "Average, ${range.label.lowercase()}" else "Total, ${range.label.lowercase()}") +
            if (kind == TrackerKind.TIMER) " (tap to change unit)" else ""
        val rangeStat = when {
            rangeValues.isEmpty() -> "None"
            kind == TrackerKind.TIMER -> timeUnit.format(rangeValues.sum())
            averages -> "${formatNumber(rangeValues.average())} $unitLabel".trim()
            else -> "${formatNumber(rangeValues.sum())} $unitLabel".trim()
        }
        val spanDays = ChronoUnit.DAYS.between(start, today).toInt() + 1
        // a long stretch is shown a month at a time so the bars stay readable
        val monthly = spanDays > 45
        val values: List<Float>
        val firstLabel: String
        if (monthly) {
            val months = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(today) }.toList()
            values = months.map { month ->
                val perDay = byDay.filterKeys { YearMonth.from(it) == month }.values.mapNotNull { dayValue(t.tracker, it) }
                (if (averages) perDay.average().takeIf { perDay.isNotEmpty() } ?: 0.0 else perDay.sum()).toFloat()
            }
            firstLabel = months.first().format(DateTimeFormatter.ofPattern("MMM yyyy"))
        } else {
            val shownDays = (spanDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
            values = shownDays.map { (dayValue(t.tracker, byDay[it].orEmpty()) ?: 0.0).toFloat() }
            firstLabel = shownDays.first().format(DateTimeFormatter.ofPattern("d MMM"))
        }
        val todaySummary = TrackerSummary(
            item = t.item,
            tracker = t.tracker,
            todayCount = byDay[today].orEmpty().size,
            todaySeconds = byDay[today].orEmpty().sumOf { it.durationSeconds ?: 0L },
            todayValue = dayValue(t.tracker, byDay[today].orEmpty()).takeIf { byDay[today].orEmpty().isNotEmpty() },
        )

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (t.tracker.runningSince != null) {
                item {
                    OutlinedButton(onClick = vm::cancelTimer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Text("  Discard the running timer")
                    }
                }
            }
            item {
                when (kind) {
                    TrackerKind.NUMBER, TrackerKind.RATING -> Button(onClick = { logging = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (kind == TrackerKind.RATING) "Rate today" else "Log an amount")
                    }
                    TrackerKind.COUNTER -> Button(onClick = vm::tap, modifier = Modifier.fillMaxWidth()) {
                        Text("Log one now (${todaySummary.todayLabel()})")
                    }
                    else -> Text(todaySummary.todayLabel(), style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                OutlinedButton(onClick = { loggingPast = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Log something in the past")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Current streak", "${streak.current} d", Modifier.weight(1f))
                    StatCard("Best streak", "${streak.best} d", Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRange.entries.forEach { r ->
                        FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        rangeLabel,
                        rangeStat,
                        Modifier.weight(1f),
                        onClick = if (kind == TrackerKind.TIMER) ({ timeUnit = timeUnit.next() }) else null,
                    )
                    StatCard("Days logged", "${inRange.size}", Modifier.weight(1f))
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (monthly) "${range.label}, by month" else range.label, style = MaterialTheme.typography.titleSmall)
                        BarChart(values, accent, goal = if (kind == TrackerKind.RATING) 5f else t.tracker.dailyGoal?.toFloat())
                        Row {
                            Text(firstLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text(if (monthly) "This month" else "Today", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "Nothing logged yet. Tap the tracker on Home or in Track to log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(logs.take(200), key = { it.id }) { entry ->
                val detail = listOfNotNull(
                    entry.durationSeconds?.let { formatDuration(it) },
                    when (kind) {
                        TrackerKind.NUMBER -> "${formatNumber(entry.value)} ${t.tracker.unit.orEmpty()}".trim()
                        TrackerKind.RATING -> "${entry.value.toInt()} of 5 stars"
                        else -> null
                    },
                    entry.note,
                ).joinToString(", ")
                ListItem(
                    headlineContent = { Text("${formatDay(entry.timestamp)}, ${formatTime(entry.timestamp, settings.use24Hour)}") },
                    supportingContent = if (detail.isNotEmpty()) {
                        { Text(detail) }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { if (settings.confirmLogDelete) confirmDelete = entry else vm.deleteLog(entry) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete entry")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    val t = tracker
    if (editing && t != null) {
        EditTrackerDialog(
            tracker = t,
            onDismiss = { editing = false },
            onSave = { name, color, goal, home, unit, aggregate, onCalendar ->
                vm.save(name, color, goal, home, unit, aggregate, onCalendar)
                editing = false
            },
        )
    }
    if (loggingPast && t != null) {
        PastLogDialog(
            kind = t.tracker.kind,
            name = t.item.name,
            unit = t.tracker.unit,
            onDismiss = { loggingPast = false },
            onSave = { day, count, value, seconds, note ->
                vm.logPast(day, count, value, seconds, note)
                loggingPast = false
            },
        )
    }
    if (logging && t != null) {
        val todayLogs = logs.filter { it.timestamp.toLocalDate() == LocalDate.now() }
        LogValueDialog(
            summary = TrackerSummary(t.item, t.tracker, todayLogs.size, 0L, dayValue(t.tracker, todayLogs).takeIf { todayLogs.isNotEmpty() }),
            onDismiss = { logging = false },
            onLog = { value, note ->
                vm.logValue(value, note)
                logging = false
            },
        )
    }
    confirmDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete this entry?",
            body = "${formatDay(entry.timestamp)}, ${formatTime(entry.timestamp, settings.use24Hour)}",
            button = "Delete",
            onDismiss = { confirmDelete = null },
        ) { vm.deleteLog(entry) }
    }
}

// how far back the numbers and the chart look, a null length means everything
private enum class StatRange(val label: String, val days: Int?) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
    ALL("All time", null),
}

// the unit a timer total is shown in
private enum class TimeUnitView {
    MINUTES, HOURS, DAYS;

    fun next() = entries[(ordinal + 1) % entries.size]

    // the total comes in as minutes
    fun format(minutes: Double): String = when (this) {
        MINUTES -> "${formatNumber(minutes)} min"
        HOURS -> "${formatNumber(minutes / 60.0)} h"
        DAYS -> "${formatNumber(minutes / 1440.0)} d"
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BarChart(values: List<Float>, color: Color, goal: Float?) {
    val track = color.copy(alpha = 0.15f)
    val goalColor = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val max = maxOf(values.maxOrNull() ?: 0f, goal ?: 0f, 1f)
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barWidth) / 2
            drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(6f, 6f))
            val h = size.height * (v / max)
            if (h > 0f) {
                drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(6f, 6f))
            }
        }
        if (goal != null && goal > 0f) {
            val y = size.height - size.height * (goal / max)
            drawLine(goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTrackerDialog(
    tracker: TrackerWithItem,
    onDismiss: () -> Unit,
    onSave: (String, Int?, Int?, Boolean, String?, Aggregate?, Boolean) -> Unit,
) {
    val kind = tracker.tracker.kind
    var name by remember { mutableStateOf(tracker.item.name) }
    var color by remember { mutableStateOf(tracker.item.color) }
    var goal by remember { mutableStateOf(tracker.tracker.dailyGoal?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(tracker.tracker.unit.orEmpty()) }
    var aggregate by remember { mutableStateOf(tracker.tracker.aggregate ?: Aggregate.SUM) }
    var showOnHome by remember { mutableStateOf(tracker.tracker.showOnHome) }
    var showOnCalendar by remember { mutableStateOf(tracker.tracker.showOnCalendar) }
    val hasGoal = kind == TrackerKind.COUNTER || kind == TrackerKind.TIMER || kind == TrackerKind.NUMBER
    val hasUnit = kind == TrackerKind.COUNTER || kind == TrackerKind.NUMBER

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tracker") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                if (hasGoal) {
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { v -> goal = v.filter(Char::isDigit).take(6) },
                        label = { Text(if (kind == TrackerKind.TIMER) "Daily goal in minutes" else "Daily goal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                if (hasUnit) {
                    OutlinedTextField(value = unit, onValueChange = { unit = it.take(16) }, label = { Text("Unit") }, singleLine = true)
                }
                if (kind == TrackerKind.NUMBER) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Aggregate.entries.forEach { a ->
                            FilterChip(selected = aggregate == a, onClick = { aggregate = a }, label = { Text(a.label) })
                        }
                    }
                }
                ColorRow(selected = color, onSelect = { color = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show on Home", modifier = Modifier.weight(1f))
                    Switch(checked = showOnHome, onCheckedChange = { showOnHome = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show on the calendar")
                        Text(
                            "Off keeps it off the day cells, the full day view still lists it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showOnCalendar, onCheckedChange = { showOnCalendar = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name.trim(),
                        color,
                        goal.toIntOrNull()?.takeIf { it > 0 && hasGoal },
                        showOnHome,
                        unit.trim().takeIf { it.isNotEmpty() && hasUnit },
                        if (kind == TrackerKind.NUMBER) aggregate else null,
                        showOnCalendar,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
