package com.forsakenblank.atlas.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun startOfDay(millis: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "${s}s"
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val twelveHourFormat = DateTimeFormatter.ofPattern("h:mm a")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

fun formatTime(millis: Long, use24: Boolean = true): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(if (use24) timeFormat else twelveHourFormat)

// minutes after midnight, as used by timetable slots
fun formatMinuteOfDay(minute: Int, use24: Boolean = true): String =
    LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60).format(if (use24) timeFormat else twelveHourFormat)

fun LocalDate.atMinute(minute: Int): Long =
    atTime(LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.minuteOfDay(): Int {
    val time = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.hour * 60 + time.minute
}

fun formatDay(millis: Long): String {
    val date = millis.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormat)
    }
}

data class Streaks(val current: Int, val best: Int)

// a day counts once it has any log, today being empty does not break the streak yet
fun streaks(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Streaks {
    if (days.isEmpty()) return Streaks(0, 0)
    var current = 0
    var day = if (today in days) today else today.minusDays(1)
    while (day in days) {
        current++
        day = day.minusDays(1)
    }
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (d in days.sorted()) {
        run = if (previous != null && d == previous.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        previous = d
    }
    return Streaks(current, best)
}
