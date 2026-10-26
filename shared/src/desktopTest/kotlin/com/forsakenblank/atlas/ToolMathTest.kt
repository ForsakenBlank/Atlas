package com.forsakenblank.atlas

import com.forsakenblank.atlas.util.BillSplit
import com.forsakenblank.atlas.util.DiceSpec
import com.forsakenblank.atlas.util.Odds
import com.forsakenblank.atlas.util.UnitCategory
import com.forsakenblank.atlas.util.addPercent
import com.forsakenblank.atlas.util.chanceFromOneIn
import com.forsakenblank.atlas.util.chanceFromPercent
import com.forsakenblank.atlas.util.chanceOfAll
import com.forsakenblank.atlas.util.chanceOfAtLeastOne
import com.forsakenblank.atlas.util.convertTemperature
import com.forsakenblank.atlas.util.convertUnit
import com.forsakenblank.atlas.util.expectedSuccesses
import com.forsakenblank.atlas.util.formatChance
import com.forsakenblank.atlas.util.formatMoney
import com.forsakenblank.atlas.util.formatStopwatch
import com.forsakenblank.atlas.util.niceNumber
import com.forsakenblank.atlas.util.parseDice
import com.forsakenblank.atlas.util.parseNumberInput
import com.forsakenblank.atlas.util.parseWholeInput
import com.forsakenblank.atlas.util.percentChange
import com.forsakenblank.atlas.util.percentOf
import com.forsakenblank.atlas.util.pickNumbers
import com.forsakenblank.atlas.util.rollDice
import com.forsakenblank.atlas.util.splitBill
import com.forsakenblank.atlas.util.splitIntoTeams
import com.forsakenblank.atlas.util.subtractPercent
import com.forsakenblank.atlas.util.triesForChance
import com.forsakenblank.atlas.util.whatPercent
import com.forsakenblank.atlas.util.workingDaysBetween
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolMathTest {

    private val delta = 1e-6

    @Test
    fun numbersParseWithDotsOrCommas() {
        assertEquals(3.5, parseNumberInput("3.5")!!, delta)
        assertEquals(3.5, parseNumberInput(" 3,5 ")!!, delta)
        assertEquals(-12.0, parseNumberInput("-12")!!, delta)
        assertNull(parseNumberInput(""))
        assertNull(parseNumberInput("abc"))
        assertNull(parseNumberInput("NaN"))
        assertEquals(42L, parseWholeInput("42"))
        assertNull(parseWholeInput("4.2"))
    }

    @Test
    fun niceNumbersDropTrailingZeros() {
        assertEquals("2.5", niceNumber(2.50))
        assertEquals("3", niceNumber(3.0))
        assertEquals("0.3", niceNumber(0.1 + 0.2))
        assertEquals("1234.57", niceNumber(1234.56789, 2))
        assertEquals("0", niceNumber(-0.0))
        assertEquals("-7.25", niceNumber(-7.25))
        assertEquals("0.00001235", niceNumber(0.000012345))
        assertEquals("-", niceNumber(Double.NaN))
        assertEquals("1000000", niceNumber(1_000_000.0))
    }

    @Test
    fun moneyHasTwoPlacesAndCommas() {
        assertEquals("£12.50", formatMoney(12.5, "£"))
        assertEquals("\$1,234,567.89", formatMoney(1234567.891, "\$"))
        assertEquals("-€3.00", formatMoney(-3.0, "€"))
        assertEquals("£0.00", formatMoney(-0.001, "£"))
    }

    @Test
    fun chancesShowAsPercentages() {
        assertEquals("50%", formatChance(0.5))
        assertEquals("0.0977%", formatChance(0.000977))
        assertEquals("over 99.99%", formatChance(0.999999))
        assertEquals("100%", formatChance(1.0))
        assertEquals("0%", formatChance(0.0))
    }

    @Test
    fun stopwatchFormat() {
        assertEquals("00:00.00", formatStopwatch(0))
        assertEquals("02:03.45", formatStopwatch(123_450))
        assertEquals("1:02:03.45", formatStopwatch(3_723_450))
    }

    @Test
    fun fractionalOddsToEverythingElse() {
        val decimal = Odds.parseFractional("5/2")!!
        assertEquals(3.5, decimal, delta)
        assertEquals(250.0, Odds.toAmerican(decimal)!!, delta)
        assertEquals(28.571428, Odds.toPercent(decimal)!!, 1e-4)
        assertEquals(2.0, Odds.parseFractional("evens")!!, delta)
        assertEquals(5.0, Odds.parseFractional("4")!!, delta)
        assertNull(Odds.parseFractional("5/0"))
        assertNull(Odds.parseFractional("five to two"))
        assertNull(Odds.parseFractional("1/2/3"))
    }

    @Test
    fun decimalOddsToFractions() {
        assertEquals(Odds.Fraction(5, 2), Odds.toFractional(3.5))
        assertEquals(Odds.Fraction(10, 11), Odds.toFractional(1.91))
        assertEquals(Odds.Fraction(1, 3), Odds.toFractional(1.333))
        assertEquals(Odds.Fraction(1, 1), Odds.toFractional(2.0))
        assertEquals(Odds.Fraction(1, 100), Odds.toFractional(1.01))
        assertEquals("5/2", Odds.toFractional(3.5).toString())
        assertNull(Odds.toFractional(0.5))
    }

    @Test
    fun americanOddsBothWays() {
        assertEquals(3.5, Odds.fromAmerican(250.0)!!, delta)
        assertEquals(1.666666, Odds.fromAmerican(-150.0)!!, 1e-5)
        assertEquals(2.0, Odds.fromAmerican(100.0)!!, delta)
        assertEquals(2.0, Odds.fromAmerican(-100.0)!!, delta)
        assertNull(Odds.fromAmerican(50.0))
        assertEquals(-150.0, Odds.toAmerican(1.0 + 100.0 / 150)!!, delta)
        assertEquals(100.0, Odds.toAmerican(2.0)!!, delta)
        assertNull(Odds.toAmerican(1.0))
        assertEquals("+250", Odds.formatAmerican(250.0))
        assertEquals("-110", Odds.formatAmerican(-110.0))
    }

    @Test
    fun probabilityOddsBothWays() {
        assertEquals(4.0, Odds.fromPercent(25.0)!!, delta)
        assertEquals(1.0, Odds.fromPercent(100.0)!!, delta)
        assertNull(Odds.fromPercent(0.0))
        assertNull(Odds.fromPercent(120.0))
        assertEquals(25.0, Odds.toPercent(4.0)!!, delta)
        assertNull(Odds.toPercent(0.5))
        assertEquals(4.0, Odds.fromFractional(3.0, 1.0)!!, delta)
        assertNull(Odds.fromFractional(-1.0, 2.0))
    }

    @Test
    fun chanceOverManyTries() {
        assertEquals(0.75, chanceOfAtLeastOne(0.5, 2), delta)
        assertEquals(1 - Math.pow(5.0 / 6, 10.0), chanceOfAtLeastOne(1.0 / 6, 10), delta)
        assertEquals(0.0, chanceOfAtLeastOne(0.3, 0), delta)
        assertEquals(1.0, chanceOfAtLeastOne(1.0, 3), delta)
        assertEquals(1e-12 * 3, chanceOfAtLeastOne(1e-12, 3), 1e-20)
        assertEquals(0.125, chanceOfAll(0.5, 3), delta)
        assertEquals(1.0 / 7776, chanceOfAll(1.0 / 6, 5), delta)
        assertEquals(2.5, expectedSuccesses(0.25, 10), delta)
        assertEquals(0.25, chanceFromPercent(25.0)!!, delta)
        assertNull(chanceFromPercent(101.0))
        assertEquals(0.05, chanceFromOneIn(20.0)!!, delta)
        assertNull(chanceFromOneIn(0.5))
    }

    @Test
    fun triesNeededForATarget() {
        assertEquals(1L, triesForChance(0.5, 0.5))
        assertEquals(2L, triesForChance(0.5, 0.75))
        assertEquals(4L, triesForChance(1.0 / 6, 0.5))
        assertEquals(13L, triesForChance(1.0 / 6, 0.9))
        assertEquals(26L, triesForChance(1.0 / 6, 0.99))
        assertEquals(69L, triesForChance(0.01, 0.5))
        assertEquals(1L, triesForChance(1.0, 0.99))
        assertEquals(0L, triesForChance(0.2, 0.0))
        assertNull(triesForChance(0.0, 0.5))
        assertNull(triesForChance(0.5, 1.0))
    }

    @Test
    fun percentageHelpers() {
        assertEquals(30.0, percentOf(15.0, 200.0), delta)
        assertEquals(25.0, whatPercent(50.0, 200.0)!!, delta)
        assertNull(whatPercent(5.0, 0.0))
        assertEquals(25.0, percentChange(80.0, 100.0)!!, delta)
        assertEquals(-20.0, percentChange(100.0, 80.0)!!, delta)
        assertEquals(50.0, percentChange(-100.0, -50.0)!!, delta)
        assertNull(percentChange(0.0, 10.0))
        assertEquals(120.0, addPercent(100.0, 20.0), delta)
        assertEquals(80.0, subtractPercent(100.0, 20.0), delta)
    }

    @Test
    fun billSplits() {
        val plain = splitBill(100.0, 10.0, 4)!!
        assertEquals(10.0, plain.tip, delta)
        assertEquals(110.0, plain.total, delta)
        assertEquals(27.5, plain.share, delta)

        val rounded = splitBill(100.0, 12.5, 3, roundUp = true)!!
        assertEquals(38.0, rounded.share, delta)
        assertEquals(114.0, rounded.total, delta)
        assertEquals(14.0, rounded.tip, delta)

        val exact = splitBill(90.0, 0.0, 3, roundUp = true)!!
        assertEquals(BillSplit(0.0, 90.0, 30.0), exact)

        assertNull(splitBill(50.0, 10.0, 0))
        assertNull(splitBill(-5.0, 10.0, 2))
    }

    @Test
    fun unitConversions() {
        val length = UnitCategory.LENGTH
        assertEquals(0.621371, convertUnit(length, 1.0, length.unit("km"), length.unit("mi")), 1e-6)
        assertEquals(2.54, convertUnit(length, 1.0, length.unit("in"), length.unit("cm")), delta)
        assertEquals(1.0, convertUnit(length, 12.0, length.unit("in"), length.unit("ft")), delta)

        val weight = UnitCategory.WEIGHT
        assertEquals(2.204623, convertUnit(weight, 1.0, weight.unit("kg"), weight.unit("lb")), 1e-6)
        assertEquals(14.0, convertUnit(weight, 1.0, weight.unit("st"), weight.unit("lb")), delta)

        val volume = UnitCategory.VOLUME
        assertEquals(8.0, convertUnit(volume, 1.0, volume.unit("gal (UK)"), volume.unit("pt (UK)")), delta)

        val speed = UnitCategory.SPEED
        assertEquals(100.0, convertUnit(speed, 62.137119, speed.unit("mph"), speed.unit("km/h")), 1e-4)

        val area = UnitCategory.AREA
        assertEquals(640.0, convertUnit(area, 1.0, area.unit("mi²"), area.unit("ac")), delta)

        val data = UnitCategory.DATA
        assertEquals(1024.0, convertUnit(data, 1.0, data.unit("MiB"), data.unit("KiB")), delta)
        assertEquals(8.0, convertUnit(data, 1.0, data.unit("B"), data.unit("bit")), delta)
    }

    @Test
    fun temperatures() {
        assertEquals(212.0, convertTemperature(100.0, "°C", "°F"), delta)
        assertEquals(-40.0, convertTemperature(-40.0, "°F", "°C"), delta)
        assertEquals(-273.15, convertTemperature(0.0, "K", "°C"), delta)
        assertEquals(273.15, convertTemperature(32.0, "°F", "K"), delta)
        val temperature = UnitCategory.TEMPERATURE
        assertEquals(37.0, convertUnit(temperature, 98.6, temperature.unit("°F"), temperature.unit("°C")), delta)
    }

    @Test
    fun defaultUnitPairsFollowTheSetting() {
        val (metricFrom, metricTo) = UnitCategory.LENGTH.defaultPair(metric = true)
        assertEquals("km", metricFrom.symbol)
        assertEquals("mi", metricTo.symbol)
        val (imperialFrom, imperialTo) = UnitCategory.WEIGHT.defaultPair(metric = false)
        assertEquals("lb", imperialFrom.symbol)
        assertEquals("kg", imperialTo.symbol)
        UnitCategory.entries.forEach { category ->
            val symbols = category.units.map { it.symbol }
            assertEquals("${category.label} has repeated symbols", symbols.size, symbols.toSet().size)
        }
    }

    @Test
    fun diceNotation() {
        assertEquals(DiceSpec(3, 6), parseDice("3d6"))
        assertEquals(DiceSpec(1, 20), parseDice("d20"))
        assertEquals(DiceSpec(2, 8, 3), parseDice("2d8+3"))
        assertEquals(DiceSpec(4, 6, -1), parseDice("4d6-1"))
        assertEquals(DiceSpec(2, 10, 5), parseDice(" 2 D 10 + 5 "))
        assertEquals("2d8+3", DiceSpec(2, 8, 3).toString())
        assertEquals("4d6-1", DiceSpec(4, 6, -1).toString())
        assertNull(parseDice(""))
        assertNull(parseDice("hello"))
        assertNull(parseDice("d"))
        assertNull(parseDice("3d"))
        assertNull(parseDice("0d6"))
        assertNull(parseDice("2d1"))
        assertNull(parseDice("2d6+"))
        assertNull(parseDice("2d6+3+4"))
        assertNull(parseDice("1000d6"))
    }

    @Test
    fun diceRollsStayInRange() {
        val random = Random(7)
        repeat(200) {
            val roll = rollDice(DiceSpec(3, 6, 2), random)
            assertEquals(3, roll.faces.size)
            assertTrue(roll.faces.all { it in 1..6 })
            assertEquals(roll.faces.sum() + 2, roll.total)
        }
    }

    @Test
    fun randomNumbers() {
        val random = Random(1)
        val unique = pickNumbers(1, 10, 10, noRepeats = true, random = random)!!
        assertEquals((1L..10L).toList(), unique.sorted())
        val many = pickNumbers(-5, 5, 300, noRepeats = false, random = random)!!
        assertTrue(many.all { it in -5L..5L })
        val wide = pickNumbers(1, 1_000_000, 50, noRepeats = true, random = random)!!
        assertEquals(50, wide.toSet().size)
        assertNull(pickNumbers(1, 5, 6, noRepeats = true, random = random))
        assertNull(pickNumbers(10, 1, 1, noRepeats = false, random = random))
    }

    @Test
    fun teamsAreBalanced() {
        val teams = splitIntoTeams(listOf("a", "b", "c", "d", "e", "f", "g"), 3)
        assertEquals(listOf(3, 2, 2), teams.map { it.size })
        assertEquals(7, teams.flatten().toSet().size)
        assertEquals(listOf(2, 2), splitIntoTeams(listOf(1, 2, 3, 4), 2).map { it.size })
        assertTrue(splitIntoTeams(emptyList<String>(), 2).isEmpty())
    }

    @Test
    fun workingDays() {
        // monday 5 october 2026 to monday 12 october 2026
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(5L, workingDaysBetween(monday, monday.plusWeeks(1)))
        assertEquals(5L, workingDaysBetween(monday.plusWeeks(1), monday))
        assertEquals(4L, workingDaysBetween(monday, monday.plusDays(4)))
        assertEquals(0L, workingDaysBetween(monday.plusDays(5), monday.plusDays(7)))
        assertEquals(10L, workingDaysBetween(monday.plusDays(3), monday.plusDays(17)))
        assertEquals(0L, workingDaysBetween(monday, monday))
    }
}
