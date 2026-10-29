@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.forsakenblank.atlas.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.theme.ItemColors
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.theme.onColor
import com.forsakenblank.atlas.util.ClassSlot
import com.forsakenblank.atlas.util.classesOn
import com.forsakenblank.atlas.util.formatMinuteOfDay
import com.forsakenblank.atlas.util.inTerm
import com.forsakenblank.atlas.util.nowAndNext
import com.forsakenblank.atlas.util.weekLabel
import com.forsakenblank.atlas.util.weekOf
import com.forsakenblank.atlas.util.withWeek
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class TimetableData(
    val slots: List<TimetableSlot>,
    val subjects: List<Subject>,
    val terms: List<Term>,
)

class TimetableViewModel(private val repo: AtlasRepository) : ViewModel() {

    val data: StateFlow<TimetableData?> = combine(repo.slots(), repo.subjects(), repo.terms()) { slots, subjects, terms ->
        TimetableData(slots, subjects, terms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveSlot(slot: TimetableSlot) {
        viewModelScope.launch { repo.saveSlot(slot) }
    }

    fun deleteSlot(id: Long) {
        viewModelScope.launch { repo.deleteSlot(id) }
    }

    // the slot editor picks the new subject straight away, so it needs the id back
    suspend fun addSubject(name: String, color: Int?, makeFolder: Boolean): Long? {
        repo.saveSubject(Subject(name = name, color = color), makeFolder)
        return repo.subjects().first().filter { it.name == name }.maxByOrNull { it.id }?.id
    }
}

private data class SlotDraft(val slot: TimetableSlot?, val day: Int, val startMinute: Int)

private data class PlacedSlot(val item: ClassSlot, val lane: Int, val lanes: Int)

private enum class TimeField { START, END }

private val DayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val ClassKinds = listOf("Lesson", "Lecture", "Lab", "Seminar", "Tutorial")
private val HourHeight = 56.dp
private val TimeColumnWidth = 48.dp
private const val LastMinute = 23 * 60 + 59

private fun timeRange(slot: TimetableSlot, use24: Boolean): String =
    "${formatMinuteOfDay(slot.startMinute, use24)} to ${formatMinuteOfDay(slot.endMinute, use24)}"

private fun hourLabel(hour: Int, use24: Boolean): String = when {
    use24 -> formatMinuteOfDay(hour * 60, true)
    hour == 0 -> "12am"
    hour == 12 -> "12pm"
    hour < 12 -> "${hour}am"
    else -> "${hour - 12}pm"
}

// classes that overlap sit side by side instead of on top of each other
private fun placeSlots(classes: List<ClassSlot>): List<PlacedSlot> {
    val placed = mutableListOf<PlacedSlot>()
    val group = mutableListOf<Pair<ClassSlot, Int>>()
    val laneEnds = mutableListOf<Int>()
    var groupEnd = 0
    fun closeGroup() {
        group.forEach { (item, lane) -> placed += PlacedSlot(item, lane, laneEnds.size) }
        group.clear()
        laneEnds.clear()
    }
    for (item in classes.sortedBy { it.slot.startMinute }) {
        if (item.slot.startMinute >= groupEnd) closeGroup()
        var lane = laneEnds.indexOfFirst { it <= item.slot.startMinute }
        if (lane == -1) {
            laneEnds += item.slot.endMinute
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = item.slot.endMinute
        }
        group += item to lane
        groupEnd = maxOf(groupEnd, item.slot.endMinute)
    }
    closeGroup()
    return placed
}

@Composable
fun TimetableScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { TimetableViewModel(it.repository) }
    val data by vm.data.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val app = atlasApp()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val buzz = rememberHaptic()

    // ticks over so now and next stays right while the screen is open
    var clock by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clock = LocalDateTime.now()
        }
    }
    val today = clock.toLocalDate()
    val nowMinute = clock.hour * 60 + clock.minute
    val currentWeek = settings.weekOf(today)
    var viewWeek by remember(currentWeek) { mutableIntStateOf(currentWeek) }
    var draft by remember { mutableStateOf<SlotDraft?>(null) }

    val loaded = data ?: return

    val days = if (settings.timetableWeekends) (1..7).toList() else (1..5).toList()
    val subjectsById = loaded.subjects.associateBy { it.id }
    val shown = loaded.slots
        .filter { slot ->
            slot.dayOfWeek in days && (!settings.twoWeekTimetable || slot.week == 0 || slot.week == viewWeek)
        }
        .mapNotNull { slot -> subjectsById[slot.subjectId]?.let { ClassSlot(slot, it) } }
    // the grid grows to fit any class outside the usual hours
    val startHour = minOf(settings.timetableStartHour, shown.minOfOrNull { it.slot.startMinute / 60 } ?: 23).coerceIn(0, 23)
    val endHour = maxOf(settings.timetableEndHour, shown.maxOfOrNull { (it.slot.endMinute + 59) / 60 } ?: 0)
        .coerceIn(startHour + 1, 24)
    val todayNumber = today.dayOfWeek.value
    val showToday = (!settings.twoWeekTimetable || viewWeek == currentWeek) && todayNumber in days
    val defaultDay = if (todayNumber in days) todayNumber else 1

    fun addAt(day: Int, minute: Int) {
        draft = SlotDraft(null, day, minute.coerceIn(0, LastMinute - 1))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loaded.slots.isEmpty()) {
                EmptyTimetable(
                    onAdd = { addAt(defaultDay, settings.timetableStartHour * 60) },
                    onSubjects = { navigator.openSubjects() },
                )
            } else {
                NowNextCard(
                    todaysClasses = classesOn(today, loaded.slots, loaded.subjects, loaded.terms, settings),
                    minute = nowMinute,
                    outOfTerm = !inTerm(today, loaded.terms),
                    use24 = settings.use24Hour,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { navigator.openSubjects() }) {
                        Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Subjects")
                    }
                    TextButton(onClick = { navigator.openTerms() }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Terms")
                    }
                }
                if (settings.twoWeekTimetable) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(1, 2).forEachIndexed { index, week ->
                            SegmentedButton(
                                selected = viewWeek == week,
                                onClick = { viewWeek = week },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(weekLabel(week)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "This week is ${weekLabel(currentWeek)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val other = if (currentWeek == 1) 2 else 1
                                app.updateSettings { it.withWeek(LocalDate.now(), other) }
                            },
                        ) { Text("Swap") }
                    }
                }
                TimetableGrid(
                    days = days,
                    classes = shown,
                    startHour = startHour,
                    endHour = endHour,
                    highlightDay = if (showToday) todayNumber else 0,
                    nowMinute = nowMinute,
                    use24 = settings.use24Hour,
                    onTapEmpty = { day, hour -> addAt(day, hour * 60) },
                    onTapClass = { slot -> draft = SlotDraft(slot, slot.dayOfWeek, slot.startMinute) },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { addAt(defaultDay, settings.timetableStartHour * 60) },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Add class") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    val current = draft
    if (current != null) {
        SlotEditor(
            initial = current.slot,
            day = current.day,
            startMinute = current.startMinute,
            subjects = loaded.subjects,
            onDismiss = { draft = null },
            onSave = { slot ->
                vm.saveSlot(slot)
                buzz()
                draft = null
            },
            onDelete = { slot ->
                vm.deleteSlot(slot.id)
                draft = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar("Class deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) vm.saveSlot(slot)
                }
            },
            onNewSubject = { name, color -> vm.addSubject(name, color, settings.makeSubjectFolders) },
        )
    }
}

