package com.forsakenblank.atlas.ui.home

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.LongPress
import com.forsakenblank.atlas.data.NoteRow
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.money.HomeQuickMoney
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.SelectionBar
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.common.toggle
import com.forsakenblank.atlas.ui.countdowns.CountdownStrip
import com.forsakenblank.atlas.ui.sectionIcon
import com.forsakenblank.atlas.ui.tasks.TaskEditorDialog
import com.forsakenblank.atlas.ui.tasks.TaskRow
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.track.NewTrackerDialog
import com.forsakenblank.atlas.ui.track.TrackerButton
import com.forsakenblank.atlas.ui.track.TrackerSummary
import com.forsakenblank.atlas.ui.track.rememberTrackerTap
import com.forsakenblank.atlas.ui.track.todaySummaries
import com.forsakenblank.atlas.util.ClassSlot
import com.forsakenblank.atlas.util.classesOn
import com.forsakenblank.atlas.util.formatMinuteOfDay
import com.forsakenblank.atlas.util.formatTime
import com.forsakenblank.atlas.util.minuteOfDay
import com.forsakenblank.atlas.util.nowAndNext
import com.forsakenblank.atlas.util.occursOn
import com.forsakenblank.atlas.util.onDay
import com.forsakenblank.atlas.util.startMillis
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import com.forsakenblank.atlas.ui.journal.Mood

