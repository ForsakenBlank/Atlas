package com.forsakenblank.atlas.ui.countdowns

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import com.forsakenblank.atlas.ui.common.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.SelectionBar
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.common.toggle
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.theme.onColor
import com.forsakenblank.atlas.util.daysSince
import com.forsakenblank.atlas.util.daysUntil
import com.forsakenblank.atlas.util.isPassed
import com.forsakenblank.atlas.util.milestone
import com.forsakenblank.atlas.util.nextDate
import com.forsakenblank.atlas.util.startDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class CountdownsViewModel(private val repo: AtlasRepository) : ViewModel() {

    // null until the first load so the empty state does not flash
    val countdowns: StateFlow<List<Countdown>?> = repo.countdowns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(ids: Set<Long>) {
        viewModelScope.launch { ids.forEach { repo.deleteCountdown(it) } }
    }
}

internal fun CountdownKind.title(): String = when (this) {
    CountdownKind.BIRTHDAY -> "Birthday"
    CountdownKind.ANNIVERSARY -> "Anniversary"
    CountdownKind.HOLIDAY -> "Holiday"
    CountdownKind.EVENT -> "Event"
    CountdownKind.OTHER -> "Other"
}

private fun CountdownKind.plural(): String = when (this) {
    CountdownKind.BIRTHDAY -> "Birthdays"
    CountdownKind.ANNIVERSARY -> "Anniversaries"
    CountdownKind.HOLIDAY -> "Holidays"
    CountdownKind.EVENT -> "Events"
    CountdownKind.OTHER -> "Other"
}

internal fun CountdownKind.icon(): ImageVector = when (this) {
    CountdownKind.BIRTHDAY -> Icons.Outlined.Cake
    CountdownKind.ANNIVERSARY -> Icons.Outlined.FavoriteBorder
    CountdownKind.HOLIDAY -> Icons.Outlined.BeachAccess
    CountdownKind.EVENT -> Icons.Outlined.Celebration
    CountdownKind.OTHER -> Icons.Outlined.Star
}

// coming up soonest first, then count ups newest first, then passed ones most recent first
internal fun countdownOrder(today: LocalDate): Comparator<Countdown> {
    fun stage(c: Countdown) = when {
        c.countUp -> 1
        c.isPassed(today) -> 2
        else -> 0
    }
    fun distance(c: Countdown) = abs(if (c.countUp) c.daysSince(today) else c.daysUntil(today))
    return compareBy<Countdown>({ stage(it) }, { distance(it) }).thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
}

// the big number on a card and the words under it, "Today!" stands on its own
internal fun bigCount(countdown: Countdown, today: LocalDate): Pair<String, String?> {
    if (countdown.countUp) {
        val since = countdown.daysSince(today)
        if (since >= 0) return since.toString() to if (since == 1L) "day since" else "days since"
    }
    val days = countdown.daysUntil(today)
    return when {
        days == 0L -> "Today!" to null
        days > 0 -> days.toString() to if (days == 1L) "day to go" else "days to go"
        else -> (-days).toString() to if (days == -1L) "day ago" else "days ago"
    }
}

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val sinceDate = DateTimeFormatter.ofPattern("d MMM yyyy")

internal fun dateLine(countdown: Countdown, today: LocalDate): String {
    if (countdown.countUp) return "Since ${countdown.startDate().format(sinceDate)}"
    val next = countdown.nextDate(today)
    return next.format(if (next.year == today.year) shortDate else longDate)
}

// a soft wash of the countdown's colour behind the card
@Composable
internal fun cardTint(countdown: Countdown): Color? =
    countdown.color?.let { lerp(MaterialTheme.colorScheme.surfaceContainer, Color(it), 0.18f) }

