package com.forsakenblank.atlas.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.journal.Mood
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.track.formatNumber
import com.forsakenblank.atlas.util.Insights
import com.forsakenblank.atlas.util.TrackerChange
import com.forsakenblank.atlas.util.buildInsights
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.penceInput
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.startOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class InsightsViewModel(repo: AtlasRepository) : ViewModel() {
    private val today = LocalDate.now()
    private val from = minOf(startOfWeek(today).minusWeeks(1), today.withDayOfYear(1))

    private val base = combine(repo.trackers(), repo.logsBetween(from.startMillis(), today.plusDays(1).startMillis()), repo.loggedDays(today.minusDays(366).startMillis())) { t, logs, days ->
        Triple(t, logs, days.groupBy({ it.trackerId }, { runCatching { LocalDate.parse(it.day) }.getOrNull() }).mapValues { (_, v) -> v.filterNotNull().toSet() })
    }

    val insights = combine(base, repo.tasks(), repo.moneyEntries(), repo.dayNotes()) { (trackers, logs, days), tasks, money, journal ->
        buildInsights(today, trackers, logs, days, tasks, money, journal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InsightsScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { InsightsViewModel(it.repository) }
    val state by vm.insights.collectAsStateWithLifecycle()
    val insights = state ?: return

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "week") { WeekCard(insights) }
        if (insights.changes.isNotEmpty()) item(key = "trackers") { TrackersCard(insights.changes) }
        if (insights.streaks.isNotEmpty()) item(key = "streaks") { StreaksCard(insights) }
        item(key = "money") { MoneyCard(insights) }
        item(key = "year") { YearCard(insights) }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

// a big number with a small label, used across every card
@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.widthIn(min = 96.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCard(insights: Insights) {
    Card("This week") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${insights.tasksThisWeek}", "tasks done" + versus(insights.tasksThisWeek, insights.tasksLastWeek))
            if (insights.overdue > 0) Stat("${insights.overdue}", "overdue", tint = MaterialTheme.colorScheme.error)
            insights.moodThisWeek?.let { mood ->
                val face = Mood.of(mood.roundToInt())
                Stat("${face?.face.orEmpty()} ${"%.1f".format(mood)}", "average mood" + (insights.moodLastWeek?.let { " (was ${"%.1f".format(it)})" } ?: ""))
            }
        }
    }
}

@Composable
private fun TrackersCard(changes: List<TrackerChange>) {
    Card("Trackers against last week") {
        changes.take(8).forEach { change ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(change.tracker.item.name, fontWeight = FontWeight.Medium)
                    Text(
                        "${amount(change.tracker.tracker.kind, change.thisWeek)} this week · ${amount(change.tracker.tracker.kind, change.lastWeek)} last week",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Arrow(change)
            }
        }
    }
}

@Composable
private fun Arrow(change: TrackerChange) {
    val up = change.difference > 0
    val colour = when {
        change.difference == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        up -> Color(0xFF46A758)
        else -> MaterialTheme.colorScheme.error
    }
    val text = when {
        change.difference == 0.0 -> "same"
        change.percent != null -> (if (up) "▲ " else "▼ ") + "${abs(change.percent!!)}%"
        else -> "▲ new"
    }
    Text(text, color = colour, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun StreaksCard(insights: Insights) {
    Card("Streaks") {
        insights.streaks.take(5).forEach { (tracker, runs) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tracker.item.name, modifier = Modifier.weight(1f))
                Text(
                    if (runs.current > 0) "🔥 ${runs.current} days" else "best ${runs.best}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (runs.current > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (runs.current > 0 && runs.best > runs.current) {
                    Text("  best ${runs.best}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoneyCard(insights: Insights) {
    val symbol = LocalSettings.current.currencySymbol
    fun money(pence: Long) = (if (pence < 0) "-" else "") + symbol + penceInput(abs(pence))
    val now = insights.moneyThisMonth
    val before = insights.moneyLastMonth
    if (now.inPence == 0L && now.outPence == 0L && before.inPence == 0L && before.outPence == 0L) return
    Card("Money this month") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(money(now.inPence), "in · last month ${money(before.inPence)}")
            Stat(money(now.outPence), "out · last month ${money(before.outPence)}")
            Stat(
                money(now.net),
                "left over",
                tint = if (now.net >= 0) Color(0xFF46A758) else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearCard(insights: Insights) {
    Card("${LocalDate.now().year} so far") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${insights.yearLogs}", "things logged")
            if (insights.yearSeconds > 0) Stat(formatDuration(insights.yearSeconds).substringBefore(" "), "of timed work")
            Stat("${insights.yearTasks}", "tasks finished")
            if (insights.yearJournalDays > 0) Stat("${insights.yearJournalDays}", "journal days")
        }
        insights.busiestDay?.let {
            Text(
                "${it.getDisplayName(TextStyle.FULL, Locale.getDefault())} is when you log the most.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun versus(now: Int, before: Int): String = when {
    before == 0 && now == 0 -> ""
    now > before -> " · up from $before"
    now < before -> " · down from $before"
    else -> " · same as last week"
}

private fun amount(kind: TrackerKind, value: Double): String = when (kind) {
    TrackerKind.TIMER -> formatDuration((value * 60).toLong())
    TrackerKind.RATING -> "${formatNumber(value)} avg"
    else -> formatNumber(value)
}
