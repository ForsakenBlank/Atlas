package com.forsakenblank.atlas.util

import com.forsakenblank.atlas.data.MoneyEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

const val DAY_MS = 86_400_000L

fun pounds(pence: Long): String = formatMoney(pence / 100.0, "£")

// +£5.00 or -£5.00, used where the direction matters
fun signedPounds(pence: Long): String = (if (pence > 0) "+" else "") + pounds(pence)

// "12.50", "£1,200" or "-40" into pence, null when it is not a number
fun parsePence(text: String, allowNegative: Boolean = false): Long? {
    val clean = text.trim().removePrefix("£").replace(",", "").trim()
    if (clean.isEmpty()) return null
    val value = clean.toBigDecimalOrNull() ?: return null
    if (!allowNegative && value.signum() < 0) return null
    if (value.abs() > BigDecimal("1000000000")) return null
    return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
}

// plain text for an edit box, 1250 becomes 12.50
fun penceInput(pence: Long): String = BigDecimal.valueOf(pence, 2).toPlainString()

// what an account holds now, records marked as past leave it alone
fun currentBalance(openingPence: Long, entries: List<MoneyEntry>): Long =
    openingPence + entries.filter { !it.historical }.sumOf { it.amountPence }

// transfers move money around but do not count as earning or spending
private fun List<MoneyEntry>.real() = filter { it.transferId == null }

class MonthTotal(val month: YearMonth, val earned: Long, val spent: Long)

// the last few months ending this month, spent is a positive number
fun monthlyTotals(entries: List<MoneyEntry>, months: Int, today: LocalDate = LocalDate.now()): List<MonthTotal> {
    val current = YearMonth.from(today)
    val grouped = entries.real().groupBy { YearMonth.from(LocalDate.ofEpochDay(it.day)) }
    return (months - 1 downTo 0).map { back ->
        val month = current.minusMonths(back.toLong())
        val list = grouped[month].orEmpty()
        MonthTotal(
            month,
            earned = list.filter { it.amountPence > 0 }.sumOf { it.amountPence },
            spent = -list.filter { it.amountPence < 0 }.sumOf { it.amountPence },
        )
    }
}

// where the spending (or the earnings) came from since a day, biggest first
fun categoryTotals(entries: List<MoneyEntry>, fromDay: Long, earning: Boolean = false): List<Pair<String, Long>> =
    entries.real()
        .filter { (if (earning) it.amountPence > 0 else it.amountPence < 0) && it.day >= fromDay }
        .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Other" }
        .map { (name, list) -> name to abs(list.sumOf { it.amountPence }) }
        .sortedByDescending { it.second }

// the total across every account at the end of each day, worked backwards from what it is today
// so entries dated in the past make the line fit what happened without moving today's number
fun balanceSeries(currentTotal: Long, entries: List<MoneyEntry>, fromDay: Long, toDay: Long): List<Pair<Long, Long>> {
    if (toDay < fromDay) return emptyList()
    val byDay = entries.groupBy { it.day }
    // anything dated after the last day shown has not happened yet as far as the chart goes
    var balance = currentTotal - entries.filter { it.day > toDay }.sumOf { it.amountPence }
    val out = ArrayList<Pair<Long, Long>>()
    for (day in toDay downTo fromDay) {
        out += day to balance
        balance -= byDay[day].orEmpty().sumOf { it.amountPence }
    }
    return out.reversed()
}
