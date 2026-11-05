package com.forsakenblank.atlas.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class StatRange(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All time") }

enum class ChartGroup(val label: String) { DAY("By day"), MONTH("By month"), YEAR("By year") }

// which stretch a range shows, offset 0 is the one with today in it and each step goes one further back
fun statPeriod(range: StatRange, offset: Int, today: LocalDate, firstLog: LocalDate): ClosedRange<LocalDate> = when (range) {
    StatRange.WEEK -> today.minusWeeks(offset.toLong()).let { it.minusDays(6)..it }
    StatRange.MONTH -> YearMonth.from(today).minusMonths(offset.toLong()).let { it.atDay(1)..minOf(it.atEndOfMonth(), today) }
    StatRange.YEAR -> today.minusYears(offset.toLong()).year.let { LocalDate.of(it, 1, 1)..minOf(LocalDate.of(it, 12, 31), today) }
    StatRange.ALL -> minOf(firstLog, today)..today
}

// the groupings that make sense for a stretch this long
fun chartGroups(period: ClosedRange<LocalDate>): List<ChartGroup> {
    val days = ChronoUnit.DAYS.between(period.start, period.endInclusive) + 1
    return buildList {
        if (days <= 400) add(ChartGroup.DAY)
        if (days > 31) add(ChartGroup.MONTH)
        if (days > 366) add(ChartGroup.YEAR)
    }
}

// the first choice keeps the bars wide enough to read and tap
fun defaultChartGroup(period: ClosedRange<LocalDate>): ChartGroup {
    val days = ChronoUnit.DAYS.between(period.start, period.endInclusive) + 1
    return when {
        days <= 45 -> ChartGroup.DAY
        days <= 1100 -> ChartGroup.MONTH
        else -> ChartGroup.YEAR
    }
}

// each bar's first and last day
fun chartBuckets(period: ClosedRange<LocalDate>, group: ChartGroup): List<ClosedRange<LocalDate>> {
    val out = mutableListOf<ClosedRange<LocalDate>>()
    var at = period.start
    while (at <= period.endInclusive) {
        val last = when (group) {
            ChartGroup.DAY -> at
            ChartGroup.MONTH -> YearMonth.from(at).atEndOfMonth()
            ChartGroup.YEAR -> LocalDate.of(at.year, 12, 31)
        }
        val end = minOf(last, period.endInclusive)
        out += at..end
        at = end.plusDays(1)
    }
    return out
}

data class PeriodAverages(val perDay: Double, val perMonth: Double, val perYear: Double)

// a total shared out over the time it covers, days with nothing logged count too
fun periodAverages(total: Double, from: LocalDate, to: LocalDate): PeriodAverages {
    val days = ChronoUnit.DAYS.between(from, to) + 1
    val months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1
    val years = to.year - from.year + 1
    return PeriodAverages(total / days, total / months, total / years)
}
