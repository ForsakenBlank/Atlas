package com.forsakenblank.atlas.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.TaskSort
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatDay
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.next
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class QuickTask(val title: String, val due: LocalDate?, val priority: Int)

private val spaces = Regex("\\s+")
private val trailingMarks = Regex("!+\$")

private val weekdayWords: Map<String, DayOfWeek> = buildMap {
    for (day in DayOfWeek.values()) {
        val name = day.name.lowercase()
        put(name, day)
        put(name.take(3), day)
    }
    put("tues", DayOfWeek.TUESDAY)
    put("weds", DayOfWeek.WEDNESDAY)
    put("thur", DayOfWeek.THURSDAY)
    put("thurs", DayOfWeek.THURSDAY)
}

// reads hints like "essay fri !!" off the end of the text, a weekday always means the next one after today
fun parseQuickTask(text: String, today: LocalDate): QuickTask {
    var title = text.trim().replace(spaces, " ")
    var due: LocalDate? = null
    var priority = 0
    while (true) {
        val marks = if (priority == 0) trailingMarks.find(title) else null
        if (marks != null) {
            val rest = title.substring(0, marks.range.first).trimEnd()
            // a title made only of hints stays as it was typed
            if (rest.isEmpty()) break
            priority = minOf(marks.value.length, 3)
            title = rest
            continue
        }
        val lastSpace = title.lastIndexOf(' ')
        val date = if (due == null && lastSpace > 0) dayFromWord(title.substring(lastSpace + 1), today) else null
        if (date != null) {
            due = date
            title = title.substring(0, lastSpace).trimEnd()
            continue
        }
        break
    }
    return QuickTask(title, due, priority)
}

private fun dayFromWord(word: String, today: LocalDate): LocalDate? = when (val w = word.lowercase()) {
    "today" -> today
    "tomorrow", "tmr", "tmrw" -> today.plusDays(1)
    else -> weekdayWords[w]?.let { today.with(TemporalAdjusters.next(it)) }
}

internal val priorityNames = listOf("None", "Low", "Medium", "High")

// formatDay already says today and yesterday
internal fun dueDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (date == today.plusDays(1)) "Tomorrow" else formatDay(date.startMillis())

class TasksViewModel(private val repo: AtlasRepository) : ViewModel() {

    // null until the first load so the empty state does not flash
    val tasks: StateFlow<List<Task>?> = repo.tasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val subjects: StateFlow<List<Subject>> = repo.subjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(quick: QuickTask, subjectId: Long?) {
        viewModelScope.launch {
            repo.saveTask(Task(title = quick.title, due = quick.due?.startMillis(), priority = quick.priority, subjectId = subjectId))
        }
    }

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { repo.setTaskDone(task, done) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { repo.deleteTask(task.id) }
    }

    fun putBack(task: Task) {
        viewModelScope.launch { repo.saveTask(task) }
    }
}

private enum class TaskGroup(val title: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    THIS_WEEK("This week"),
    LATER("Later"),
    SOMEDAY("Someday"),
    DONE("Done"),
}

private class TaskSection(val group: TaskGroup, val tasks: List<Task>, val total: Int)

private fun groupOf(task: Task, today: LocalDate, weekEnd: LocalDate): TaskGroup {
    if (task.done) return TaskGroup.DONE
    val due = task.due?.toLocalDate() ?: return TaskGroup.SOMEDAY
    return when {
        due < today -> TaskGroup.OVERDUE
        due == today -> TaskGroup.TODAY
        due == today.plusDays(1) -> TaskGroup.TOMORROW
        due <= weekEnd -> TaskGroup.THIS_WEEK
        else -> TaskGroup.LATER
    }
}

private fun taskOrder(sort: TaskSort): Comparator<Task> = when (sort) {
    TaskSort.DUE -> compareBy<Task> { it.due ?: Long.MAX_VALUE }.thenByDescending { it.priority }.thenBy { it.created }
    TaskSort.PRIORITY -> compareByDescending<Task> { it.priority }.thenBy { it.due ?: Long.MAX_VALUE }.thenBy { it.created }
    TaskSort.CREATED -> compareByDescending<Task> { it.created }
    TaskSort.TITLE -> compareBy<Task, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.created }
}

