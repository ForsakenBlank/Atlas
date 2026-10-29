package com.forsakenblank.atlas.ui.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.Tracker
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.streaks
import com.forsakenblank.atlas.util.toLocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// one row of the grid: how much was done each of the last seven days, oldest first
data class Habit(val item: Item, val tracker: Tracker, val week: List<Int>, val streak: Int, val best: Int) {
    val goal: Int get() = if (tracker.kind == TrackerKind.COUNTER) (tracker.dailyGoal ?: 1).coerceAtLeast(1) else 1
    val doneToday: Boolean get() = week.last() >= goal
    val daysHit: Int get() = week.count { it >= goal }
}

class HabitsViewModel(private val repo: AtlasRepository) : ViewModel() {
    private val today = LocalDate.now()
    val days: List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }

    val habits = combine(
        repo.trackers(),
        repo.logsBetween(days.first().startMillis(), today.plusDays(1).startMillis()),
        repo.loggedDays(today.minusDays(366).startMillis()),
    ) { trackers, logs, logged ->
        val byTracker = logs.groupBy { it.trackerId }
        val loggedByTracker = logged.groupBy({ it.trackerId }, { runCatching { LocalDate.parse(it.day) }.getOrNull() })
        trackers
            .filter { it.tracker.kind == TrackerKind.YES_NO || it.tracker.kind == TrackerKind.COUNTER }
            .map { t ->
                val perDay = byTracker[t.item.id].orEmpty().groupingBy { it.timestamp.toLocalDate() }.eachCount()
                val runs = streaks(loggedByTracker[t.item.id].orEmpty().filterNotNull().toSet(), today)
                Habit(t.item, t.tracker, days.map { perDay[it] ?: 0 }, runs.current, runs.best)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun tick(habit: Habit, day: LocalDate) = viewModelScope.launch { repo.tickDay(habit.item.id, day) }

    fun untick(habit: Habit, day: LocalDate) = viewModelScope.launch { repo.untickDay(habit.item.id, day) }
}

@Composable
fun HabitsScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { HabitsViewModel(it.repository) }
    val list by vm.habits.collectAsStateWithLifecycle()
    val habits = list ?: return

    if (habits.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(Icons.Outlined.TaskAlt, "No habits yet", "Habits are your yes or no and counter trackers. Make one in Track and it shows up here.")
            Spacer(Modifier.padding(8.dp))
            Button(onClick = { navigator.openSection(Section.TRACK, isTab = false) }) { Text("Go to Track") }
        }
        return
    }

    val buzz = rememberHaptic()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "today") { TodayCard(habits) }
        item(key = "days") { DayHeader(vm.days) }
        items(habits, key = { it.item.id }) { habit ->
            HabitRow(
                habit = habit,
                days = vm.days,
                onTick = { day ->
                    buzz()
                    vm.tick(habit, day)
                },
                onUntick = { day -> vm.untick(habit, day) },
            )
        }
        item(key = "hint") {
            Text(
                "Tap a day to tick it, tap a counter again to add one more. Hold a day to take one off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TodayCard(habits: List<Habit>) {
    val done = habits.count { it.doneToday }
    val best = habits.maxByOrNull { it.streak }
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (done == habits.size) "Everything done today 🎉" else "$done of ${habits.size} done today",
                style = MaterialTheme.typography.titleMedium,
            )
            LinearProgressIndicator(progress = { done / habits.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            if (best != null && best.streak > 1) {
                Text(
                    "Longest going: ${best.item.name}, ${best.streak} days in a row",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(days: List<LocalDate>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1.4f))
        days.forEach { day ->
            Text(
                day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = if (day == days.last()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(habit: Habit, days: List<LocalDate>, onTick: (LocalDate) -> Unit, onUntick: (LocalDate) -> Unit) {
    val colour = habit.item.color.toItemColor(MaterialTheme.colorScheme.primary)
    AtlasCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.4f)) {
                Text(habit.item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    buildString {
                        append("${habit.daysHit} of 7")
                        if (habit.streak > 0) append(" · 🔥 ${habit.streak}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            days.forEachIndexed { index, day ->
                val count = habit.week[index]
                val fraction = (count / habit.goal.toFloat()).coerceIn(0f, 1f)
                Box(Modifier.weight(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (fraction >= 1f) colour else colour.copy(alpha = fraction * 0.45f))
                            .border(1.5.dp, if (count > 0) colour else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .combinedClickable(onClick = { onTick(day) }, onLongClick = { onUntick(day) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (habit.tracker.kind == TrackerKind.COUNTER && count > 0) {
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (fraction >= 1f) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
