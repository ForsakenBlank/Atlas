package com.forsakenblank.atlas.util

import kotlin.math.max
import kotlin.math.min

// uk tax for the 2026/27 year, all the numbers sit at the top so next year is a quick edit
enum class TaxRegion(val label: String) { ENGLAND("England, Wales, NI"), SCOTLAND("Scotland") }

enum class WorkType(val label: String) { EMPLOYED("Employed"), SELF_EMPLOYED("Self employed") }

enum class LoanPlan(val label: String, val threshold: Double) {
    NONE("No loan", 0.0),
    PLAN_1("Plan 1", 26_900.0),
    PLAN_2("Plan 2", 29_385.0),
    PLAN_4("Plan 4", 33_795.0),
    PLAN_5("Plan 5", 25_000.0),
}

object TaxYear {
    const val LABEL = "2026/27"
    const val PERSONAL_ALLOWANCE = 12_570.0
    const val TAPER_START = 100_000.0
    const val NI_THRESHOLD = 12_570.0
    const val NI_UPPER = 50_270.0
    const val POSTGRAD_THRESHOLD = 21_000.0

    // band width on taxable income, the last one has no end
    class Band(val name: String, val width: Double, val rate: Double)

    val england = listOf(
        Band("Basic", 37_700.0, 0.20),
        Band("Higher", 125_140.0 - 37_700.0, 0.40),
        Band("Additional", Double.POSITIVE_INFINITY, 0.45),
    )

    val scotland = listOf(
        Band("Starter", 16_537.0 - 12_570.0, 0.19),
        Band("Basic", 29_526.0 - 16_537.0, 0.20),
        Band("Intermediate", 43_662.0 - 29_526.0, 0.21),
        Band("Higher", 75_000.0 - 43_662.0, 0.42),
        Band("Advanced", 125_140.0 - 75_000.0, 0.45),
        Band("Top", Double.POSITIVE_INFINITY, 0.48),
    )
}

class TaxLine(val label: String, val amount: Double, val rate: Double, val tax: Double)

class TaxResult(
    val gross: Double,
    val personalAllowance: Double,
    val taxable: Double,
    val lines: List<TaxLine>,
    val incomeTax: Double,
    val nationalInsurance: Double,
    val studentLoan: Double,
    val postgradLoan: Double,
) {
    val totalDeductions get() = incomeTax + nationalInsurance + studentLoan + postgradLoan
    val takeHome get() = gross - totalDeductions
    val effectiveRate get() = if (gross > 0) totalDeductions / gross else 0.0
}

// the allowance drops by 1 for every 2 over 100k, so it is gone at 125,140
fun personalAllowance(income: Double): Double =
    max(0.0, TaxYear.PERSONAL_ALLOWANCE - max(0.0, income - TaxYear.TAPER_START) / 2)

fun calculateTax(
    gross: Double,
    region: TaxRegion = TaxRegion.ENGLAND,
    work: WorkType = WorkType.EMPLOYED,
    plan: LoanPlan = LoanPlan.NONE,
    postgrad: Boolean = false,
): TaxResult {
    val income = max(0.0, gross)
    val allowance = personalAllowance(income)
    val taxable = max(0.0, income - allowance)

    var left = taxable
    val lines = ArrayList<TaxLine>()
    for (band in if (region == TaxRegion.SCOTLAND) TaxYear.scotland else TaxYear.england) {
        if (left <= 0) break
        val slice = min(left, band.width)
        lines += TaxLine(band.name, slice, band.rate, slice * band.rate)
        left -= slice
    }
    val incomeTax = lines.sumOf { it.tax }

    // employees pay 8% then 2%, the self employed pay class 4 at 6% then 2%
    val lowRate = if (work == WorkType.EMPLOYED) 0.08 else 0.06
    val middle = max(0.0, min(income, TaxYear.NI_UPPER) - TaxYear.NI_THRESHOLD)
    val above = max(0.0, income - TaxYear.NI_UPPER)
    val ni = middle * lowRate + above * 0.02

    val loan = if (plan == LoanPlan.NONE) 0.0 else max(0.0, income - plan.threshold) * 0.09
    val pg = if (postgrad) max(0.0, income - TaxYear.POSTGRAD_THRESHOLD) * 0.06 else 0.0

    return TaxResult(income, allowance, taxable, lines, incomeTax, ni, loan, pg)
}