@Composable
private fun EmptyTimetable(onAdd: () -> Unit, onSubjects: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Outlined.School,
            title = "No classes yet",
            body = "Add your classes once and the timetable lays out your week, shows what is on now and next, " +
                "and gives each subject its own colour.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text("Add a class") }
            OutlinedButton(onClick = onSubjects) { Text("Manage subjects") }
        }
    }
}

@Composable
private fun NowNextCard(todaysClasses: List<ClassSlot>, minute: Int, outOfTerm: Boolean, use24: Boolean) {
    val nowNext = nowAndNext(todaysClasses, minute)
    val nowClass = nowNext.now
    val nextClass = nowNext.next
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Now and next", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (nowClass == null && nextClass == null) {
                Text(
                    when {
                        outOfTerm -> "Outside term time, so no classes today"
                        todaysClasses.isEmpty() -> "No classes today"
                        else -> "No more classes today"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (nowClass != null) ClassLine("Now", nowClass, use24)
            if (nextClass != null) ClassLine("Next", nextClass, use24)
        }
    }
}

@Composable
private fun ClassLine(label: String, item: ClassSlot, use24: Boolean) {
    val details = listOfNotNull(timeRange(item.slot, use24), item.room?.takeIf { it.isNotBlank() }).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(item.subject.color.toItemColor(MaterialTheme.colorScheme.primary)),
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.subject.name, style = MaterialTheme.typography.titleMedium)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimetableGrid(
    days: List<Int>,
    classes: List<ClassSlot>,
    startHour: Int,
    endHour: Int,
    highlightDay: Int,
    nowMinute: Int,
    use24: Boolean,
    onTapEmpty: (day: Int, hour: Int) -> Unit,
    onTapClass: (TimetableSlot) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val hours = endHour - startHour
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.width(TimeColumnWidth))
            days.forEach { day ->
                val highlighted = day == highlightDay
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        DayNames[day - 1],
                        style = MaterialTheme.typography.labelLarge,
                        color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (highlighted) {
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        } else {
                            Modifier.padding(vertical = 4.dp)
                        },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(HourHeight * hours),
        ) {
            Box(
                Modifier
                    .width(TimeColumnWidth)
                    .fillMaxHeight(),
            ) {
                for (hour in startHour until endHour) {
                    Text(
                        hourLabel(hour, use24),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier
                            .offset(y = HourHeight * (hour - startHour))
                            .fillMaxWidth()
                            .padding(top = 2.dp, end = 6.dp),
                    )
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val hourPx = HourHeight.toPx()
                        for (i in 0..hours) {
                            val y = (i * hourPx).coerceAtMost(size.height - 1f)
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                        val columnWidth = size.width / days.size
                        for (i in 1 until days.size) {
                            val x = i * columnWidth
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        }
                    },
            ) {
                days.forEach { day ->
                    DayColumn(
                        classes = classes.filter { it.slot.dayOfWeek == day },
                        startHour = startHour,
                        endHour = endHour,
                        isToday = day == highlightDay,
                        nowMinute = nowMinute,
                        use24 = use24,
                        onTapEmpty = { hour -> onTapEmpty(day, hour) },
                        onTapClass = onTapClass,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    classes: List<ClassSlot>,
    startHour: Int,
    endHour: Int,
    isToday: Boolean,
    nowMinute: Int,
    use24: Boolean,
    onTapEmpty: (hour: Int) -> Unit,
    onTapClass: (TimetableSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapEmpty by rememberUpdatedState(onTapEmpty)
    val gridStart = startHour * 60
    BoxWithConstraints(
        modifier
            .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent)
            .pointerInput(startHour) {
                // taps on a class are taken by the class itself, so this only sees empty space
                detectTapGestures { offset ->
                    val hour = startHour + (offset.y / HourHeight.toPx()).toInt()
                    tapEmpty(hour.coerceIn(0, 23))
                }
            },
    ) {
        placeSlots(classes).forEach { placed ->
            val slot = placed.item.slot
            val laneWidth = maxWidth / placed.lanes
            val height = HourHeight * ((slot.endMinute - slot.startMinute) / 60f)
            ClassBlock(
                item = placed.item,
                use24 = use24,
                onClick = { onTapClass(slot) },
                modifier = Modifier
                    .offset(x = laneWidth * placed.lane, y = HourHeight * ((slot.startMinute - gridStart) / 60f))
                    .width(laneWidth)
                    .height(maxOf(height, 20.dp))
                    .padding(1.dp),
            )
        }
        if (isToday && nowMinute >= gridStart && nowMinute <= endHour * 60) {
            Box(
                Modifier
                    .offset(y = HourHeight * ((nowMinute - gridStart) / 60f))
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}

@Composable
private fun ClassBlock(item: ClassSlot, use24: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = item.subject.color.toItemColor(MaterialTheme.colorScheme.primary)
    val content = background.onColor()
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Text(
            item.subject.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            timeRange(item.slot, use24),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val room = item.room
        if (!room.isNullOrBlank()) {
            Text(
                room,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SlotEditor(
    initial: TimetableSlot?,
    day: Int,
    startMinute: Int,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSave: (TimetableSlot) -> Unit,
    onDelete: (TimetableSlot) -> Unit,
    onNewSubject: suspend (String, Int?) -> Long?,
) {
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    var subjectId by remember { mutableStateOf(initial?.subjectId) }
    var dayOfWeek by remember { mutableIntStateOf(initial?.dayOfWeek ?: day) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: startMinute) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: minOf(startMinute + 60, LastMinute)) }
    var week by remember { mutableIntStateOf(initial?.week ?: 0) }
    var kind by remember { mutableStateOf(initial?.kind) }
    var room by remember { mutableStateOf(initial?.room.orEmpty()) }
    var picking by remember { mutableStateOf<TimeField?>(null) }
    var addingSubject by remember { mutableStateOf(subjects.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(ItemColors.firstOrNull { c -> subjects.none { it.color == c } }) }
    var savingSubject by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val subjectRoom = subjects.firstOrNull { it.id == subjectId }?.room?.takeIf { it.isNotBlank() }
    val timesOk = end > start

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add class" else "Edit class") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Subject", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subjects.forEach { subject ->
                        FilterChip(
                            selected = subjectId == subject.id,
                            onClick = { subjectId = subject.id },
                            label = { Text(subject.name) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(subject.color.toItemColor(primary)),
                                )
                            },
                        )
                    }
                    FilterChip(
                        selected = addingSubject,
                        onClick = { addingSubject = !addingSubject },
                        label = { Text("New subject") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                if (addingSubject) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Subject name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ColorRow(selected = newColor, onSelect = { newColor = it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (subjects.isNotEmpty()) {
                            TextButton(onClick = { addingSubject = false }) { Text("Cancel") }
                        }
                        TextButton(
                            enabled = newName.isNotBlank() && !savingSubject,
                            onClick = {
                                savingSubject = true
                                scope.launch {
                                    val id = onNewSubject(newName.trim(), newColor)
                                    if (id != null) subjectId = id
                                    newName = ""
                                    addingSubject = false
                                    savingSubject = false
                                }
                            },
                        ) { Text("Add subject") }
                    }
                }

                Text("Day", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val choices = if (settings.timetableWeekends || dayOfWeek > 5) 1..7 else 1..5
                    for (d in choices) {
                        FilterChip(
                            selected = dayOfWeek == d,
                            onClick = { dayOfWeek = d },
                            label = { Text(DayNames[d - 1]) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeButton(
                        label = "Starts",
                        minute = start,
                        use24 = settings.use24Hour,
                        onClick = { picking = TimeField.START },
                        modifier = Modifier.weight(1f),
                    )
                    TimeButton(
                        label = "Ends",
                        minute = end,
                        use24 = settings.use24Hour,
                        onClick = { picking = TimeField.END },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!timesOk) {
                    Text(
                        "The class has to end after it starts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (settings.twoWeekTimetable) {
                    Text("Week", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(0, 1, 2).forEach { w ->
                            FilterChip(
                                selected = week == w,
                                onClick = { week = w },
                                label = { Text(weekLabel(w)) },
                            )
                        }
                    }
                }

                Text("Type (optional)", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ClassKinds.forEach { k ->
                        val selected = kind.equals(k, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { kind = if (selected) null else k },
                            label = { Text(k) },
                        )
                    }
                }

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room (optional)") },
                    supportingText = {
                        Text(if (subjectRoom == null) "Only needed if it differs from the subject" else "Leave empty to use $subjectRoom")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = subjectId != null && timesOk,
                onClick = {
                    val id = subjectId
                    if (id != null) {
                        onSave(
                            TimetableSlot(
                                id = initial?.id ?: 0L,
                                subjectId = id,
                                dayOfWeek = dayOfWeek,
                                startMinute = start,
                                endMinute = end,
                                // a one week timetable leaves any old A or B choice alone
                                week = if (settings.twoWeekTimetable) week else (initial?.week ?: 0),
                                kind = kind,
                                room = room.trim().takeIf { it.isNotEmpty() },
                            )
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    val which = picking
    if (which != null) {
        TimePickDialog(
            title = if (which == TimeField.START) "Starts at" else "Ends at",
            minute = if (which == TimeField.START) start else end,
            use24 = settings.use24Hour,
            onDismiss = { picking = null },
            onPick = { picked ->
                if (which == TimeField.START) {
                    // moving the start keeps the class the same length
                    val length = end - start
                    start = picked
                    if (length > 0) end = minOf(picked + length, LastMinute)
                } else {
                    end = picked
                }
                picking = null
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, use24: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(formatMinuteOfDay(minute, use24), maxLines = 1)
        }
    }
}

@Composable
private fun TimePickDialog(title: String, minute: Int, use24: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (minute / 60).coerceIn(0, 23),
        initialMinute = minute % 60,
        is24Hour = use24,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
