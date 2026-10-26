package com.forsakenblank.atlas.ui.track

import com.forsakenblank.atlas.data.Aggregate
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.Tracker
import com.forsakenblank.atlas.data.TrackerDay
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.startOfDay
import com.forsakenblank.atlas.util.streaks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class TrackerSummary(
    val item: Item,
    val tracker: Tracker,
    val todayCount: Int,
    val todaySeconds: Long,
    val todayValue: Double? = null, // number and rating trackers, null when nothing is logged today
    val streak: Int = 0,
) {
    val id: Long get() = item.id
    val kind: TrackerKind get() = tracker.kind
    val running: Boolean get() = tracker.runningSince != null
    val doneToday: Boolean get() = todayCount > 0
    val aggregate: Aggregate get() = tracker.aggregate ?: Aggregate.SUM

    // null when there is no goal to measure against
    val progress: Float?
        get() = when (kind) {
            TrackerKind.YES_NO -> if (doneToday) 1f else 0f
            TrackerKind.COUNTER -> tracker.dailyGoal?.takeIf { it > 0 }?.let { todayCount / it.toFloat() }
            TrackerKind.TIMER -> tracker.dailyGoal?.takeIf { it > 0 }?.let { todaySeconds / (it * 60f) }
            TrackerKind.NUMBER -> tracker.dailyGoal?.takeIf { it > 0 }?.let { ((todayValue ?: 0.0) / it).toFloat() }
            TrackerKind.RATING -> null
        }?.coerceIn(0f, 1f)

    val goalMet: Boolean get() = (progress ?: if (doneToday) 1f else 0f) >= 1f

    fun todayLabel(): String {
        val unit = tracker.unit?.let { " $it" }.orEmpty()
        return when (kind) {
            TrackerKind.YES_NO -> if (doneToday) "Done today" else "Not yet today"
            TrackerKind.COUNTER -> tracker.dailyGoal?.let { "$todayCount of $it$unit" } ?: "$todayCount$unit today"
            TrackerKind.TIMER -> {
                val time = if (todaySeconds == 0L) "0m" else formatDuration(todaySeconds)
                tracker.dailyGoal?.let { "$time of ${it}m" } ?: "$time today"
            }
            TrackerKind.NUMBER -> {
                val value = todayValue
                when {
                    value == null -> "Nothing logged today"
                    tracker.dailyGoal != null -> "${formatNumber(value)} of ${tracker.dailyGoal}$unit"
                    else -> "${formatNumber(value)}$unit today"
                }
            }
            TrackerKind.RATING -> todayValue?.let { "Rated ${formatNumber(it)} of 5" } ?: "Not rated today"
        }
    }
}

fun formatNumber(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

// one number for a day of logs, which is what charts and goals compare against
fun dayValue(tracker: Tracker, logs: List<LogEntry>): Double? = when (tracker.kind) {
    TrackerKind.COUNTER, TrackerKind.YES_NO -> logs.size.toDouble()
    TrackerKind.TIMER -> logs.sumOf { it.durationSeconds ?: 0L } / 60.0
    TrackerKind.RATING -> if (logs.isEmpty()) null else logs.map { it.value }.average()
    TrackerKind.NUMBER -> if (logs.isEmpty()) null else when (tracker.aggregate ?: Aggregate.SUM) {
        Aggregate.SUM -> logs.sumOf { it.value }
        Aggregate.AVERAGE -> logs.map { it.value }.average()
        Aggregate.LAST -> logs.maxBy { it.timestamp }.value
    }
}

fun summarise(trackers: List<TrackerWithItem>, todayLogs: List<LogEntry>, days: List<TrackerDay> = emptyList()): List<TrackerSummary> {
    val byTracker = todayLogs.groupBy { it.trackerId }
    val daysByTracker = days.groupBy({ it.trackerId }, { runCatching { LocalDate.parse(it.day) }.getOrNull() })
    return trackers.map { t ->
        val logs = byTracker[t.item.id].orEmpty()
        TrackerSummary(
            item = t.item,
            tracker = t.tracker,
            todayCount = logs.size,
            todaySeconds = logs.sumOf { it.durationSeconds ?: 0L },
            todayValue = if (t.tracker.kind == TrackerKind.NUMBER || t.tracker.kind == TrackerKind.RATING) dayValue(t.tracker, logs) else null,
            streak = streaks(daysByTracker[t.item.id].orEmpty().filterNotNull().toSet()).current,
        )
    }
}

// emits the start of today and again after midnight, so "today" never goes stale
private val today: Flow<Long> = flow {
    while (true) {
        emit(startOfDay())
        delay(60_000)
    }
}.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
fun todaySummaries(repo: AtlasRepository): Flow<List<TrackerSummary>> =
    today.flatMapLatest { start ->
        // a year back is plenty for the streak shown on buttons
        val yearAgo = start - TimeUnit.DAYS.toMillis(366)
        combine(repo.trackers(), repo.logsSince(start), repo.loggedDays(yearAgo)) { t, logs, days -> summarise(t, logs, days) }
    }

// what the snackbar says after a tap, worked out before the tap happens
fun TrackerSummary.tapMessage(): String? = when (kind) {
    TrackerKind.COUNTER -> "Logged ${item.name}"
    TrackerKind.YES_NO -> if (doneToday) null else "${item.name} done for today"
    TrackerKind.TIMER -> if (running) "Saved ${item.name} time" else null
    TrackerKind.NUMBER, TrackerKind.RATING -> "Logged ${item.name}"
}

// true when this tap is the one that reaches the daily goal
fun TrackerSummary.tapHitsGoal(value: Double? = null, now: Long = System.currentTimeMillis()): Boolean {
    val goal = tracker.dailyGoal?.takeIf { it > 0 }
    if (goalMet) return false
    return when (kind) {
        TrackerKind.YES_NO -> !doneToday
        TrackerKind.COUNTER -> goal != null && todayCount + 1 >= goal
        TrackerKind.TIMER -> {
            val since = tracker.runningSince ?: return false
            goal != null && todaySeconds + (now - since) / 1000 >= goal * 60L
        }
        TrackerKind.NUMBER -> {
            if (goal == null || value == null) return false
            val after = when (aggregate) {
                Aggregate.SUM -> (todayValue ?: 0.0) + value
                Aggregate.LAST -> value
                Aggregate.AVERAGE -> ((todayValue ?: 0.0) * todayCount + value) / (todayCount + 1)
            }
            after >= goal
        }
        TrackerKind.RATING -> false
    }
}
