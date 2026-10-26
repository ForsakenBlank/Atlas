package com.forsakenblank.atlas

import com.forsakenblank.atlas.util.LoanPlan
import com.forsakenblank.atlas.util.TaxRegion
import com.forsakenblank.atlas.util.WorkType
import com.forsakenblank.atlas.util.calculateTax
import com.forsakenblank.atlas.util.personalAllowance
import org.junit.Assert.assertEquals
import org.junit.Test

class UkTaxTest {

    @Test
    fun nothingIsPaidUnderTheAllowance() {
        val r = calculateTax(12_000.0)
        assertEquals(0.0, r.incomeTax, 0.001)
        assertEquals(0.0, r.nationalInsurance, 0.001)
    }

    @Test
    fun thirtyThousandInEngland() {
        val r = calculateTax(30_000.0)
        // 17,430 taxable at 20 percent
        assertEquals(3_486.0, r.incomeTax, 0.001)
        // 17,430 at 8 percent
        assertEquals(1_394.4, r.nationalInsurance, 0.001)
        assertEquals(25_119.6, r.takeHome, 0.001)
    }

    @Test
    fun higherRateStartsAfterTheBasicBand() {
        val r = calculateTax(60_000.0)
        // 37,700 at 20 percent plus 9,730 at 40 percent
        assertEquals(7_540.0 + 3_892.0, r.incomeTax, 0.001)
        // 37,700 at 8 percent plus 9,730 at 2 percent
        assertEquals(3_016.0 + 194.6, r.nationalInsurance, 0.001)
    }

    @Test
    fun allowanceTapersAboveOneHundredThousand() {
        assertEquals(12_570.0, personalAllowance(100_000.0), 0.001)
        assertEquals(7_570.0, personalAllowance(110_000.0), 0.001)
        assertEquals(0.0, personalAllowance(125_140.0), 0.001)
        assertEquals(0.0, personalAllowance(200_000.0), 0.001)
    }

    @Test
    fun scottishBandsApply() {
        val r = calculateTax(50_000.0, region = TaxRegion.SCOTLAND)
        // 3,967 at 19, 12,989 at 20, 14,136 at 21, 6,338 at 42
        val expected = 3_967 * 0.19 + 12_989 * 0.20 + 14_136 * 0.21 + 6_338 * 0.42
        assertEquals(expected, r.incomeTax, 0.001)
        assertEquals(4, r.lines.size)
    }

    @Test
    fun selfEmployedPaySixPercentClassFour() {
        val r = calculateTax(30_000.0, work = WorkType.SELF_EMPLOYED)
        assertEquals(17_430 * 0.06, r.nationalInsurance, 0.001)
    }

    @Test
    fun studentLoanTakesNinePercentOverTheThreshold() {
        val r = calculateTax(35_000.0, plan = LoanPlan.PLAN_2, postgrad = true)
        assertEquals((35_000 - 29_385) * 0.09, r.studentLoan, 0.001)
        assertEquals((35_000 - 21_000) * 0.06, r.postgradLoan, 0.001)
    }
}
