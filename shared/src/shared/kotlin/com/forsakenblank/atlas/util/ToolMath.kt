package com.forsakenblank.atlas.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.expm1
import kotlin.math.floor
import kotlin.math.ln1p
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

// what people type in number boxes, a comma works as a decimal point too
fun parseNumberInput(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

fun parseWholeInput(text: String): Long? {
    val value = parseNumberInput(text) ?: return null
    if (value != floor(value) || abs(value) > 1e15) return null
    return value.toLong()
}

// rounds to a few decimals and drops trailing zeros, so 2.50 shows as 2.5 and 3.0 as 3.
// tiny numbers keep that many significant digits instead of turning into 0
fun niceNumber(value: Double, decimals: Int = 4): String {
    if (!value.isFinite()) return "-"
    if (abs(value) >= 1e15) return BigDecimal(value).round(MathContext(6)).stripTrailingZeros().toString()
    val exact = BigDecimal.valueOf(value)
    val rounded = if (abs(value) < 1 && decimals > 0) {
        exact.round(MathContext(decimals, RoundingMode.HALF_UP))
    } else {
        exact.setScale(decimals, RoundingMode.HALF_UP)
    }
    if (rounded.signum() == 0) return "0"
    return rounded.stripTrailingZeros().toPlainString()
}

fun formatMoney(value: Double, symbol: String): String {
    if (!value.isFinite()) return "-"
    val plain = BigDecimal.valueOf(abs(value)).setScale(2, RoundingMode.HALF_UP).toPlainString()
    val whole = plain.substringBefore('.').reversed().chunked(3).joinToString(",").reversed()
    val pence = plain.substringAfter('.')
    val sign = if (value < 0 && plain != "0.00") "-" else ""
    return "$sign$symbol$whole.$pence"
}

// a chance from 0 to 1 as a percentage, a near miss never rounds up to 100%
fun formatChance(chance: Double): String {
    if (!chance.isFinite()) return "-"
    val percent = chance * 100
    return when {
        percent <= 0.0 -> "0%"
        percent >= 100.0 -> "100%"
        percent > 99.99 -> "over 99.99%"
        percent < 0.000001 -> "under 0.000001%"
        else -> niceNumber(percent, if (percent < 1) 3 else 2) + "%"
    }
}

// hundredths of a second, like 02:03.45 or 1:02:03.45
fun formatStopwatch(millis: Long): String {
    val total = millis.coerceAtLeast(0)
    val hundredths = total / 10 % 100
    val seconds = total / 1000 % 60
    val minutes = total / 60_000 % 60
    val hours = total / 3_600_000
    return if (hours > 0) {
        "%d:%02d:%02d.%02d".format(hours, minutes, seconds, hundredths)
    } else {
        "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}

// every format goes through decimal odds, which is what a stake of 1 pays back including the stake
object Odds {

    data class Fraction(val numerator: Long, val denominator: Long) {
        val value: Double get() = numerator.toDouble() / denominator
        override fun toString() = "$numerator/$denominator"
    }

    fun fromFractional(numerator: Double, denominator: Double): Double? =
        if (numerator < 0 || denominator <= 0) null else numerator / denominator + 1

    // takes "5/2", "evens" or a lone number like "4", which means 4/1
    fun parseFractional(text: String): Double? {
        val clean = text.trim().lowercase()
        if (clean == "evens" || clean == "evs" || clean == "even") return 2.0
        val parts = clean.split('/')
        return when (parts.size) {
            1 -> parseNumberInput(parts[0])?.let { fromFractional(it, 1.0) }
            2 -> {
                val top = parseNumberInput(parts[0]) ?: return null
                val bottom = parseNumberInput(parts[1]) ?: return null
                fromFractional(top, bottom)
            }
            else -> null
        }
    }

    // american odds below 100 either way do not exist, +100 and -100 are both evens
    fun fromAmerican(american: Double): Double? = when {
        american >= 100 -> 1 + american / 100
        american <= -100 -> 1 + 100 / -american
        else -> null
    }

    fun fromPercent(percent: Double): Double? = if (percent <= 0 || percent > 100) null else 100 / percent

    fun toFractional(decimal: Double): Fraction? = if (decimal < 1) null else simpleFraction(decimal - 1)

    fun toAmerican(decimal: Double): Double? = when {
        decimal <= 1 -> null
        decimal >= 2 -> (decimal - 1) * 100
        else -> -100 / (decimal - 1)
    }

    fun toPercent(decimal: Double): Double? = if (decimal < 1) null else 100 / decimal

    fun formatAmerican(american: Double): String =
        if (american > 0) "+" + niceNumber(american, 2) else niceNumber(american, 2)

    // the simplest fraction close enough to the value, so 2.5 gives 5/2 and 0.909 gives 10/11.
    // walks the continued fraction until a step lands within the tolerance
    fun simpleFraction(value: Double, tolerance: Double = 0.005, maxDenominator: Long = 1000): Fraction? {
        if (!value.isFinite() || value < 0) return null
        if (value > 1_000_000) return Fraction(value.roundToLong(), 1)
        var previousTop = 0L
        var top = 1L
        var previousBottom = 1L
        var bottom = 0L
        var rest = value
        var best = Fraction(value.roundToLong(), 1)
        var steps = 0
        while (steps < 32) {
            steps++
            // the nudge stops 99.9999999 from counting as 99
            val whole = floor(rest + 1e-9).toLong()
            val nextTop = whole * top + previousTop
            val nextBottom = whole * bottom + previousBottom
            if (nextBottom > maxDenominator) break
            best = Fraction(nextTop, nextBottom)
            if (nextTop > 0 && abs(best.value - value) < tolerance) break
            val leftover = rest - whole
            if (leftover < 1e-9) break
            rest = 1 / leftover
            previousTop = top
            top = nextTop
            previousBottom = bottom
            bottom = nextBottom
        }
        return best
    }
}

// chances here run from 0 to 1, not 0 to 100
fun chanceFromPercent(percent: Double): Double? = if (percent < 0 || percent > 100) null else percent / 100

fun chanceFromOneIn(n: Double): Double? = if (n < 1) null else 1 / n

// worked out through logs so very small chances do not vanish into rounding
fun chanceOfAtLeastOne(chance: Double, tries: Int): Double =
    if (tries <= 0) 0.0 else -expm1(tries * ln1p(-chance))

fun chanceOfAll(chance: Double, tries: Int): Double = if (tries <= 0) 1.0 else chance.pow(tries)

fun expectedSuccesses(chance: Double, tries: Int): Double = chance * tries

// fewest tries for at least one success to reach the target, null when it never will
fun triesForChance(chance: Double, target: Double): Long? = when {
    target <= 0 -> 0L
    chance >= 1 -> 1L
    chance <= 0 || target >= 1 -> null
    else -> {
        val tries = ceil(ln1p(-target) / ln1p(-chance) - 1e-9)
        if (tries.isFinite()) tries.toLong().coerceAtLeast(1) else null
    }
}

fun percentOf(percent: Double, value: Double): Double = value * percent / 100

fun whatPercent(part: Double, whole: Double): Double? = if (whole == 0.0) null else part / whole * 100

fun percentChange(from: Double, to: Double): Double? = if (from == 0.0) null else (to - from) / abs(from) * 100

fun addPercent(value: Double, percent: Double): Double = value * (1 + percent / 100)

fun subtractPercent(value: Double, percent: Double): Double = value * (1 - percent / 100)

data class BillSplit(val tip: Double, val total: Double, val share: Double)

fun splitBill(bill: Double, tipPercent: Double, people: Int, roundUp: Boolean = false): BillSplit? {
    if (bill < 0 || tipPercent < 0 || people < 1) return null
    val tip = bill * tipPercent / 100
    val exactShare = (bill + tip) / people
    if (!roundUp) return BillSplit(tip, bill + tip, exactShare)
    // everyone rounds up to a whole amount and the extra goes on the tip
    val share = ceil(exactShare - 1e-9).coerceAtLeast(0.0)
    val total = share * people
    return BillSplit(total - bill, total, share)
}

// each unit has a factor to its category's base unit, temperature has its own maths below
data class MeasureUnit(val name: String, val symbol: String, val factor: Double = 1.0)

enum class UnitCategory(
    val label: String,
    val units: List<MeasureUnit>,
    private val metricDefault: String,
    private val imperialDefault: String,
) {
    LENGTH(
        "Length",
        listOf(
            MeasureUnit("Millimetre", "mm", 0.001),
            MeasureUnit("Centimetre", "cm", 0.01),
            MeasureUnit("Metre", "m", 1.0),
            MeasureUnit("Kilometre", "km", 1000.0),
            MeasureUnit("Inch", "in", 0.0254),
            MeasureUnit("Foot", "ft", 0.3048),
            MeasureUnit("Yard", "yd", 0.9144),
            MeasureUnit("Mile", "mi", 1609.344),
            MeasureUnit("Nautical mile", "nmi", 1852.0),
        ),
        metricDefault = "km",
        imperialDefault = "mi",
    ),
    WEIGHT(
        "Weight",
        listOf(
            MeasureUnit("Milligram", "mg", 0.000001),
            MeasureUnit("Gram", "g", 0.001),
            MeasureUnit("Kilogram", "kg", 1.0),
            MeasureUnit("Tonne", "t", 1000.0),
            MeasureUnit("Ounce", "oz", 0.028349523125),
            MeasureUnit("Pound", "lb", 0.45359237),
            MeasureUnit("Stone", "st", 6.35029318),
        ),
        metricDefault = "kg",
        imperialDefault = "lb",
    ),
    TEMPERATURE(
        "Temperature",
        listOf(
            MeasureUnit("Celsius", "°C"),
            MeasureUnit("Fahrenheit", "°F"),
            MeasureUnit("Kelvin", "K"),
        ),
        metricDefault = "°C",
        imperialDefault = "°F",
    ),
    VOLUME(
        "Volume",
        listOf(
            MeasureUnit("Millilitre", "ml", 0.001),
            MeasureUnit("Litre", "l", 1.0),
            MeasureUnit("Cubic metre", "m³", 1000.0),
            MeasureUnit("Teaspoon", "tsp", 0.00492892159375),
            MeasureUnit("Tablespoon", "tbsp", 0.01478676478125),
            MeasureUnit("Fluid ounce (UK)", "fl oz (UK)", 0.0284130625),
            MeasureUnit("Fluid ounce (US)", "fl oz (US)", 0.0295735295625),
            MeasureUnit("Cup (US)", "cup", 0.2365882365),
            MeasureUnit("Pint (UK)", "pt (UK)", 0.56826125),
            MeasureUnit("Pint (US)", "pt (US)", 0.473176473),
            MeasureUnit("Gallon (UK)", "gal (UK)", 4.54609),
            MeasureUnit("Gallon (US)", "gal (US)", 3.785411784),
        ),
        metricDefault = "l",
        imperialDefault = "pt (UK)",
    ),
    SPEED(
        "Speed",
        listOf(
            MeasureUnit("Metres per second", "m/s", 1.0),
            MeasureUnit("Kilometres per hour", "km/h", 1 / 3.6),
            MeasureUnit("Miles per hour", "mph", 0.44704),
            MeasureUnit("Feet per second", "ft/s", 0.3048),
            MeasureUnit("Knot", "kn", 1852.0 / 3600),
        ),
        metricDefault = "km/h",
        imperialDefault = "mph",
    ),
    AREA(
        "Area",
        listOf(
            MeasureUnit("Square centimetre", "cm²", 0.0001),
            MeasureUnit("Square metre", "m²", 1.0),
            MeasureUnit("Hectare", "ha", 10_000.0),
            MeasureUnit("Square kilometre", "km²", 1_000_000.0),
            MeasureUnit("Square inch", "in²", 0.00064516),
            MeasureUnit("Square foot", "ft²", 0.09290304),
            MeasureUnit("Square yard", "yd²", 0.83612736),
            MeasureUnit("Acre", "ac", 4046.8564224),
            MeasureUnit("Square mile", "mi²", 2_589_988.110336),
        ),
        metricDefault = "m²",
        imperialDefault = "ft²",
    ),
    DATA(
        "Data",
        listOf(
            MeasureUnit("Bit", "bit", 0.125),
            MeasureUnit("Byte", "B", 1.0),
            MeasureUnit("Kilobyte", "kB", 1e3),
            MeasureUnit("Megabyte", "MB", 1e6),
            MeasureUnit("Gigabyte", "GB", 1e9),
            MeasureUnit("Terabyte", "TB", 1e12),
            MeasureUnit("Kibibyte", "KiB", 1024.0),
            MeasureUnit("Mebibyte", "MiB", 1024.0 * 1024),
            MeasureUnit("Gibibyte", "GiB", 1024.0 * 1024 * 1024),
            MeasureUnit("Tebibyte", "TiB", 1024.0 * 1024 * 1024 * 1024),
        ),
        metricDefault = "MB",
        imperialDefault = "GB",
    );

    fun unit(symbol: String): MeasureUnit = units.firstOrNull { it.symbol == symbol } ?: units.first()

    // metric to imperial for metric people, the other way round for everyone else
    fun defaultPair(metric: Boolean): Pair<MeasureUnit, MeasureUnit> {
        val metricUnit = unit(metricDefault)
        val imperialUnit = unit(imperialDefault)
        return if (metric) metricUnit to imperialUnit else imperialUnit to metricUnit
    }
}

fun convertUnit(category: UnitCategory, value: Double, from: MeasureUnit, to: MeasureUnit): Double =
    if (category == UnitCategory.TEMPERATURE) {
        convertTemperature(value, from.symbol, to.symbol)
    } else {
        value * from.factor / to.factor
    }

// symbols are °C, °F and K
fun convertTemperature(value: Double, from: String, to: String): Double {
    val celsius = when (from) {
        "°F" -> (value - 32) * 5 / 9
        "K" -> value - 273.15
        else -> value
    }
    return when (to) {
        "°F" -> celsius * 9 / 5 + 32
        "K" -> celsius + 273.15
        else -> celsius
    }
}

const val MAX_DICE = 100
const val MAX_DICE_SIDES = 1000
const val MAX_DICE_MODIFIER = 1000

data class DiceSpec(val count: Int, val sides: Int, val modifier: Int = 0) {
    override fun toString(): String = when {
        modifier > 0 -> "${count}d$sides+$modifier"
        modifier < 0 -> "${count}d$sides$modifier"
        else -> "${count}d$sides"
    }
}

data class DiceRoll(val spec: DiceSpec, val faces: List<Int>) {
    val total: Int get() = faces.sum() + spec.modifier
}

private val diceNotation = Regex("""(\d*)\s*[dD]\s*(\d+)\s*(?:([+-])\s*(\d+))?""")

// reads "3d6", "d20", "2d8+3" or "4d6-1", anything else gives null
fun parseDice(text: String): DiceSpec? {
    val match = diceNotation.matchEntire(text.trim()) ?: return null
    val (countText, sidesText, sign, modifierText) = match.destructured
    val count = if (countText.isEmpty()) 1 else countText.toIntOrNull() ?: return null
    val sides = sidesText.toIntOrNull() ?: return null
    val modifier = if (modifierText.isEmpty()) 0 else modifierText.toIntOrNull() ?: return null
    if (count !in 1..MAX_DICE || sides !in 2..MAX_DICE_SIDES || modifier > MAX_DICE_MODIFIER) return null
    return DiceSpec(count, sides, if (sign == "-") -modifier else modifier)
}

fun rollDice(spec: DiceSpec, random: Random = Random.Default): DiceRoll =
    DiceRoll(spec, List(spec.count) { random.nextInt(1, spec.sides + 1) })

const val MAX_PICKED_NUMBERS = 1000
private const val MAX_PICK_RANGE = 1_000_000_000_000L

// whole numbers from min to max inclusive, null when the range is wrong or too small to avoid repeats
fun pickNumbers(min: Long, max: Long, count: Int, noRepeats: Boolean, random: Random = Random.Default): List<Long>? {
    if (min > max || count !in 1..MAX_PICKED_NUMBERS) return null
    if (abs(min) > MAX_PICK_RANGE || abs(max) > MAX_PICK_RANGE) return null
    if (!noRepeats) return List(count) { random.nextLong(min, max + 1) }
    val size = max - min + 1
    if (count > size) return null
    if (size <= 10_000) return (min..max).shuffled(random).take(count)
    val picked = LinkedHashSet<Long>()
    while (picked.size < count) picked += random.nextLong(min, max + 1)
    return picked.toList()
}

// deals people out like cards so team sizes never differ by more than one
fun <T> splitIntoTeams(people: List<T>, teamSize: Int): List<List<T>> {
    if (people.isEmpty() || teamSize < 1) return emptyList()
    val teams = (people.size + teamSize - 1) / teamSize
    return List(teams) { team -> people.filterIndexed { index, _ -> index % teams == team } }
}

// monday to friday from the earlier date up to, but not including, the later one
fun workingDaysBetween(from: LocalDate, to: LocalDate): Long {
    val start = if (from.isAfter(to)) to else from
    val end = if (from.isAfter(to)) from else to
    val days = ChronoUnit.DAYS.between(start, end)
    var count = days / 7 * 5
    var day = start.plusDays(days / 7 * 7)
    while (day.isBefore(end)) {
        if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) count++
        day = day.plusDays(1)
    }
    return count
}
