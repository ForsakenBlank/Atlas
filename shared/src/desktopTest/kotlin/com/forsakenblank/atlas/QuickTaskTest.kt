package com.forsakenblank.atlas

import com.forsakenblank.atlas.ui.tasks.QuickTask
import com.forsakenblank.atlas.ui.tasks.parseQuickTask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QuickTaskTest {

    // a wednesday
    private val today = LocalDate.of(2026, 10, 7)
    private val friday = LocalDate.of(2026, 10, 9)

    @Test
    fun plainTextHasNoHints() {
        assertEquals(QuickTask("Buy milk", null, 0), parseQuickTask("Buy milk", today))
    }

    @Test
    fun todayAndTomorrowSetTheDueDate() {
        assertEquals(QuickTask("Essay", today, 0), parseQuickTask("Essay today", today))
        assertEquals(QuickTask("Essay", today.plusDays(1), 0), parseQuickTask("Essay tomorrow", today))
        assertEquals(QuickTask("Essay", today.plusDays(1), 0), parseQuickTask("Essay tmr", today))
    }

    @Test
    fun weekdayMeansTheNextOneAfterToday() {
        assertEquals(QuickTask("Essay", friday, 0), parseQuickTask("Essay fri", today))
        assertEquals(QuickTask("Essay", friday, 0), parseQuickTask("Essay friday", today))
        assertEquals(LocalDate.of(2026, 10, 12), parseQuickTask("Essay mon", today).due)
        assertEquals(LocalDate.of(2026, 10, 8), parseQuickTask("Essay thurs", today).due)
    }

    @Test
    fun todaysWeekdayMeansNextWeek() {
        assertEquals(LocalDate.of(2026, 10, 14), parseQuickTask("Essay wednesday", today).due)
        assertEquals(LocalDate.of(2026, 10, 14), parseQuickTask("Essay wed", today).due)
    }

    @Test
    fun hintsIgnoreCase() {
        assertEquals(QuickTask("Essay", today.plusDays(1), 0), parseQuickTask("Essay TOMORROW", today))
        assertEquals(QuickTask("Essay", friday, 0), parseQuickTask("Essay Fri", today))
    }

    @Test
    fun exclamationMarksSetPriority() {
        assertEquals(QuickTask("Revise", null, 1), parseQuickTask("Revise !", today))
        assertEquals(QuickTask("Revise", null, 2), parseQuickTask("Revise !!", today))
        assertEquals(QuickTask("Revise", null, 3), parseQuickTask("Revise !!!", today))
    }

    @Test
    fun moreThanThreeMarksIsStillHigh() {
        assertEquals(QuickTask("Revise", null, 3), parseQuickTask("Revise !!!!", today))
    }

    @Test
    fun marksStuckToTheLastWordCount() {
        assertEquals(QuickTask("Call mum", null, 1), parseQuickTask("Call mum!", today))
        assertEquals(QuickTask("Essay", friday, 2), parseQuickTask("Essay fri!!", today))
    }

    @Test
    fun hintsCanComeInEitherOrder() {
        val expected = QuickTask("Lab report", friday, 2)
        assertEquals(expected, parseQuickTask("Lab report fri !!", today))
        assertEquals(expected, parseQuickTask("Lab report !! fri", today))
    }

    @Test
    fun hintsInTheMiddleAreLeftAlone() {
        assertEquals(QuickTask("Fri night plans", null, 0), parseQuickTask("Fri night plans", today))
        assertEquals(QuickTask("Tidy today please", null, 0), parseQuickTask("Tidy today please", today))
    }

    @Test
    fun onlyTheLastDateCounts() {
        assertEquals(QuickTask("Essay fri", today.plusDays(1), 0), parseQuickTask("Essay fri tomorrow", today))
    }

    @Test
    fun aHintOnItsOwnStaysAsTheTitle() {
        assertEquals(QuickTask("tomorrow", null, 0), parseQuickTask("tomorrow", today))
        assertEquals(QuickTask("!!", null, 0), parseQuickTask("!!", today))
        assertEquals(QuickTask("fri", null, 2), parseQuickTask("fri !!", today))
    }

    @Test
    fun extraSpacesAreTidied() {
        assertEquals(QuickTask("Essay plan", friday, 0), parseQuickTask("  Essay   plan  fri  ", today))
    }
}
