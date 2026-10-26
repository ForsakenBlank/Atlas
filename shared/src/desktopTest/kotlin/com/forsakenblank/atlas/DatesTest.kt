package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.ui.calendar.CalendarData
import com.forsakenblank.atlas.util.classesOn
import com.forsakenblank.atlas.util.next
import com.forsakenblank.atlas.util.nowAndNext
import com.forsakenblank.atlas.util.occursOn
import com.forsakenblank.atlas.util.onDay
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.streaks
import com.forsakenblank.atlas.util.toLocalDate
import com.forsakenblank.atlas.util.weekOf
import com.forsakenblank.atlas.util.withWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DatesTest {

    private val today = LocalDate.of(2026, 10, 7) // a wednesday

    @Test
    fun streakCountsBackFromToday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        val result = streaks(days, today)
        assertEquals(3, result.current)
        assertEquals(3, result.best)
    }

    @Test
    fun emptyTodayDoesNotBreakTheStreakYet() {
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, streaks(days, today).current)
    }

    @Test
    fun bestStreakCanBeInThePast() {
        val days = (10L..14L).map { today.minusDays(it) }.toSet() + today
        val result = streaks(days, today)
        assertEquals(1, result.current)
        assertEquals(5, result.best)
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val friday = LocalDate.of(2026, 10, 9)
        assertEquals(LocalDate.of(2026, 10, 12), Repeat.WEEKDAYS.next(friday))
        assertFalse(Repeat.WEEKDAYS.occursOn(today, LocalDate.of(2026, 10, 10)))
        assertTrue(Repeat.WEEKDAYS.occursOn(today, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun fortnightlyOnlyHitsEveryOtherWeek() {
        assertTrue(Repeat.FORTNIGHTLY.occursOn(today, today.plusWeeks(2)))
        assertFalse(Repeat.FORTNIGHTLY.occursOn(today, today.plusWeeks(1)))
        assertFalse(Repeat.FORTNIGHTLY.occursOn(today, today.minusWeeks(2)))
    }

    @Test
    fun repeatsStopAtTheUntilDate() {
        val until = today.plusDays(3)
        assertTrue(Repeat.DAILY.occursOn(today, today.plusDays(3), until))
        assertFalse(Repeat.DAILY.occursOn(today, today.plusDays(4), until))
    }

    @Test
    fun monthlyAndYearlyKeepTheDate() {
        assertEquals(LocalDate.of(2026, 11, 7), Repeat.MONTHLY.next(today))
        assertTrue(Repeat.YEARLY.occursOn(today, LocalDate.of(2027, 10, 7)))
        assertFalse(Repeat.YEARLY.occursOn(today, LocalDate.of(2027, 10, 8)))
    }

    @Test
    fun oneOffEventsCoverEveryDayTheySpan() {
        val start = today.atTime(LocalTime.of(22, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = today.plusDays(1).atTime(LocalTime.of(2, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val event = Event(title = "Late one", startsAt = start, endsAt = end)
        assertTrue(event.occursOn(today))
        assertTrue(event.occursOn(today.plusDays(1)))
        assertFalse(event.occursOn(today.plusDays(2)))
    }

    @Test
    fun repeatingEventsMoveOntoTheDayWithTheSameTimes() {
        val start = today.atTime(LocalTime.of(9, 30)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val event = Event(title = "Standup", startsAt = start, endsAt = start + 15 * 60_000, repeatRule = Repeat.WEEKLY)
        val later = today.plusWeeks(6)
        assertTrue(event.occursOn(later))
        val moved = event.onDay(later)
        assertEquals(later, moved.startsAt.toLocalDate())
        assertEquals(15 * 60_000L, moved.endsAt - moved.startsAt)
        assertEquals(later.atTime(LocalTime.of(9, 30)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), moved.startsAt)
    }

    @Test
    fun weekAAndBAlternate() {
        val settings = AppSettings(twoWeekTimetable = true).withWeek(today, 1)
        assertEquals(1, settings.weekOf(today))
        assertEquals(2, settings.weekOf(today.plusWeeks(1)))
        assertEquals(1, settings.weekOf(today.plusWeeks(2)))
        assertEquals(2, settings.weekOf(today.minusWeeks(1)))
        assertEquals(2, settings.withWeek(today, 2).weekOf(today))
        assertEquals(0, AppSettings().weekOf(today))
    }

    @Test
    fun classesFollowTheDayWeekAndTerm() {
        val maths = Subject(id = 1, name = "Maths")
        val slots = listOf(
            TimetableSlot(id = 1, subjectId = 1, dayOfWeek = 3, startMinute = 600, endMinute = 660, week = 0),
            TimetableSlot(id = 2, subjectId = 1, dayOfWeek = 3, startMinute = 540, endMinute = 600, week = 2),
            TimetableSlot(id = 3, subjectId = 1, dayOfWeek = 4, startMinute = 540, endMinute = 600, week = 0),
        )
        val settings = AppSettings(twoWeekTimetable = true).withWeek(today, 1)
        val wednesday = classesOn(today, slots, listOf(maths), emptyList(), settings)
        assertEquals(listOf(1L), wednesday.map { it.slot.id })

        val weekB = classesOn(today.plusWeeks(1), slots, listOf(maths), emptyList(), settings)
        assertEquals(listOf(2L, 1L), weekB.map { it.slot.id })

        val term = Term(name = "Autumn", startDay = today.plusDays(1).toEpochDay(), endDay = today.plusDays(60).toEpochDay())
        assertTrue(classesOn(today, slots, listOf(maths), listOf(term), settings).isEmpty())

        val upNext = nowAndNext(weekB, 590)
        assertEquals(2L, upNext.now?.slot?.id)
        assertEquals(1L, upNext.next?.slot?.id)
    }

    @Test
    fun yearlyAllDayEventsComeBackEachYear() {
        val birthday = Event(
            title = "Birthday",
            startsAt = today.startMillis(),
            endsAt = today.plusDays(1).startMillis(),
            allDay = true,
            repeatRule = Repeat.YEARLY,
        )
        val data = CalendarData(events = listOf(birthday))
        assertEquals(1, data.eventsOn(today.plusYears(1)).size)
        assertEquals(today.plusYears(3), data.eventsOn(today.plusYears(3)).single().startsAt.toLocalDate())
        assertTrue(data.eventsOn(today.plusYears(1).plusDays(1)).isEmpty())
    }

    @Test
    fun repeatingTasksPreviewTheirLaterDates() {
        val task = Task(id = 1, title = "Mum's birthday", due = today.startMillis(), repeatRule = Repeat.YEARLY)
        val data = CalendarData(tasks = listOf(task))
        assertEquals(listOf(task), data.tasksOn(today))
        assertTrue(data.repeatsOn(today).isEmpty())
        assertEquals(listOf(task), data.repeatsOn(today.plusYears(1)))
        assertTrue(data.repeatsOn(today.plusDays(1)).isEmpty())
        assertTrue(CalendarData(tasks = listOf(task.copy(done = true))).repeatsOn(today.plusYears(1)).isEmpty())
    }

    @Test
    fun dayStartRoundTrips() {
        assertEquals(today, today.startMillis().toLocalDate())
    }
}
