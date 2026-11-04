package com.forsakenblank.atlas.util

import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Repeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun Repeat.label(): String = when (this) {
    Repeat.NONE -> "Does not repeat"
    Repeat.DAILY -> "Every day"
    Repeat.WEEKDAYS -> "Every weekday"
    Repeat.WEEKLY -> "Every week"
    Repeat.FORTNIGHTLY -> "Every 2 weeks"
    Repeat.MONTHLY -> "Every month"
    Repeat.YEARLY -> "Every year"
}

private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun Repeat.next(date: LocalDate): LocalDate = when (this) {
    Repeat.NONE -> date
    Repeat.DAILY -> date.plusDays(1)
    Repeat.WEEKDAYS -> {
        var d = date.plusDays(1)
        while (d.dayOfWeek in weekend) d = d.plusDays(1)
        d
    }
    Repeat.WEEKLY -> date.plusWeeks(1)
    Repeat.FORTNIGHTLY -> date.plusWeeks(2)
    Repeat.MONTHLY -> date.plusMonths(1)
    Repeat.YEARLY -> date.plusYears(1)
}

fun Repeat.occursOn(start: LocalDate, day: LocalDate, until: LocalDate? = null): Boolean {
    if (day < start) return false
    if (until != null && day > until) return false
    return when (this) {
        Repeat.NONE -> day == start
        Repeat.DAILY -> true
        Repeat.WEEKDAYS -> day.dayOfWeek !in weekend
        Repeat.WEEKLY -> day.dayOfWeek == start.dayOfWeek
        Repeat.FORTNIGHTLY -> day.dayOfWeek == start.dayOfWeek && ChronoUnit.WEEKS.between(start, day) % 2 == 0L
        Repeat.MONTHLY -> day.dayOfMonth == start.dayOfMonth
        Repeat.YEARLY -> day.month == start.month && day.dayOfMonth == start.dayOfMonth
    }
}

// a one off event can span several days, a repeating one shows on each day it repeats
fun Event.occursOn(day: LocalDate): Boolean {
    val startDay = startsAt.toLocalDate()
    if (repeatRule == Repeat.NONE) {
        val endDay = maxOf(startDay, (endsAt - 1).toLocalDate())
        return day in startDay..endDay
    }
    if (day.toEpochDay() in skippedDayList()) return false
    return repeatRule.occursOn(startDay, day, repeatUntil?.toLocalDate())
}

fun Event.skippedDayList(): Set<Long> =
    skippedDays?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()

// takes one date out of a repeat, or puts it back
fun Event.withSkipped(day: LocalDate, skip: Boolean): Event {
    val days = if (skip) skippedDayList() + day.toEpochDay() else skippedDayList() - day.toEpochDay()
    return copy(skippedDays = days.sorted().joinToString(",").ifEmpty { null })
}

fun Event.isMultiDay(): Boolean = repeatRule == Repeat.NONE && (endsAt - 1).toLocalDate() > startsAt.toLocalDate()

// one stretch of a multi day event across one week of the month grid, columns are 0 to 6
data class WeekSpan(val event: Event, val startCol: Int, val endCol: Int, val lane: Int, val continuesBefore: Boolean, val continuesAfter: Boolean)

// lays the week's multi day events out in lanes so that none of them overlap
fun weekSpans(events: List<Event>, weekStart: LocalDate): List<WeekSpan> {
    val weekEnd = weekStart.plusDays(6)
    val laneEnds = mutableListOf<Int>()
    return events.filter { it.isMultiDay() }
        .filter { it.startsAt.toLocalDate() <= weekEnd && (it.endsAt - 1).toLocalDate() >= weekStart }
        .sortedWith(compareBy<Event> { it.startsAt }.thenByDescending { it.endsAt })
        .map { event ->
            val first = event.startsAt.toLocalDate()
            val last = (event.endsAt - 1).toLocalDate()
            val startCol = ChronoUnit.DAYS.between(weekStart, maxOf(first, weekStart)).toInt()
            val endCol = ChronoUnit.DAYS.between(weekStart, minOf(last, weekEnd)).toInt()
            val free = laneEnds.indexOfFirst { it < startCol }
            val lane = if (free >= 0) free else laneEnds.size
            if (lane == laneEnds.size) laneEnds += endCol else laneEnds[lane] = endCol
            WeekSpan(event, startCol, endCol, lane, continuesBefore = (first < weekStart), continuesAfter = (last > weekEnd))
        }
}

// moves a repeating event onto the given day, keeping its clock times even across a clock change
fun Event.onDay(day: LocalDate): Event {
    if (repeatRule == Repeat.NONE) return this
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startsAt).atZone(zone)
    val shift = ChronoUnit.DAYS.between(start.toLocalDate(), day)
    val newStart = start.plusDays(shift)
    val length = endsAt - startsAt
    return copy(startsAt = newStart.toInstant().toEpochMilli(), endsAt = newStart.toInstant().toEpochMilli() + length)
}
