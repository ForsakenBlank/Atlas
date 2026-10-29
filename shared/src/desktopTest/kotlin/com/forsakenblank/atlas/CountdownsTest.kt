package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.util.daysSince
import com.forsakenblank.atlas.util.daysUntil
import com.forsakenblank.atlas.util.label
import com.forsakenblank.atlas.util.milestone
import com.forsakenblank.atlas.util.nextDate
import com.forsakenblank.atlas.util.occursOn
import com.forsakenblank.atlas.util.ordinal
import com.forsakenblank.atlas.util.turning
import com.forsakenblank.atlas.util.upcoming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CountdownsTest {

    // a wednesday
    private val today = LocalDate.of(2026, 10, 7)

    private fun countdown(
        date: LocalDate,
        title: String = "Thing",
        kind: CountdownKind = CountdownKind.EVENT,
        yearly: Boolean = false,
        yearKnown: Boolean = true,
        countUp: Boolean = false,
    ) = Countdown(
        id = 0,
        title = title,
        date = date.toEpochDay(),
        kind = kind,
        yearly = yearly,
        yearKnown = yearKnown,
        countUp = countUp,
        created = 0L,
    )

    private fun birthday(date: LocalDate, yearKnown: Boolean = true, title: String = "Mum") =
        countdown(date, title = title, kind = CountdownKind.BIRTHDAY, yearly = true, yearKnown = yearKnown)

    @Test
    fun oneOffStaysOnItsDate() {
        val gig = countdown(LocalDate.of(2026, 10, 19))
        assertEquals(LocalDate.of(2026, 10, 19), gig.nextDate(today))
        assertEquals(12L, gig.daysUntil(today))
    }

    @Test
    fun yearlyRollsToNextYearOncePassed() {
        val mum = birthday(LocalDate.of(1975, 3, 14))
        assertEquals(LocalDate.of(2027, 3, 14), mum.nextDate(today))
        val later = birthday(LocalDate.of(1990, 12, 25))
        assertEquals(LocalDate.of(2026, 12, 25), later.nextDate(today))
    }

    @Test
    fun yearlyOnTheDayIsToday() {
        val mum = birthday(LocalDate.of(2007, 10, 7))
        assertEquals(today, mum.nextDate(today))
        assertEquals(0L, mum.daysUntil(today))
        assertEquals(LocalDate.of(2027, 10, 7), mum.nextDate(today.plusDays(1)))
    }

    @Test
    fun leapDayFallsOnThe28thInOtherYears() {
        val leap = birthday(LocalDate.of(2004, 2, 29))
        assertEquals(LocalDate.of(2027, 2, 28), leap.nextDate(today))
        assertEquals(LocalDate.of(2028, 2, 29), leap.nextDate(LocalDate.of(2027, 3, 1)))
        assertEquals(LocalDate.of(2027, 2, 28), leap.nextDate(LocalDate.of(2027, 2, 28)))
        assertTrue(leap.occursOn(LocalDate.of(2027, 2, 28)))
        assertFalse(leap.occursOn(LocalDate.of(2028, 2, 28)))
        assertTrue(leap.occursOn(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun yearlyWithAKnownYearStartsOnItsFirstDate() {
        val wedding = countdown(LocalDate.of(2027, 6, 5), kind = CountdownKind.ANNIVERSARY, yearly = true)
        assertEquals(LocalDate.of(2027, 6, 5), wedding.nextDate(today))
        assertFalse(wedding.occursOn(LocalDate.of(2026, 6, 5)))
        assertTrue(wedding.occursOn(LocalDate.of(2027, 6, 5)))
        assertTrue(wedding.occursOn(LocalDate.of(2030, 6, 5)))
        assertNull(wedding.milestone(today))
    }

    @Test
    fun unknownYearIgnoresTheStoredYear() {
        val friend = birthday(LocalDate.of(2027, 3, 1), yearKnown = false)
        assertEquals(LocalDate.of(2027, 3, 1), friend.nextDate(today))
        assertEquals(LocalDate.of(2026, 11, 2), birthday(LocalDate.of(2030, 11, 2), yearKnown = false).nextDate(today))
        assertTrue(friend.occursOn(LocalDate.of(2026, 3, 1)))
        assertNull(friend.turning(today))
    }

    @Test
    fun oneOffOnlyOccursOnItsDay() {
        val gig = countdown(LocalDate.of(2026, 10, 19))
        assertTrue(gig.occursOn(LocalDate.of(2026, 10, 19)))
        assertFalse(gig.occursOn(LocalDate.of(2027, 10, 19)))
        assertFalse(gig.occursOn(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun turningCountsTheNextBirthday() {
        assertEquals(19, birthday(LocalDate.of(2008, 3, 14)).turning(today))
        assertEquals(18, birthday(LocalDate.of(2008, 12, 1)).turning(today))
        assertEquals(19, birthday(LocalDate.of(2007, 10, 7)).turning(today))
        assertNull(countdown(LocalDate.of(2008, 3, 14), kind = CountdownKind.ANNIVERSARY, yearly = true).turning(today))
    }

    @Test
    fun milestoneReadsNaturally() {
        assertEquals("turns 19", birthday(LocalDate.of(2008, 3, 14)).milestone(today))
        val wedding = countdown(LocalDate.of(2021, 11, 20), kind = CountdownKind.ANNIVERSARY, yearly = true)
        assertEquals("5th anniversary", wedding.milestone(today))
        assertNull(countdown(LocalDate.of(2020, 12, 25), kind = CountdownKind.HOLIDAY, yearly = true).milestone(today))
    }

    @Test
    fun ordinalsHandleTheTeens() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111).map { ordinal(it) })
    }

    @Test
    fun countUpCountsDaysSince() {
        val quit = countdown(today.minusDays(142), countUp = true)
        assertEquals(142L, quit.daysSince(today))
        assertEquals("142 days since", quit.label(today))
        assertEquals("1 day since", countdown(today.minusDays(1), countUp = true).label(today))
        assertEquals("Today", countdown(today, countUp = true).label(today))
        // one that has not started yet counts down to its start
        assertEquals("in 3 days", countdown(today.plusDays(3), countUp = true).label(today))
    }

    @Test
    fun labelsForCloseDates() {
        assertEquals("Today", countdown(today).label(today))
        assertEquals("Tomorrow", countdown(today.plusDays(1)).label(today))
        assertEquals("in 12 days", countdown(today.plusDays(12)).label(today))
        assertEquals("in 59 days", countdown(today.plusDays(59)).label(today))
        assertEquals("Yesterday", countdown(today.minusDays(1)).label(today))
        assertEquals("2 days ago", countdown(today.minusDays(2)).label(today))
    }

    @Test
    fun labelsForFarDates() {
        assertEquals("in 2 months", countdown(today.plusDays(60)).label(today))
        assertEquals("in 3 months", countdown(today.plusDays(95)).label(today))
        assertEquals("in 12 months", countdown(today.plusDays(365)).label(today))
        assertEquals("in 2 years", countdown(today.plusDays(800)).label(today))
        assertEquals("4 months ago", countdown(today.minusDays(120)).label(today))
    }

    @Test
    fun upcomingIsSoonestFirstWithoutPassedOrCountUps() {
        val christmas = countdown(LocalDate.of(2020, 12, 25), title = "Christmas", kind = CountdownKind.HOLIDAY, yearly = true)
        val mum = birthday(LocalDate.of(1975, 3, 14))
        val gig = countdown(LocalDate.of(2026, 10, 19), title = "Gig")
        val gone = countdown(LocalDate.of(2026, 9, 1), title = "Gone")
        val quit = countdown(LocalDate.of(2026, 1, 1), title = "Quit", countUp = true)
        val todayOne = countdown(today, title = "Today one")
        val result = upcoming(listOf(mum, gone, christmas, quit, gig, todayOne), today)
        assertEquals(listOf("Today one", "Gig", "Christmas", "Mum"), result.map { it.title })
    }

    @Test
    fun upcomingBreaksTiesByTitle() {
        val b = countdown(today.plusDays(5), title = "banana")
        val a = countdown(today.plusDays(5), title = "Apple")
        assertEquals(listOf("Apple", "banana"), upcoming(listOf(b, a), today).map { it.title })
    }
}
