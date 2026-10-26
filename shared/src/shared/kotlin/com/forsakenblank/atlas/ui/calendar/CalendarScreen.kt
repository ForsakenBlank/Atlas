package com.forsakenblank.atlas.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.CalendarView
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.tasks.TaskEditorDialog
import com.forsakenblank.atlas.ui.tasks.TaskRow
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.ClassSlot
import com.forsakenblank.atlas.util.classesOn
import com.forsakenblank.atlas.util.formatMinuteOfDay
import com.forsakenblank.atlas.util.formatTime
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.milestone
import com.forsakenblank.atlas.util.startMillis
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")

private fun AppSettings.firstDay(): DayOfWeek = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

private fun LocalDate.weekStart(first: DayOfWeek): LocalDate = with(TemporalAdjusters.previousOrSame(first))

private sealed interface CalendarDialog {
    data class EditTask(val task: Task?, val day: LocalDate) : CalendarDialog
    data class Details(val day: LocalDate) : CalendarDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { CalendarViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val today = LocalDate.now()
    var view by rememberSaveable { mutableStateOf(settings.calendarView) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var shownMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dialog by remember { mutableStateOf<CalendarDialog?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    val selected = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.parse(shownMonth)
    val first = settings.firstDay()
    val gridStart = month.atDay(1).weekStart(first)
    val weekStart = selected.weekStart(first)

    LaunchedEffect(gridStart, weekStart) {
        vm.ensure(minOf(gridStart, weekStart), maxOf(gridStart.plusDays(41), weekStart.plusDays(6)))
    }

    fun select(day: LocalDate) {
        selectedDay = day.toEpochDay()
        shownMonth = YearMonth.from(day).toString()
    }

    fun move(step: Long) {
        when (view) {
            CalendarView.MONTH -> {
                val next = month.plusMonths(step)
                shownMonth = next.toString()
                selectedDay = (if (YearMonth.from(today) == next) today else next.atDay(1)).toEpochDay()
            }
            CalendarView.WEEK -> select(selected.plusWeeks(step))
            CalendarView.AGENDA -> select(selected.plusDays(step * 7))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    CalendarView.entries.forEachIndexed { index, v ->
                        SegmentedButton(
                            selected = view == v,
                            onClick = { view = v },
                            shape = SegmentedButtonDefaults.itemShape(index, CalendarView.entries.size),
                        ) { Text(v.label) }
                    }
                }
            }
            item {
                val title = when (view) {
                    CalendarView.MONTH -> month.atDay(1).format(monthTitle)
                    CalendarView.WEEK -> "${weekStart.format(shortDay)} to ${weekStart.plusDays(6).format(shortDay)}"
                    CalendarView.AGENDA -> "From ${selected.format(shortDay)}"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { move(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back") }
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { move(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Forward") }
                }
                if (selected != today || YearMonth.from(today) != month) {
                    TextButton(onClick = { select(today) }) { Text("Back to today") }
                }
            }

            when (view) {
                CalendarView.MONTH -> {
                    item {
                        MonthGrid(
                            month = month,
                            gridStart = gridStart,
                            selected = selected,
                            today = today,
                            data = data,
                            settings = settings,
                            onSelect = ::select,
                        )
                    }
                    dayContents(selected, data, settings, navigator, vm, header = true) { dialog = it }
                }
                CalendarView.WEEK -> {
                    (0L..6L).forEach { offset ->
                        val day = weekStart.plusDays(offset)
                        dayContents(day, data, settings, navigator, vm, header = true, compact = true) { dialog = it }
                    }
                }
                CalendarView.AGENDA -> {
                    val days = (0L until 60L).map { selected.plusDays(it) }
                    val busy = days.filter { it == today || hasAnything(it, data, settings) }
                    if (busy.isEmpty()) {
                        item {
                            Text(
                                "Nothing planned for the next 60 days.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    busy.forEach { day ->
                        dayContents(day, data, settings, navigator, vm, header = true, compact = true) { dialog = it }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { addMenu = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add")
            }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Event") },
                    leadingIcon = { Icon(Icons.Outlined.Event, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        navigator.openEvent(null, selected.toEpochDay())
                    },
                )
                DropdownMenuItem(
                    text = { Text("Task") },
                    leadingIcon = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        dialog = CalendarDialog.EditTask(null, selected)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Birthday") },
                    leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
                    onClick = {
                        addMenu = false
                        navigator.openCountdown(null, CountdownKind.BIRTHDAY, day = selected.toEpochDay())
                    },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is CalendarDialog.EditTask -> TaskEditorDialog(
            task = d.task,
            initialDue = if (d.task == null) d.day.startMillis() else null,
            onDismiss = { dialog = null },
        )
        is CalendarDialog.Details -> DayDetailsSheet(
            day = d.day,
            data = data,
            settings = settings,
            navigator = navigator,
            vm = vm,
            onEditTask = { task -> dialog = CalendarDialog.EditTask(task, d.day) },
            onDismiss = { dialog = null },
        )
    }
}

private fun hasAnything(day: LocalDate, data: CalendarData, settings: AppSettings): Boolean =
    data.noteOn(day) != null || data.eventsOn(day).isNotEmpty() || data.countdownsOn(day).isNotEmpty() ||
        (settings.showTasksOnCalendar && (data.tasksOn(day).isNotEmpty() || data.repeatsOn(day).isNotEmpty()))

@Composable
private fun MonthGrid(
    month: YearMonth,
    gridStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    data: CalendarData,
    settings: AppSettings,
    onSelect: (LocalDate) -> Unit,
) {
    val weekFields = if (settings.weekStartsMonday) WeekFields.ISO else WeekFields.SUNDAY_START
    // only as many rows as the month needs
    val rows = ((month.atEndOfMonth().toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()
    Column(Modifier.padding(bottom = 8.dp)) {
        Row {
            if (settings.showWeekNumbers) Spacer(Modifier.width(28.dp))
            (0L..6L).forEach { i ->
                Text(
                    gridStart.plusDays(i).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(rows) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rowStart = gridStart.plusDays(row * 7L)
                if (settings.showWeekNumbers) {
                    Text(
                        "${rowStart.get(weekFields.weekOfWeekBasedYear())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.width(28.dp),
                    )
                }
                (0L..6L).forEach { i ->
                    val day = rowStart.plusDays(i)
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        selected = day == selected,
                        today = day == today,
                        data = data,
                        settings = settings,
                        onClick = { onSelect(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    data: CalendarData,
    settings: AppSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val events = data.eventsOn(day)
    val tasks = if (settings.showTasksOnCalendar) data.tasksOn(day).filter { !it.done } + data.repeatsOn(day) else emptyList()
    val logged = settings.showTrackerDots && data.calendarLogsOn(day).isNotEmpty()
    val noted = data.noteOn(day) != null
    val dots = data.countdownsOn(day).take(2).map { it.color.toItemColor(colors.tertiary) } +
        events.take(3).map { it.color.toItemColor(colors.primary) } +
        (if (tasks.isNotEmpty()) listOf(colors.secondary) else emptyList()) +
        (if (logged) listOf(colors.tertiary) else emptyList()) +
        (if (noted) listOf(colors.outline) else emptyList())

    Column(
        modifier = modifier
            .height(52.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .then(if (today) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> colors.onPrimaryContainer
                    inMonth -> colors.onSurface
                    else -> colors.outline
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
            dots.take(4).forEach { c ->
                Box(Modifier.size(5.dp).background(c, CircleShape))
            }
        }
    }
}

private fun LazyListScope.dayContents(
    day: LocalDate,
    data: CalendarData,
    settings: AppSettings,
    navigator: AtlasNavigator,
    vm: CalendarViewModel,
    header: Boolean,
    compact: Boolean = false,
    onDialog: (CalendarDialog) -> Unit,
) {
    val events = data.eventsOn(day)
    val countdowns = data.countdownsOn(day)
    val classes = if (settings.showClassesOnCalendar) classesOn(day, data.slots, data.subjects, data.terms, settings) else emptyList()
    val tasks = if (settings.showTasksOnCalendar) data.tasksOn(day) else emptyList()
    val repeats = if (settings.showTasksOnCalendar) data.repeatsOn(day) else emptyList()
    val logs = if (settings.showTrackerDots) data.calendarLogsOn(day) else emptyList()
    val note = data.noteOn(day)
    val empty = events.isEmpty() && countdowns.isEmpty() && classes.isEmpty() && tasks.isEmpty() && repeats.isEmpty() && logs.isEmpty()

    if (header) {
        item(key = "header-$day") {
            val today = day == LocalDate.now()
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (today) "Today, ${day.format(DateTimeFormatter.ofPattern("d MMMM"))}" else day.format(dayTitle),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onDialog(CalendarDialog.Details(day)) }) { Text("Full day") }
            }
        }
    }
    if (note != null) {
        item(key = "note-$day") {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    if (empty) {
        item(key = "empty-$day") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Nothing on",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!compact) {
                    TextButton(onClick = { navigator.openEvent(null, day.toEpochDay()) }) { Text("Add event") }
                    TextButton(onClick = { onDialog(CalendarDialog.EditTask(null, day)) }) { Text("Add task") }
                }
            }
        }
        return
    }
    items(countdowns, key = { "countdown-$day-${it.id}" }) { countdown ->
        CountdownRow(countdown, day) { navigator.openCountdown(countdown.id) }
    }
    items(events, key = { "event-$day-${it.id}" }) { event ->
        EventRow(event, settings) { navigator.openEvent(event.id, day.toEpochDay()) }
    }
    items(classes, key = { "class-$day-${it.slot.id}" }) { c ->
        ClassRow(c, settings) { navigator.openSection(Section.TIMETABLE, Section.TIMETABLE in settings.tabs) }
    }
    items(tasks, key = { "task-$day-${it.id}" }) { task ->
        TaskRow(
            task = task,
            subject = data.subject(task.subjectId),
            onToggle = { vm.setTaskDone(task, it) },
            onClick = { onDialog(CalendarDialog.EditTask(task, day)) },
        )
    }
    items(repeats, key = { "repeat-$day-${it.id}" }) { task ->
        RepeatPreviewRow(task) { onDialog(CalendarDialog.EditTask(task, day)) }
    }
    if (logs.isNotEmpty()) {
        item(key = "logs-$day") {
            val names = data.trackers.associateBy({ it.item.id }, { it.item.name })
            val summary = logs.groupBy { it.trackerId }.mapNotNull { (id, entries) ->
                names[id]?.let { if (entries.size > 1) "$it x${entries.size}" else it }
            }.joinToString(", ")
            if (summary.isNotEmpty()) {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Text(
                        "Logged $summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun EventRow(event: Event, settings: AppSettings, onClick: () -> Unit) {
    val accent = event.color.toItemColor(MaterialTheme.colorScheme.primary)
    AtlasCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), onClick = onClick) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = if (event.allDay) "All day" else "${formatTime(event.startsAt, settings.use24Hour)} to ${formatTime(event.endsAt, settings.use24Hour)}"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    if (event.repeatRule != Repeat.NONE) {
                        Icon(Icons.Outlined.Repeat, contentDescription = "Repeats", modifier = Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                event.location?.takeIf { it.isNotBlank() }?.let { place ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CountdownRow(countdown: Countdown, day: LocalDate, onClick: () -> Unit) {
    val accent = countdown.color.toItemColor(MaterialTheme.colorScheme.tertiary)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val emoji = countdown.emoji?.takeIf { it.isNotBlank() }
        if (emoji != null) {
            Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
        } else {
            Icon(Icons.Outlined.Cake, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(countdown.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // the milestone is worked out from the day being looked at, so next year's birthday says the right age
            val detail = countdown.milestone(day) ?: countdown.kind.name.lowercase().replaceFirstChar { it.uppercase() }
            Text(detail.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// a later date of a repeating task, it turns into a real task once the current one is ticked off
@Composable
internal fun RepeatPreviewRow(task: Task, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Repeating task, ${task.repeatRule.label().lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ClassRow(c: ClassSlot, settings: AppSettings, onClick: () -> Unit) {
    val accent = c.subject.color.toItemColor(MaterialTheme.colorScheme.secondary)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.School, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(c.subject.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val details = listOfNotNull(
                "${formatMinuteOfDay(c.slot.startMinute, settings.use24Hour)} to ${formatMinuteOfDay(c.slot.endMinute, settings.use24Hour)}",
                c.slot.kind,
                c.room,
            ).joinToString(", ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