data class TodayPlan(
    val day: LocalDate = LocalDate.now(),
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val slots: List<TimetableSlot> = emptyList(),
    val terms: List<Term> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: AtlasRepository) : ViewModel() {

    val shortcuts = todaySummaries(repo)
        .map { list -> list.filter { it.tracker.showOnHome } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // today's journal entry, so the greeting can ask how the day is going
    val todayNote = repo.dayNotes()
        .map { notes -> notes.firstOrNull { it.day == LocalDate.now().toEpochDay() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMood(mood: Int) = viewModelScope.launch {
        val note = todayNote.value
        repo.saveJournal(LocalDate.now().toEpochDay(), note?.text.orEmpty(), if (note?.mood == mood) null else mood)
    }

    val pinnedNotes = repo.notes()
        .map { notes -> notes.filter { it.item.pinned }.take(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ticks so the now and next card moves on by itself
    val minute = flow {
        while (true) {
            emit(System.currentTimeMillis().minuteOfDay())
            delay(30_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis().minuteOfDay())

    private val day = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val timetable = combine(repo.subjects(), repo.slots(), repo.terms()) { s, sl, t -> Triple(s, sl, t) }

    val today = day.flatMapLatest { date ->
        val start = date.startMillis()
        val end = date.plusDays(1).startMillis()
        combine(repo.eventsBetween(start, end), repo.tasks(), timetable) { events, tasks, (subjects, slots, terms) ->
            TodayPlan(
                day = date,
                events = events.filter { it.occursOn(date) }.map { it.onDay(date) }.sortedWith(compareBy({ !it.allDay }, { it.startsAt })),
                // anything due today plus anything overdue
                tasks = tasks.filter { !it.done && it.due != null && it.due < end },
                subjects = subjects,
                slots = slots,
                terms = terms,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayPlan())

    fun setTaskDone(task: Task, done: Boolean) {
        viewModelScope.launch { repo.setTaskDone(task, done) }
    }

    suspend fun newNote(): Long = repo.createNote(parentId = null)

    val countdowns = repo.countdowns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeShortcuts(ids: Set<Long>, trash: Boolean) {
        viewModelScope.launch { if (trash) repo.moveToTrash(ids) else repo.setOnHome(ids, false) }
    }

    fun undoRemove(ids: Set<Long>, trash: Boolean) {
        viewModelScope.launch { if (trash) repo.restoreAll(ids) else repo.setOnHome(ids, true) }
    }
}

private enum class QuickAdd { TASK, TRACKER }

@Composable
fun HomeScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { HomeViewModel(it.repository) }
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    val pinned by vm.pinnedNotes.collectAsStateWithLifecycle()
    val plan by vm.today.collectAsStateWithLifecycle()
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val minute by vm.minute.collectAsStateWithLifecycle()
    val todayNote by vm.todayNote.collectAsStateWithLifecycle()
    val todayMood = todayNote?.mood
    val settings = LocalSettings.current
    val tap = rememberTrackerTap()
    val scope = rememberCoroutineScope()
    var addMenu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<QuickAdd?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val selecting = selected.isNotEmpty()

    // a shortcut can vanish while selected, for example when it is trashed from Track
    LaunchedEffect(shortcuts) {
        val ids = shortcuts.map { it.id }.toSet()
        if (!ids.containsAll(selected)) selected = selected intersect ids
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    val classes = remember(plan, settings.twoWeekTimetable, settings.weekAStart) {
        classesOn(plan.day, plan.slots, plan.subjects, plan.terms, settings)
    }
    val subjects = remember(plan.subjects) { plan.subjects.associateBy { it.id } }
    val extraSections = Section.entries.filter { it != Section.HOME && it !in settings.tabs && it !in settings.hiddenSections }
    val columns = if (settings.shortcutColumns > 0) GridCells.Fixed(settings.shortcutColumns) else GridCells.Adaptive(settings.shortcutSize.minWidth.dp)

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.showGreeting) {
                fullWidth { GreetingCard(settings.userName, shortcuts, plan, todayMood, vm::setMood) }
            }

            if (settings.showNowNext) {
                val upNext = nowNextLine(classes, plan.events, minute, settings.use24Hour)
                if (upNext != null) fullWidth { NowNextCard(upNext) }
            }

            if (settings.showCountdowns && countdowns.isNotEmpty()) {
                fullWidth {
                    CountdownStrip(
                        countdowns = countdowns,
                        onOpen = { navigator.openCountdown(it.id) },
                        onSeeAll = { navigator.openSection(Section.COUNTDOWNS, Section.COUNTDOWNS in settings.tabs) },
                    )
                }
            }

            if (Section.MONEY !in settings.hiddenSections) {
                fullWidth { HomeQuickMoney(navigator, isTab = Section.MONEY in settings.tabs) }
            }

            if (settings.showSectionsRow && extraSections.isNotEmpty()) {
                fullWidth {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(extraSections, key = { it.name }) { section ->
                            AssistChip(
                                onClick = { navigator.openSection(section, isTab = false) },
                                label = { Text(section.label) },
                                leadingIcon = { Icon(sectionIcon(section, selected = false), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }

            if (settings.showAgenda) {
                val tasks = if (settings.tasksOnHome) plan.tasks else emptyList()
                if (plan.events.isNotEmpty() || tasks.isNotEmpty()) {
                    fullWidth { SectionTitle("Today") }
                    items(plan.events, key = { "event-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { event ->
                        EventLine(event, settings.use24Hour) { navigator.openEvent(event.id, plan.day.toEpochDay()) }
                    }
                    items(tasks, key = { "task-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { task ->
                        TaskRow(
                            task = task,
                            subject = task.subjectId?.let { subjects[it] },
                            onToggle = { vm.setTaskDone(task, it) },
                            onClick = { editingTask = task },
                        )
                    }
                }
            }

            fullWidth { SectionTitle("Shortcuts") }
            if (shortcuts.isEmpty()) {
                fullWidth { NoShortcuts(navigator) }
            }
            items(shortcuts, key = { it.id }) { summary ->
                TrackerButton(
                    summary = summary,
                    onTap = { if (selecting) selected = selected.toggle(summary.id) else tap(summary) },
                    onLongPress = {
                        when {
                            selecting -> selected = selected.toggle(summary.id)
                            settings.shortcutLongPress == LongPress.SELECT -> selected = setOf(summary.id)
                            else -> navigator.openTracker(summary.id)
                        }
                    },
                    selecting = selecting,
                    selected = summary.id in selected,
                )
            }

            if (settings.showPinnedNotes && pinned.isNotEmpty()) {
                fullWidth { SectionTitle("Pinned notes") }
                items(pinned, key = { "note-${it.item.id}" }) { note ->
                    PinnedNote(note, settings.notePreviewLines) { navigator.openNote(note.item.id) }
                }
            }
        }

        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClear = { selected = emptySet() },
                onSelectAll = if (selected.size < shortcuts.size) ({ selected = shortcuts.map { it.id }.toSet() }) else null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) {
                if (selected.size == 1) {
                    IconButton(onClick = {
                        val id = selected.first()
                        selected = emptySet()
                        navigator.openTracker(id)
                    }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open tracker") }
                }
                IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove") }
            }
        } else if (settings.showQuickAdd) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                FloatingActionButton(onClick = { addMenu = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Note") },
                        leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                        onClick = {
                            addMenu = false
                            scope.launch { navigator.openNote(vm.newNote()) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Task") },
                        leadingIcon = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
                        onClick = {
                            addMenu = false
                            adding = QuickAdd.TASK
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Event") },
                        leadingIcon = { Icon(Icons.Outlined.Event, contentDescription = null) },
                        onClick = {
                            addMenu = false
                            navigator.openEvent(null, LocalDate.now().toEpochDay())
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Birthday") },
                        leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
                        onClick = {
                            addMenu = false
                            navigator.openCountdown(null, CountdownKind.BIRTHDAY)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Tracker") },
                        leadingIcon = { Icon(Icons.Outlined.Insights, contentDescription = null) },
                        onClick = {
                            addMenu = false
                            adding = QuickAdd.TRACKER
                        },
                    )
                }
            }
        }
    }

    when (adding) {
        QuickAdd.TASK -> TaskEditorDialog(task = null, initialDue = LocalDate.now().startMillis(), onDismiss = { adding = null })
        QuickAdd.TRACKER -> NewTrackerDialog(parentId = null, onDismiss = { adding = null }, onCreated = { adding = null })
        null -> Unit
    }
    editingTask?.let { task ->
        TaskEditorDialog(task = task, onDismiss = { editingTask = null })
    }
    if (confirmRemove) {
        RemoveShortcutsDialog(
            count = selected.size,
            trashDays = settings.trashDays,
            onDismiss = { confirmRemove = false },
        ) { trash ->
            val ids = selected
            confirmRemove = false
            selected = emptySet()
            vm.removeShortcuts(ids, trash)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val what = if (ids.size == 1) "1 shortcut" else "${ids.size} shortcuts"
                val message = if (trash) "Moved $what to the trash" else "Removed $what from Home"
                val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.undoRemove(ids, trash)
            }
        }
    }
}

@Composable
private fun RemoveShortcutsDialog(count: Int, trashDays: Int, onDismiss: () -> Unit, onConfirm: (trash: Boolean) -> Unit) {
    var trash by remember { mutableStateOf(false) }
    val plural = count != 1
    val kept = if (trashDays <= 0) "until you empty the trash" else "for $trashDays days"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plural) "Remove $count shortcuts?" else "Remove this shortcut?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (trash) {
                        "The tracker${if (plural) "s" else ""} and all ${if (plural) "their" else "its"} history go to the trash. You can restore them $kept."
                    } else {
                        "${if (plural) "They come" else "It comes"} off Home. The tracker${if (plural) "s" else ""} and history stay in Track."
                    },
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(value = trash, role = Role.Checkbox, onValueChange = { trash = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = trash, onCheckedChange = null)
                    Text("Also move the tracker${if (plural) "s" else ""} to the trash", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trash) },
                colors = ButtonDefaults.textButtonColors(contentColor = if (trash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            ) { Text(if (trash) "Move to trash" else "Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun GreetingCard(name: String, shortcuts: List<TrackerSummary>, plan: TodayPlan, mood: Int?, onMood: (Int) -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    } + if (name.isNotBlank()) ", ${name.trim()}" else ""
    val left = shortcuts.count { !it.goalMet }
    val parts = buildList {
        if (shortcuts.isNotEmpty()) add(if (left == 0) "every tracker done" else "$left tracker${if (left == 1) "" else "s"} to go")
        val tasks = plan.tasks.size
        if (tasks > 0) add("$tasks task${if (tasks == 1) "" else "s"} due")
        val events = plan.events.size
        if (events > 0) add("$events event${if (events == 1) "" else "s"}")
    }
    val summary = if (parts.isEmpty()) "A clear day so far." else parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    val onCard = MaterialTheme.colorScheme.onPrimaryContainer
    AtlasCard(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plan.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), style = MaterialTheme.typography.labelLarge, color = onCard)
                    Text(greeting, style = MaterialTheme.typography.headlineSmall, color = onCard)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = onCard)
                }
                // how much of today's trackers is done, at a glance
                if (shortcuts.isNotEmpty()) {
                    val done = shortcuts.count { it.goalMet }
                    Box(Modifier.padding(start = 12.dp).size(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { done / shortcuts.size.toFloat() },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = onCard.copy(alpha = 0.15f),
                            strokeWidth = 6.dp,
                        )
                        Text("$done/${shortcuts.size}", style = MaterialTheme.typography.labelLarge, color = onCard)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How's today?", style = MaterialTheme.typography.labelLarge, color = onCard, modifier = Modifier.weight(1f))
                Mood.entries.forEach { choice ->
                    val picked = mood == choice.value
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (picked) choice.color.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable { onMood(choice.value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(choice.face, fontSize = if (picked) 24.sp else 20.sp)
                    }
                }
            }
        }
    }
}

private data class UpNext(val nowTitle: String?, val nowDetail: String?, val nextTitle: String?, val nextDetail: String?, val school: Boolean)

// classes and timed events today, whichever is happening now and whichever comes next
private fun nowNextLine(classes: List<ClassSlot>, events: List<Event>, minute: Int, use24: Boolean): UpNext? {
    data class Slot(val title: String, val start: Int, val end: Int, val detail: String, val school: Boolean)

    val slots = classes.map {
        Slot(
            it.subject.name,
            it.slot.startMinute,
            it.slot.endMinute,
            listOfNotNull("${formatMinuteOfDay(it.slot.startMinute, use24)} to ${formatMinuteOfDay(it.slot.endMinute, use24)}", it.room).joinToString(", "),
            true,
        )
    } + events.filter { !it.allDay }.map {
        Slot(
            it.title,
            it.startsAt.minuteOfDay(),
            if (it.endsAt - it.startsAt >= 86_400_000L) 24 * 60 else it.endsAt.minuteOfDay().let { end -> if (end == 0) 24 * 60 else end },
            listOfNotNull("${formatTime(it.startsAt, use24)} to ${formatTime(it.endsAt, use24)}", it.location?.takeIf { l -> l.isNotBlank() }).joinToString(", "),
            false,
        )
    }
    if (slots.isEmpty()) return null
    val sorted = slots.sortedBy { it.start }
    val now = sorted.firstOrNull { minute in it.start until it.end }
    val next = sorted.firstOrNull { it.start > minute }
    if (now == null && next == null) return null
    return UpNext(now?.title, now?.detail, next?.title, next?.detail, (now ?: next)?.school == true)
}

@Composable
private fun NowNextCard(upNext: UpNext) {
    AtlasCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (upNext.school) Icons.Outlined.School else Icons.Outlined.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (upNext.nowTitle != null) {
                    Column {
                        Text("Now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(upNext.nowTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        upNext.nowDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (upNext.nextTitle != null) {
                    Column {
                        Text("Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(upNext.nextTitle, style = MaterialTheme.typography.titleSmall)
                        upNext.nextDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLine(event: Event, use24: Boolean, onClick: () -> Unit) {
    AtlasCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = event.color.toItemColor(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (event.allDay) "All day" else "${formatTime(event.startsAt, use24)} to ${formatTime(event.endsAt, use24)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoShortcuts(navigator: AtlasNavigator) {
    AtlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No shortcuts yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Trackers you make show up here for one tap logging. Make one in Track, or add a starter pack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { navigator.openSection(Section.TRACK, isTab = false) }) { Text("Open Track") }
                TextButton(onClick = navigator::openStarterPacks) { Text("Starter packs") }
            }
        }
    }
}

@Composable
private fun PinnedNote(note: NoteRow, previewLines: Int, onClick: () -> Unit) {
    AtlasCard(
        modifier = Modifier.fillMaxWidth(),
        color = note.item.color?.let { lerp(MaterialTheme.colorScheme.surfaceContainer, Color(it), 0.2f) },
        onClick = onClick,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(note.item.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (previewLines > 0) {
                Text(
                    note.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = previewLines.coerceAtLeast(1) + 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
