package com.forsakenblank.atlas.util

import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Task
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// the key stays the same for the same reminder, so rescheduling keeps its notification id
data class Reminder(val key: String, val at: Long, val title: String, val text: String)

// how far ahead alarms are set, the refresh after midnight tops it back up
const val REMINDER_DAYS = 8

// stored on an event or in settings when there should be no reminder
const val NO_REMINDER = -1

val ReminderMinuteOptions = listOf(5, 10, 15, 30, 60, 24 * 60)

fun reminderMinutesLabel(minutes: Int): String = when {
    minutes < 0 -> "None"
    minutes == 0 -> "When it starts"
    minutes % (24 * 60) == 0 -> countOf(minutes / (24 * 60), "day") + " before"
    minutes % 60 == 0 -> countOf(minutes / 60, "hour") + " before"
    else -> countOf(minutes, "minute") + " before"
}

private fun countOf(n: Int, word: String): String = if (n == 1) "1 $word" else "$n ${word}s"

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")

private fun days(from: LocalDate, to: LocalDate): Sequence<LocalDate> =
    generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }

private fun String.capitalised(): String = replaceFirstChar { it.uppercase() }

// "Turns 30 tomorrow" when there is a milestone, otherwise just "Tomorrow"
private fun countdownText(label: String, milestone: String?): String =
    if (milestone == null) label.capitalised() else "${milestone.capitalised()} ${label.lowercase()}"

fun planReminders(
    now: Long,
    zone: ZoneId,
    settings: AppSettings,
    events: List<Event>,
    tasks: List<Task>,
    countdowns: List<Countdown>,
): List<Reminder> {
    if (!settings.remindersOn) return emptyList()
    val end = Instant.ofEpochMilli(now).atZone(zone).plusDays(REMINDER_DAYS.toLong()).toInstant().toEpochMilli()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val lastDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
    val morning = settings.reminderMinute.coerceIn(0, 24 * 60 - 1)

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun morningOf(day: LocalDate): Long =
        day.atTime(LocalTime.of(morning / 60, morning % 60)).atZone(zone).toInstant().toEpochMilli()

    val found = mutableListOf<Reminder>()

    if (settings.remindCountdowns) {
        val before = settings.countdownDaysBefore.coerceAtLeast(0).toLong()
        for (countdown in countdowns) {
            if (!countdown.remind || countdown.countUp) continue
            val title = listOfNotNull(countdown.emoji?.takeIf { it.isNotBlank() }, countdown.title).joinToString(" ")
            for (day in days(today, lastDay.plusDays(before))) {
                if (!countdown.occursOn(day)) continue
                val milestone = countdown.milestone(day)
                found += Reminder("countdown-${countdown.id}-$day", morningOf(day), title, countdownText("Today", milestone))
                if (before > 0) {
                    val early = day.minusDays(before)
                    found += Reminder("countdown-${countdown.id}-$day-early", morningOf(early), title, countdownText(countdown.label(early), milestone))
                }
            }
        }
    }

    if (settings.remindTasks) {
        for (task in tasks) {
            val due = task.due ?: continue
            if (task.done || !task.remind) continue
            val day = dayOf(due)
            found += Reminder("task-${task.id}-$day", morningOf(day), task.title, "Due today")
        }
    }

    if (settings.remindEvents) {
        for (event in events) {
            val minutes = event.reminderMinutes ?: settings.eventReminderMinutes
            // all day events use the morning time, so only turning it off on the event stops them
            val off = if (event.allDay) event.reminderMinutes == NO_REMINDER else minutes < 0
            if (off) continue
            // a reminder a day or more ahead can belong to an event after the last day
            val extraDays = if (event.allDay) 0L else ((minutes + 24 * 60 - 1) / (24 * 60)).toLong()
            for (day in days(today, lastDay.plusDays(extraDays))) {
                if (!event.occursOn(day)) continue
                val moved = event.onDay(day)
                // a one off event over several days only reminds on its first
                if (dayOf(moved.startsAt) != day) continue
                val key = "event-${event.id}-$day"
                val place = event.location?.takeIf { it.isNotBlank() }
                if (event.allDay) {
                    found += Reminder(key, morningOf(day), event.title, listOfNotNull("All day today", place).joinToString(" · "))
                } else {
                    val at = moved.startsAt - minutes * 60_000L
                    val start = Instant.ofEpochMilli(moved.startsAt).atZone(zone)
                    val time = formatMinuteOfDay(start.hour * 60 + start.minute, settings.use24Hour)
                    val whenText = when (ChronoUnit.DAYS.between(dayOf(at), day)) {
                        0L -> "Starts at $time"
                        1L -> "Tomorrow at $time"
                        else -> "${day.format(shortDay)} at $time"
                    }
                    found += Reminder(key, at, event.title, listOfNotNull(whenText, place).joinToString(" · "))
                }
            }
        }
    }

    return found
        .filter { it.at > now && it.at <= end }
        .sortedWith(compareBy({ it.at }, { it.key }))
}