private fun groupTasks(
    tasks: List<Task>,
    today: LocalDate,
    sort: TaskSort,
    weekStartsMonday: Boolean,
    showDone: Boolean,
): List<TaskSection> {
    val lastDay = if (weekStartsMonday) DayOfWeek.SUNDAY else DayOfWeek.SATURDAY
    val weekEnd = today.with(TemporalAdjusters.nextOrSame(lastDay))
    val byGroup = tasks.groupBy { groupOf(it, today, weekEnd) }
    val order = taskOrder(sort)
    return TaskGroup.entries.mapNotNull { group ->
        val list = byGroup[group].orEmpty()
        when {
            list.isEmpty() -> null
            // finished tasks read best newest first
            group == TaskGroup.DONE -> if (showDone) {
                TaskSection(group, list.sortedByDescending { it.doneAt ?: it.created }.take(50), list.size)
            } else {
                null
            }
            else -> TaskSection(group, list.sortedWith(order), list.size)
        }
    }
}

// lower case so "today" and "tomorrow" sit inside a sentence
private fun relativeDay(date: LocalDate): String {
    val label = dueDayLabel(date)
    return if (label == "Today" || label == "Tomorrow" || label == "Yesterday") label.lowercase() else label
}

@Composable
fun TasksScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { TasksViewModel(it.repository) }
    val allTasks by vm.tasks.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val buzz = rememberHaptic()

    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }

    val loaded = allTasks != null
    val tasks = allTasks.orEmpty()
    val subjectsById = remember(subjects) { subjects.associateBy { it.id } }
    val usedSubjects = remember(subjects, tasks) { subjects.filter { s -> tasks.any { it.subjectId == s.id } } }
    // a subject whose last task went away drops back to showing everything
    val activeFilter = filter?.takeIf { id -> usedSubjects.any { it.id == id } }
    val today = LocalDate.now()
    val sections = remember(tasks, activeFilter, today, settings.taskSort, settings.weekStartsMonday, settings.showCompletedTasks) {
        groupTasks(
            tasks = if (activeFilter == null) tasks else tasks.filter { it.subjectId == activeFilter },
            today = today,
            sort = settings.taskSort,
            weekStartsMonday = settings.weekStartsMonday,
            showDone = settings.showCompletedTasks,
        )
    }

    fun toggle(task: Task, done: Boolean) {
        if (!done) {
            vm.setDone(task, false)
            return
        }
        buzz()
        vm.setDone(task, true)
        val repeating = task.repeatRule != Repeat.NONE
        if (!repeating && !settings.showUndo) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (repeating) {
                val next = task.repeatRule.next(task.due?.toLocalDate() ?: LocalDate.now())
                snackbar.showSnackbar("Next one due ${relativeDay(next)}", duration = SnackbarDuration.Short)
            } else {
                val finished = task.copy(done = true, doneAt = System.currentTimeMillis())
                val result = snackbar.showSnackbar("Done", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.setDone(finished, false)
            }
        }
    }

    fun delete(task: Task) {
        vm.delete(task)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Deleted \"${task.title}\"", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.putBack(task)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            QuickAddField(
                onAdd = { text -> vm.add(parseQuickTask(text, LocalDate.now()), activeFilter) },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            if (usedSubjects.isNotEmpty()) {
                SubjectFilter(subjects = usedSubjects, selected = activeFilter, onSelect = { filter = it })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (loaded && tasks.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.CheckCircle,
                            title = "No tasks yet",
                            body = "Type above and press done to add one. End it with today, tomorrow or a day like fri " +
                                "to set when it is due, and add ! to !!! for priority.",
                        )
                    }
                } else if (tasks.isNotEmpty() && sections.none { it.group != TaskGroup.DONE }) {
                    item(key = "caught-up") {
                        EmptyState(
                            icon = Icons.Outlined.CheckCircle,
                            title = "All caught up",
                            body = "Nothing left to do here.",
                        )
                    }
                }
                sections.forEach { section ->
                    val isDone = section.group == TaskGroup.DONE
                    item(key = "group-${section.group.name}") {
                        GroupHeading(
                            title = section.group.title,
                            count = section.total,
                            color = if (section.group == TaskGroup.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            expanded = if (isDone) showDone else null,
                            onClick = if (isDone) {
                                { showDone = !showDone }
                            } else {
                                null
                            },
                        )
                    }
                    if (!isDone || showDone) {
                        items(section.tasks, key = { it.id }) { task ->
                            SwipeableTask(
                                done = task.done,
                                onDone = { toggle(task, !task.done) },
                                onDelete = { delete(task) },
                                modifier = if (settings.reduceMotion) Modifier else Modifier.animateItem(),
                            ) {
                                TaskRow(
                                    task = task,
                                    subject = task.subjectId?.let { subjectsById[it] },
                                    onToggle = { done -> toggle(task, done) },
                                    onClick = { editing = task },
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("New task") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating) {
        TaskEditorDialog(task = null, initialSubjectId = activeFilter, onDismiss = { creating = false })
    }
    editing?.let { task ->
        TaskEditorDialog(task = task, onDismiss = { editing = null })
    }
}

@Composable
private fun QuickAddField(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }

    // the keyboard stays open so several tasks can go in one after another
    fun submit() {
        if (text.isBlank()) return
        onAdd(text.trim())
        text = ""
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Add a task, like essay fri !!") },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            if (text.isNotBlank()) {
                IconButton(onClick = { submit() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add task")
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SubjectFilter(subjects: List<Subject>, selected: Long?, onSelect: (Long?) -> Unit) {
    val fallback = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        subjects.forEach { subject ->
            FilterChip(
                selected = selected == subject.id,
                onClick = { onSelect(if (selected == subject.id) null else subject.id) },
                label = { Text(subject.name) },
                leadingIcon = { ColourDot(subject.color.toItemColor(fallback)) },
            )
        }
    }
}

@Composable
private fun GroupHeading(
    title: String,
    count: Int,
    color: Color,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        if (expanded != null) {
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide done tasks" else "Show done tasks",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// swipe right to tick off, swipe left to delete (with undo)
@Composable
private fun SwipeableTask(
    done: Boolean,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDone()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            val deleting = direction == SwipeToDismissBoxValue.EndToStart
            val colors = MaterialTheme.colorScheme
            Row(
                Modifier
                    .fillMaxSize()
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> colors.primaryContainer
                            SwipeToDismissBoxValue.EndToStart -> colors.errorContainer
                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                        },
                    )
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (deleting) Arrangement.End else Arrangement.Start,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        when {
                            deleting -> Icons.Outlined.Delete
                            done -> Icons.AutoMirrored.Outlined.Undo
                            else -> Icons.Outlined.CheckCircle
                        },
                        contentDescription = null,
                        tint = if (deleting) colors.onErrorContainer else colors.onPrimaryContainer,
                    )
                }
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
fun TaskRow(
    task: Task,
    subject: Subject?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dueDate = task.due?.toLocalDate()
    val overdue = !task.done && dueDate != null && dueDate < LocalDate.now()
    val hasDetails = dueDate != null || task.priority > 0 || task.repeatRule != Repeat.NONE || subject != null
    ListItem(
        headlineContent = {
            Text(
                task.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        },
        supportingContent = if (hasDetails) {
            { TaskDetails(task, dueDate, overdue, subject) }
        } else {
            null
        },
        leadingContent = { Checkbox(checked = task.done, onCheckedChange = onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TaskDetails(task: Task, dueDate: LocalDate?, overdue: Boolean, subject: Subject?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dueDate != null) {
            Text(
                dueDayLabel(dueDate),
                color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                maxLines = 1,
            )
        }
        if (task.priority > 0) {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = "${priorityNames.getOrElse(task.priority) { "High" }} priority",
                tint = priorityColour(task.priority),
                modifier = Modifier.size(16.dp),
            )
        }
        if (task.repeatRule != Repeat.NONE) {
            Icon(Icons.Outlined.Repeat, contentDescription = task.repeatRule.label(), modifier = Modifier.size(16.dp))
        }
        if (subject != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ColourDot(subject.color.toItemColor(MaterialTheme.colorScheme.outline))
                Text(subject.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun priorityColour(level: Int): Color = when (level) {
    1 -> Color(0xFF1E88E5)
    2 -> Color(0xFFFFA000)
    else -> MaterialTheme.colorScheme.error
}

@Composable
internal fun ColourDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}
