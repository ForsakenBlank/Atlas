package com.forsakenblank.atlas.util

import com.forsakenblank.atlas.data.DayNote
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// this week against last week for one tracker, in the unit that tracker counts in
data class TrackerChange(val tracker: TrackerWithItem, val thisWeek: Double, val lastWeek: Double) {
    val difference: Double get() = thisWeek - lastWeek

    // null when last week had nothing, a percentage of nothing means nothing
    val percent: Int? get() = if (lastWeek == 0.0) null else ((difference / lastWeek) * 100).toInt()
}

data class MoneyMonth(val inPence: Long, val outPence: Long) {
    val net: Long get() = inPence - outPence
}

data class Insights(
    val changes: List<TrackerChange> = emptyList(),
    val streaks: List<Pair<TrackerWithItem, Streaks>> = emptyList(),
    val tasksThisWeek: Int = 0,
    val tasksLastWeek: Int = 0,
    val overdue: Int = 0,
    val moodThisWeek: Double? = null,
    val moodLastWeek: Double? = null,
    val moneyThisMonth: MoneyMonth = MoneyMonth(0, 0),
    val moneyLastMonth: MoneyMonth = MoneyMonth(0, 0),
    val yearLogs: Int = 0,
    val yearSeconds: Long = 0,
    val yearTasks: Int = 0,
    val yearJournalDays: Int = 0,
    val busiestDay: DayOfWeek? = null,
)

// weeks start on monday, the same as the timetable
fun startOfWeek(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

// what a week of logs adds up to for one tracker: taps, minutes, or the average for ratings
fun weekAmount(kind: TrackerKind, logs: List<LogEntry>): Double = when (kind) {
    TrackerKind.COUNTER, TrackerKind.YES_NO -> logs.size.toDouble()
    TrackerKind.TIMER -> logs.sumOf { it.durationSeconds ?: 0L } / 60.0
    TrackerKind.NUMBER -> logs.sumOf { it.value }
    TrackerKind.RATING -> if (logs.isEmpty()) 0.0 else logs.map { it.value }.average()
}

fun buildInsights(
    today: LocalDate,
    trackers: List<TrackerWithItem>,
    logs: List<LogEntry>, // everything since the start of last week or the start of the year, whichever is earlier
    loggedDays: Map<Long, Set<LocalDate>>,
    tasks: List<Task>,
    money: List<MoneyEntry>,
    journal: List<DayNote>,
): Insights {
    val thisWeek = startOfWeek(today)
    val lastWeek = thisWeek.minusWeeks(1)
    fun dayOf(log: LogEntry) = log.timestamp.toLocalDate()

    val byTracker = logs.groupBy { it.trackerId }
    val changes = trackers.map { t ->
        val mine = byTracker[t.item.id].orEmpty()
        TrackerChange(
            t,
            weekAmount(t.tracker.kind, mine.filter { dayOf(it) >= thisWeek }),
            weekAmount(t.tracker.kind, mine.filter { dayOf(it) >= lastWeek && dayOf(it) < thisWeek }),
        )
    }.filter { it.thisWeek != 0.0 || it.lastWeek != 0.0 }

    val runs = trackers
        .map { it to streaks(loggedDays[it.item.id].orEmpty(), today) }
        .filter { it.second.current > 1 || it.second.best > 1 }
        .sortedWith(compareByDescending<Pair<TrackerWithItem, Streaks>> { it.second.current }.thenByDescending { it.second.best })

    fun doneBetween(from: LocalDate, to: LocalDate) =
        tasks.count { task -> task.done && task.doneAt?.toLocalDate()?.let { it >= from && it < to } == true }
    val todayStart = today.startMillis()

    fun moodBetween(from: LocalDate, to: LocalDate) = journal
        .filter { it.mood != null && LocalDate.ofEpochDay(it.day).let { d -> d >= from && d < to } }
        .map { it.mood!! }
        .takeIf { it.isNotEmpty() }
        ?.average()

    // transfers only move money between your own accounts, they are neither earned nor spent
    fun monthOf(first: LocalDate): MoneyMonth {
        val next = first.plusMonths(1)
        val entries = money.filter { it.transferId == null && LocalDate.ofEpochDay(it.day).let { d -> d >= first && d < next } }
        return MoneyMonth(entries.filter { it.amountPence > 0 }.sumOf { it.amountPence }, entries.filter { it.amountPence < 0 }.sumOf { -it.amountPence })
    }
    val month = today.withDayOfMonth(1)

    val yearStart = today.withDayOfYear(1)
    val yearLogs = logs.filter { dayOf(it) >= yearStart }
    val busiest = yearLogs.groupingBy { dayOf(it).dayOfWeek }.eachCount().maxByOrNull { it.value }?.key

    return Insights(
        changes = changes.sortedByDescending { kotlin.math.abs(it.difference) },
        streaks = runs,
        tasksThisWeek = doneBetween(thisWeek, today.plusDays(1)),
        tasksLastWeek = doneBetween(lastWeek, thisWeek),
        overdue = tasks.count { !it.done && it.due != null && it.due < todayStart },
        moodThisWeek = moodBetween(thisWeek, today.plusDays(1)),
        moodLastWeek = moodBetween(lastWeek, thisWeek),
        moneyThisMonth = monthOf(month),
        moneyLastMonth = monthOf(month.minusMonths(1)),
        yearLogs = yearLogs.size,
        yearSeconds = yearLogs.sumOf { it.durationSeconds ?: 0L },
        yearTasks = doneBetween(yearStart, today.plusDays(1)),
        yearJournalDays = journal.count { LocalDate.ofEpochDay(it.day) >= yearStart },
        busiestDay = busiest,
    )
}
