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
    return repeatRule.occursOn(startDay, day, repeatUntil?.toLocalDate())
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
