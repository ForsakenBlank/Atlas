package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.DayNote
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.Tracker
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.util.buildInsights
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.startOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class InsightsTest {

    // a wednesday, so this week has monday and tuesday behind it
    private val today = LocalDate.of(2026, 10, 28)

    private fun at(day: LocalDate) = day.startMillis() + 12 * 3_600_000L

    private val reading = TrackerWithItem(Item(id = 1, type = ItemType.TRACKER, name = "Reading"), Tracker(1, TrackerKind.TIMER))
    private val water = TrackerWithItem(Item(id = 2, type = ItemType.TRACKER, name = "Water"), Tracker(2, TrackerKind.COUNTER))

    @Test
    fun weeksStartOnMonday() {
        assertEquals(DayOfWeek.MONDAY, startOfWeek(today).dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 26), startOfWeek(today))
        assertEquals(LocalDate.of(2026, 10, 26), startOfWeek(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun trackersAreComparedWithLastWeek() {
        val logs = listOf(
            LogEntry(1, 1, at(today), 1800.0, 1800),
            LogEntry(2, 1, at(today.minusDays(7)), 3600.0, 3600),
            LogEntry(3, 2, at(today), 1.0),
            LogEntry(4, 2, at(today.minusDays(1)), 1.0),
        )
        val insights = buildInsights(today, listOf(reading, water), logs, emptyMap(), emptyList(), emptyList(), emptyList())

        val byName = insights.changes.associateBy { it.tracker.item.name }
        assertEquals(30.0, byName.getValue("Reading").thisWeek, 0.001)
        assertEquals(60.0, byName.getValue("Reading").lastWeek, 0.001)
        assertEquals(-50, byName.getValue("Reading").percent)
        assertEquals(2.0, byName.getValue("Water").thisWeek, 0.001)
        assertNull(byName.getValue("Water").percent)
    }

    @Test
    fun moneyLeavesTransfersOutAndTasksCountByWhenTheyWereDone() {
        val money = listOf(
            MoneyEntry(accountId = 1, day = today.toEpochDay(), amountPence = 50_00),
            MoneyEntry(accountId = 1, day = today.toEpochDay(), amountPence = -12_50),
            MoneyEntry(accountId = 1, day = today.toEpochDay(), amountPence = -100_00, transferId = 9),
            MoneyEntry(accountId = 1, day = today.minusMonths(1).toEpochDay(), amountPence = -3_00),
        )
        val tasks = listOf(
            Task(title = "Bins", done = true, doneAt = at(today)),
            Task(title = "Essay", done = true, doneAt = at(today.minusDays(8))),
            Task(title = "Dentist", due = today.minusDays(2).startMillis()),
        )
        val journal = listOf(DayNote(today.toEpochDay(), "good", mood = 4), DayNote(today.minusDays(1).toEpochDay(), "meh", mood = 2))

        val insights = buildInsights(today, emptyList(), emptyList(), emptyMap(), tasks, money, journal)

        assertEquals(50_00L, insights.moneyThisMonth.inPence)
        assertEquals(12_50L, insights.moneyThisMonth.outPence)
        assertEquals(3_00L, insights.moneyLastMonth.outPence)
        assertEquals(1, insights.tasksThisWeek)
        assertEquals(1, insights.tasksLastWeek)
        assertEquals(1, insights.overdue)
        assertEquals(3.0, insights.moodThisWeek!!, 0.001)
        assertEquals(2, insights.yearJournalDays)
    }
}