@Composable
internal fun CountdownBadge(countdown: Countdown, size: Dp, modifier: Modifier = Modifier) {
    val fill = countdown.color.toItemColor(MaterialTheme.colorScheme.primaryContainer)
    val tint = if (countdown.color == null) MaterialTheme.colorScheme.onPrimaryContainer else fill.onColor()
    val emoji = countdown.emoji?.trim().orEmpty()
    Box(modifier.size(size).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        if (emoji.isNotEmpty()) {
            // sized from the circle, not the font scale, so it never spills out
            val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
            Text(emoji, fontSize = fontSize, maxLines = 1)
        } else {
            Icon(countdown.kind.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

private enum class CountdownGroup(val title: String) {
    PINNED("Pinned"),
    COMING_UP("Coming up"),
    DAYS_SINCE("Days since"),
    PASSED("Passed"),
}

private class CountdownSection(val group: CountdownGroup, val countdowns: List<Countdown>)

private fun groupCountdowns(countdowns: List<Countdown>, today: LocalDate): List<CountdownSection> {
    val byGroup = countdowns.groupBy { c ->
        when {
            c.pinned -> CountdownGroup.PINNED
            c.countUp -> CountdownGroup.DAYS_SINCE
            c.isPassed(today) -> CountdownGroup.PASSED
            else -> CountdownGroup.COMING_UP
        }
    }
    val order = countdownOrder(today)
    return CountdownGroup.entries.mapNotNull { group ->
        byGroup[group]?.let { CountdownSection(group, it.sortedWith(order)) }
    }
}

private enum class NewCountdown(val label: String, val icon: ImageVector, val kind: CountdownKind, val countUp: Boolean) {
    BIRTHDAY("Birthday", Icons.Outlined.Cake, CountdownKind.BIRTHDAY, false),
    ANNIVERSARY("Anniversary", Icons.Outlined.FavoriteBorder, CountdownKind.ANNIVERSARY, false),
    COUNTDOWN("Countdown", Icons.Outlined.HourglassTop, CountdownKind.EVENT, false),
    DAYS_SINCE("Days since", Icons.Outlined.History, CountdownKind.OTHER, true),
}

@Composable
fun CountdownsScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { CountdownsViewModel(it.repository) }
    val all by vm.countdowns.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val buzz = rememberHaptic()

    var filter by rememberSaveable { mutableStateOf<CountdownKind?>(null) }
    var showPassed by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    val loaded = all != null
    val countdowns = all.orEmpty()
    val usedKinds = remember(countdowns) { CountdownKind.entries.filter { kind -> countdowns.any { it.kind == kind } } }
    // a kind whose last countdown went away drops back to showing everything
    val activeFilter = filter?.takeIf { it in usedKinds }
    val today = LocalDate.now()
    val sections = remember(countdowns, activeFilter, today) {
        groupCountdowns(if (activeFilter == null) countdowns else countdowns.filter { it.kind == activeFilter }, today)
    }
    // select all only picks what can be seen, a collapsed group stays out of it
    val visibleIds = sections
        .filter { it.group != CountdownGroup.PASSED || showPassed }
        .flatMap { section -> section.countdowns.map { it.id } }
        .toSet()

    // a countdown can vanish while selected, for example when it is deleted from its editor
    LaunchedEffect(countdowns) {
        val ids = countdowns.map { it.id }.toSet()
        if (!ids.containsAll(selected)) selected = selected intersect ids
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (usedKinds.size > 1) {
                item(key = "filter") {
                    KindFilter(kinds = usedKinds, selected = activeFilter) {
                        filter = it
                        selected = emptySet()
                    }
                }
            }
            if (loaded && countdowns.isEmpty()) {
                item(key = "empty") {
                    NoCountdowns { navigator.openCountdown(null, CountdownKind.BIRTHDAY) }
                }
            }
            sections.forEach { section ->
                val collapsible = section.group == CountdownGroup.PASSED
                item(key = "group-${section.group.name}") {
                    GroupHeading(
                        title = section.group.title,
                        count = section.countdowns.size,
                        expanded = if (collapsible) showPassed else null,
                        onClick = if (collapsible) {
                            { showPassed = !showPassed }
                        } else {
                            null
                        },
                    )
                }
                if (!collapsible || showPassed) {
                    items(section.countdowns, key = { it.id }) { countdown ->
                        CountdownCard(
                            countdown = countdown,
                            today = today,
                            selecting = selecting,
                            selected = countdown.id in selected,
                            onClick = {
                                if (selecting) selected = selected.toggle(countdown.id) else navigator.openCountdown(countdown.id)
                            },
                            onLongClick = {
                                buzz()
                                selected = selected.toggle(countdown.id)
                            },
                            modifier = if (settings.reduceMotion) Modifier else Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClear = { selected = emptySet() },
                onSelectAll = if (!selected.containsAll(visibleIds)) ({ selected = visibleIds }) else null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            }
        } else {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("New") },
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    NewCountdown.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { Icon(option.icon, contentDescription = null) },
                            onClick = {
                                addMenu = false
                                navigator.openCountdown(null, option.kind, option.countUp)
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val only = if (selected.size == 1) countdowns.firstOrNull { it.id in selected } else null
        ConfirmDialog(
            title = if (only != null) "Delete ${only.title}?" else "Delete ${selected.size} countdowns?",
            body = "This cannot be undone.",
            button = "Delete",
            onDismiss = { confirmDelete = false },
        ) {
            vm.delete(selected)
            selected = emptySet()
        }
    }
}

@Composable
private fun KindFilter(kinds: List<CountdownKind>, selected: CountdownKind?, onSelect: (CountdownKind?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        kinds.forEach { kind ->
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(if (selected == kind) null else kind) },
                label = { Text(kind.plural()) },
                leadingIcon = { Icon(kind.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun NoCountdowns(onAddBirthday: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Outlined.Cake,
            title = "No countdowns yet",
            body = "Countdowns keep track of birthdays, anniversaries, holidays and other big dates, and show how many " +
                "days are left. You can also count the days since something happened. The next few show up on Home.",
        )
        Button(onClick = onAddBirthday) {
            Icon(Icons.Outlined.Cake, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Add a birthday")
        }
    }
}

@Composable
private fun GroupHeading(title: String, count: Int, expanded: Boolean? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        if (expanded != null) {
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide passed ones" else "Show passed ones",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountdownCard(
    countdown: Countdown,
    today: LocalDate,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (number, caption) = bigCount(countdown, today)
    val milestone = countdown.milestone(today)
    val outline = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape) else Modifier
    AtlasCard(
        modifier = modifier.fillMaxWidth().then(outline),
        color = cardTint(countdown),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                CountdownBadge(countdown, size = 52.dp)
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        countdown.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (countdown.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (countdown.yearly && !countdown.countUp) {
                        Icon(
                            Icons.Outlined.Repeat,
                            contentDescription = "Every year",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(dateLine(countdown, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (milestone != null) {
                    Text(milestone, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    number,
                    style = if (caption == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (caption == null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = 1,
                )
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
