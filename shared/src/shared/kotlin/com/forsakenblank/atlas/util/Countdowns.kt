package com.forsakenblank.atlas.util

import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

fun Countdown.startDate(): LocalDate = LocalDate.ofEpochDay(date)

// withYear moves 29 february onto the 28th in years without one
fun Countdown.nextDate(today: LocalDate = LocalDate.now()): LocalDate {
    val start = startDate()
    if (!yearly || countUp) return start
    // a yearly date with a known year has not started yet before its first time
    if (yearKnown && start >= today) return start
    val thisYear = start.withYear(today.year)
    return if (thisYear >= today) thisYear else start.withYear(today.year + 1)
}

// negative once a one off has been and gone
fun Countdown.daysUntil(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, nextDate(today))

fun Countdown.daysSince(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(startDate(), today)

fun Countdown.isPassed(today: LocalDate = LocalDate.now()): Boolean = !countUp && daysUntil(today) < 0

fun Countdown.occursOn(day: LocalDate): Boolean {
    val start = startDate()
    if (!yearly || countUp) return day == start
    if (yearKnown && day < start) return false
    return start.withYear(day.year) == day
}

private fun yearsOnNext(countdown: Countdown, today: LocalDate): Int? {
    if (!countdown.yearly || !countdown.yearKnown || countdown.countUp) return null
    return (countdown.nextDate(today).year - countdown.startDate().year).takeIf { it > 0 }
}

// how old they will be on the next birthday, only when the year is known
fun Countdown.turning(today: LocalDate = LocalDate.now()): Int? =
    if (kind == CountdownKind.BIRTHDAY) yearsOnNext(this, today) else null

// "turns 19" for birthdays, "5th anniversary" for anniversaries
fun Countdown.milestone(today: LocalDate = LocalDate.now()): String? {
    val years = yearsOnNext(this, today) ?: return null
    return when (kind) {
        CountdownKind.BIRTHDAY -> "turns $years"
        CountdownKind.ANNIVERSARY -> "${ordinal(years)} anniversary"
        else -> null
    }
}

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

private fun plural(n: Long, word: String): String = if (n == 1L) "1 $word" else "$n ${word}s"

// days while it is close, then rough months, then years
private fun span(days: Long): String = when {
    days < 60 -> plural(days, "day")
    days < 730 -> plural((days / 30.44).roundToLong(), "month")
    else -> plural(days / 365, "year")
}

fun Countdown.label(today: LocalDate = LocalDate.now()): String {
    if (countUp) {
        val since = daysSince(today)
        if (since == 0L) return "Today"
        if (since > 0) return "${plural(since, "day")} since"
    }
    val days = daysUntil(today)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days > 0 -> "in ${span(days)}"
        else -> "${span(-days)} ago"
    }
}

// soonest first, leaving out count ups and one offs that have been and gone
fun upcoming(countdowns: List<Countdown>, today: LocalDate = LocalDate.now()): List<Countdown> =
    countdowns
        .filter { !it.countUp && it.daysUntil(today) >= 0 }
        .sortedWith(compareBy<Countdown> { it.daysUntil(today) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
