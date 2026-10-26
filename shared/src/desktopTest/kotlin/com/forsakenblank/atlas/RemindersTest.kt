package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.util.NO_REMINDER
import com.forsakenblank.atlas.util.Reminder
import com.forsakenblank.atlas.util.planReminders
import com.forsakenblank.atlas.util.reminderMinutesLabel
import com.forsakenblank.atlas.util.startMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RemindersTest {

    private val zone = ZoneId.systemDefault()

    // a wednesday, planned from seven in the morning
    private val today = LocalDate.of(2026, 10, 7)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 7)

    private fun plan(
        settings: AppSettings = AppSettings(),
        events: List<Event> = emptyList(),
        tasks: List<Task> = emptyList(),
        countdowns: List<Countdown> = emptyList(),
        from: Long = now,
    ): List<Reminder> = planReminders(from, zone, settings, events, tasks, countdowns)

    private fun task(id: Long, due: LocalDate?, done: Boolean = false, remind: Boolean = true) =
        Task(id = id, title = "Task $id", due = due?.startMillis(), done = done, remind = remind)

    private fun timed(
        id: Long,
        start: Long,
        minutes: Int? = null,
        repeat: Repeat = Repeat.NONE,
        until: LocalDate? = null,
        location: String? = null,
    ) = Event(
        id = id,
        title = "Event $id",
        startsAt = start,
        endsAt = start + 60 * 60_000,
        location = location,
        repeatRule = repeat,
        repeatUntil = until?.startMillis(),
        reminderMinutes = minutes,
    )

    private fun allDay(id: Long, first: LocalDate, last: LocalDate = first, minutes: Int? = null, location: String? = null) = Event(
        id = id,
        title = "Day $id",
        startsAt = first.startMillis(),
        endsAt = last.plusDays(1).startMillis(),
        allDay = true,
        location = location,
        reminderMinutes = minutes,
    )

    private fun countdown(
        id: Long,
        date: LocalDate,
        kind: CountdownKind = CountdownKind.EVENT,
        yearly: Boolean = false,
        countUp: Boolean = false,
        emoji: String? = null,
        remind: Boolean = true,
    ) = Countdown(
        id = id,
        title = "Countdown $id",
        date = date.toEpochDay(),
        kind = kind,
        yearly = yearly,
        yearKnown = true,
        countUp = countUp,
        emoji = emoji,
        created = 0L,
        remind = remind,
    )

    @Test
    fun tasksRemindOnTheMorningTheyAreDue() {
        val tasks = listOf(
            task(1, today),
            task(2, today.plusDays(3)),
            task(3, today, done = true),
            task(4, today, remind = false),
            task(5, null),
            task(6, today.minusDays(1)),
            task(7, today.plusDays(20)),
        )
        val planned = plan(tasks = tasks)
        assertEquals(listOf(at(today, 8), at(today.plusDays(3), 8)), planned.map { it.at })
        assertEquals("Task 1", planned[0].title)
        assertEquals("Due today", planned[0].text)
    }

    @Test
    fun theMorningTimeComesFromSettings() {
        val planned = plan(settings = AppSettings(reminderMinute = 9 * 60 + 30), tasks = listOf(task(1, today)))
        assertEquals(listOf(at(today, 9, 30)), planned.map { it.at })
    }

    @Test
    fun remindersAlreadyGoneAreSkipped() {
        val planned = plan(tasks = listOf(task(1, today), task(2, today.plusDays(1))), from = at(today, 9))
        assertEquals(listOf(at(today.plusDays(1), 8)), planned.map { it.at })
    }

    @Test
    fun birthdaysRemindOnTheDayAndTheDayBefore() {
        val mum = countdown(1, LocalDate.of(1996, 10, 9), CountdownKind.BIRTHDAY, yearly = true, emoji = "🎂")
        val planned = plan(countdowns = listOf(mum))
        assertEquals(listOf(at(today.plusDays(1), 8), at(today.plusDays(2), 8)), planned.map { it.at })
        assertEquals(listOf("Turns 30 tomorrow", "Turns 30 today"), planned.map { it.text })
        assertEquals("🎂 Countdown 1", planned[0].title)
    }

    @Test
    fun anniversariesSayWhichOne() {
        val wedding = countdown(1, LocalDate.of(2021, 10, 8), CountdownKind.ANNIVERSARY, yearly = true)
        val planned = plan(settings = AppSettings(countdownDaysBefore = 0), countdowns = listOf(wedding))
        assertEquals(listOf("5th anniversary today"), planned.map { it.text })
    }

    @Test
    fun daysBeforeCanBeMoreOrNone() {
        val gig = countdown(1, today.plusDays(3))
        val early = plan(settings = AppSettings(countdownDaysBefore = 2), countdowns = listOf(gig))
        assertEquals(listOf(at(today.plusDays(1), 8), at(today.plusDays(3), 8)), early.map { it.at })
        assertEquals(listOf("In 2 days", "Today"), early.map { it.text })

        val onTheDay = plan(settings = AppSettings(countdownDaysBefore = 0), countdowns = listOf(gig))
        assertEquals(listOf(at(today.plusDays(3), 8)), onTheDay.map { it.at })
    }

    @Test
    fun anEarlyReminderCanBeForADayAfterTheWindow() {
        // the day itself is past the last day, the week before is not
        val trip = countdown(1, today.plusDays(10))
        val planned = plan(settings = AppSettings(countdownDaysBefore = 7), countdowns = listOf(trip))
        assertEquals(listOf(at(today.plusDays(3), 8)), planned.map { it.at })
        assertEquals("In 7 days", planned.single().text)
    }

    @Test
    fun countUpsAndMutedCountdownsStayQuiet() {
        val countdowns = listOf(
            countdown(1, today.plusDays(1), countUp = true),
            countdown(2, today.plusDays(1), remind = false),
        )
        assertTrue(plan(countdowns = countdowns).isEmpty())
    }

    @Test
    fun timedEventsUseTheirOwnMinutesOrTheDefault() {
        val start = at(today, 14, 30)
        val events = listOf(
            timed(1, start),
            timed(2, start, minutes = 60, location = "Room 4"),
            timed(3, start, minutes = NO_REMINDER),
        )
        val planned = plan(events = events)
        assertEquals(listOf(at(today, 13, 30), at(today, 14, 15)), planned.map { it.at })
        assertEquals(listOf("Starts at 14:30 · Room 4", "Starts at 14:30"), planned.map { it.text })

        val noDefault = plan(settings = AppSettings(eventReminderMinutes = NO_REMINDER), events = events)
        assertEquals(listOf("event-2-$today"), noDefault.map { it.key })
    }

    @Test
    fun aDayAheadReachesPastTheLastDay() {
        // the window ends at seven on the 15th, this event is the morning after
        val early = timed(1, at(today.plusDays(9), 6), minutes = 24 * 60)
        val planned = plan(events = listOf(early))
        assertEquals(listOf(at(today.plusDays(8), 6)), planned.map { it.at })
        assertEquals("Tomorrow at 06:00", planned.single().text)

        // further ahead than a day names the day
        val sunday = today.plusDays(4)
        val twoDays = plan(events = listOf(timed(2, at(sunday, 10), minutes = 2 * 24 * 60)))
        assertEquals(listOf(at(today.plusDays(2), 10)), twoDays.map { it.at })
        assertEquals("${sunday.format(DateTimeFormatter.ofPattern("EEE d MMM"))} at 10:00", twoDays.single().text)
    }

    @Test
    fun allDayEventsUseTheMorningTime() {
        val events = listOf(
            allDay(1, today.plusDays(1), location = "Spain"),
            allDay(2, today.plusDays(2), today.plusDays(4)),
            allDay(3, today.plusDays(1), minutes = NO_REMINDER),
        )
        val planned = plan(settings = AppSettings(eventReminderMinutes = NO_REMINDER), events = events)
        assertEquals(listOf(at(today.plusDays(1), 8), at(today.plusDays(2), 8)), planned.map { it.at })
        assertEquals(listOf("All day today · Spain", "All day today"), planned.map { it.text })
    }

    @Test
    fun repeatingEventsRemindEveryTimeTheyHappen() {
        val weekly = timed(1, at(today.minusWeeks(3), 9, 30), minutes = 10, repeat = Repeat.WEEKLY)
        assertEquals(listOf(at(today, 9, 20), at(today.plusWeeks(1), 9, 20)), plan(events = listOf(weekly)).map { it.at })

        val daily = timed(2, at(today.minusDays(30), 18), repeat = Repeat.DAILY, until = today.plusDays(2))
        val planned = plan(events = listOf(daily))
        assertEquals((0L..2L).map { at(today.plusDays(it), 17, 45) }, planned.map { it.at })
        assertEquals((0L..2L).map { "event-2-${today.plusDays(it)}" }, planned.map { it.key })
    }

    @Test
    fun switchesTurnEachKindOff() {
        val tasks = listOf(task(1, today.plusDays(1)))
        val events = listOf(timed(1, at(today, 12)))
        val countdowns = listOf(countdown(1, today.plusDays(2)))
        assertEquals(4, plan(events = events, tasks = tasks, countdowns = countdowns).size)
        assertTrue(plan(AppSettings(remindersOn = false), events, tasks, countdowns).isEmpty())
        assertTrue(plan(AppSettings(remindTasks = false), events, tasks, countdowns).none { it.key.startsWith("task") })
        assertTrue(plan(AppSettings(remindEvents = false), events, tasks, countdowns).none { it.key.startsWith("event") })
        assertTrue(plan(AppSettings(remindCountdowns = false), events, tasks, countdowns).none { it.key.startsWith("countdown") })
    }

    @Test
    fun everythingComesBackInTimeOrderWithSteadyKeys() {
        val tasks = listOf(task(1, today.plusDays(1)))
        val events = listOf(timed(1, at(today, 12)), timed(2, at(today.plusDays(1), 7, 30)))
        val countdowns = listOf(countdown(1, today.plusDays(1)))
        val first = plan(events = events, tasks = tasks, countdowns = countdowns)
        assertEquals(first.sortedBy { it.at }, first)
        val tomorrow = today.plusDays(1)
        assertEquals(
            listOf("countdown-1-$tomorrow-early", "event-1-$today", "event-2-$tomorrow", "countdown-1-$tomorrow", "task-1-$tomorrow"),
            first.map { it.key },
        )
        // an hour later the same reminders keep their keys
        val later = plan(events = events, tasks = tasks, countdowns = countdowns, from = now + 60 * 60_000)
        assertEquals(first.drop(1).map { it.key to it.at }, later.map { it.key to it.at })
    }

    @Test
    fun minuteLabelsReadNaturally() {
        assertEquals("None", reminderMinutesLabel(NO_REMINDER))
        assertEquals("5 minutes before", reminderMinutesLabel(5))
        assertEquals("1 hour before", reminderMinutesLabel(60))
        assertEquals("1 day before", reminderMinutesLabel(24 * 60))
    }
}
